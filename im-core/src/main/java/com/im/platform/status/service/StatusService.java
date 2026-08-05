package com.im.platform.status.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 在线状态弱一致实现:心跳续期 Redis key TTL,过期即视为离线,不做跨实例强同步。
 * 允许短暂滞后(例如客户端异常掉线到 TTL 过期之间的窗口),这是在线状态场景可接受的取舍。
 */
@Service
public class StatusService {

    private static final String ONLINE_KEY_PREFIX = "im:status:online:";

    private final StringRedisTemplate redisTemplate;

    public StatusService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public long setOnline(long userId, String deviceId, int heartbeatIntervalSec) {
        long now = System.currentTimeMillis();
        Duration ttl = Duration.ofSeconds((long) heartbeatIntervalSec * 3); // 容忍2次心跳丢失
        redisTemplate.opsForValue().set(ONLINE_KEY_PREFIX + userId, deviceId + ":" + now,
                ttl.toMillis(), TimeUnit.MILLISECONDS);
        return now;
    }

    public void setOffline(long userId, String deviceId) {
        redisTemplate.delete(ONLINE_KEY_PREFIX + userId);
    }

    public boolean isOnline(long userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(ONLINE_KEY_PREFIX + userId));
    }

    public Map<Long, Boolean> batchIsOnline(List<Long> userIds) {
        return userIds.stream().collect(Collectors.toMap(id -> id, this::isOnline));
    }
}
