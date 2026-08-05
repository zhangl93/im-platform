package com.im.platform.msg.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 记录"某用户已经通过实时推送确认收到某条消息",用于离线补偿拉取时跳过已经确认过的消息
 * (见 SyncGrpcService.pullUpdates)——同一条消息不会先实时推送成功又在重连后重复下发一遍。
 *
 * message_id 是 idgen 生成的全局唯一 ID,天然就是去重键,不需要额外发一个去重 ID。
 * 用 Redis Set 存,TTL 只需要覆盖"客户端可能离线多久"这个量级(取跟离线补偿场景匹配的 7 天),
 * 不需要永久保留——过期之后即使误判成"没 ack 过",离线补偿最多是多推一次,不是丢消息。
 */
@Service
public class AckService {

    private static final String KEY_PREFIX = "im:ack:";
    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate stringRedisTemplate;

    public AckService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void ack(long userId, long messageId) {
        String key = KEY_PREFIX + userId;
        stringRedisTemplate.opsForSet().add(key, String.valueOf(messageId));
        stringRedisTemplate.expire(key, TTL);
    }

    public boolean isAcked(long userId, long messageId) {
        return Boolean.TRUE.equals(
                stringRedisTemplate.opsForSet().isMember(KEY_PREFIX + userId, String.valueOf(messageId)));
    }
}
