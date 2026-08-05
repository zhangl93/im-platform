package com.im.platform.gateway.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * auth_key_id -&gt; 这条连接协商出来的 AES 密钥。这是 gateway"无状态,连接层例外"的具体体现:
 * 密钥只在建立连接的这个 gateway 实例的内存里,进程重启/连接断开就没了,客户端需要重新握手。
 * 不放 Redis 之类的共享存储——对称密钥没有跨实例共享的必要,共享了反而扩大泄露面。
 *
 * 三层清理机制,任何一层没触发另外两层兜底:
 * 1. 连接正常关闭时,GatewayChannelHandler.channelInactive 主动调用 remove()(最及时)
 * 2. get() 时被动检查是否过期,过期了就地删除并返回 null(逼客户端重新握手)
 * 3. 定时任务周期性主动清扫(兜底连接非正常断开、TCP FIN 没送达等 channelInactive 没触发的情况)
 */
@Component
public class ConnectionKeyStore {

    private static final Logger log = LoggerFactory.getLogger(ConnectionKeyStore.class);

    private record Entry(SecretKeySpec key, long expiresAtMillis) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAtMillis;
        }
    }

    private final Map<Long, Entry> entries = new ConcurrentHashMap<>();
    private final long ttlMillis;

    public ConnectionKeyStore(@Value("${gateway.connection-key-ttl-seconds:3600}") long ttlSeconds) {
        this.ttlMillis = Duration.ofSeconds(ttlSeconds).toMillis();
    }

    public void put(long authKeyId, SecretKeySpec key) {
        entries.put(authKeyId, new Entry(key, System.currentTimeMillis() + ttlMillis));
    }

    public SecretKeySpec get(long authKeyId) {
        Entry entry = entries.get(authKeyId);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            entries.remove(authKeyId);
            return null;
        }
        return entry.key();
    }

    public void remove(long authKeyId) {
        entries.remove(authKeyId);
    }

    public int size() {
        return entries.size();
    }

    /** 每分钟扫一遍,删掉已过期但因为一直没被 get() 过而没被动清理掉的条目。 */
    @Scheduled(fixedDelay = 60_000)
    void sweepExpired() {
        int before = entries.size();
        entries.values().removeIf(Entry::isExpired);
        // removeIf 在 values() 视图上对 ConcurrentHashMap 是安全的,等价于按 entry 删除
        int removed = before - entries.size();
        if (removed > 0) {
            log.debug("swept {} expired connection keys, {} remaining", removed, entries.size());
        }
    }
}
