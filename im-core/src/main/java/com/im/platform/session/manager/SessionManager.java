package com.im.platform.session.manager;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 会话状态的唯一读写入口。选 Redis 而不是本地内存,是因为 session 服务本身要支持多实例部署,
 * 校验请求(ValidateSession)可能落到任意实例上。
 */
@Component
public class SessionManager {

    private static final String KEY_PREFIX = "im:session:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    private final RedisTemplate<String, SessionRecord> redisTemplate;

    public SessionManager(RedisTemplate<String, SessionRecord> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public SessionRecord create(long userId, String deviceId, long authKeyId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        long expireAt = System.currentTimeMillis() + DEFAULT_TTL.toMillis();
        SessionRecord record = new SessionRecord(token, userId, deviceId, authKeyId, expireAt);
        redisTemplate.opsForValue().set(KEY_PREFIX + token, record, DEFAULT_TTL.toMillis(), TimeUnit.MILLISECONDS);
        return record;
    }

    public SessionRecord get(String sessionToken) {
        return redisTemplate.opsForValue().get(KEY_PREFIX + sessionToken);
    }

    public void remove(String sessionToken) {
        redisTemplate.delete(KEY_PREFIX + sessionToken);
    }
}
