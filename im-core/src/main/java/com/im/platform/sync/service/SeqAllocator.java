package com.im.platform.sync.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 按用户维度分配单调递增 seq。用 Redis INCR 而不是数据库自增列,
 * 避免高并发写入更新日志表时产生行锁竞争。
 */
@Component
public class SeqAllocator {

    private static final String SEQ_KEY_PREFIX = "im:sync:seq:";

    private final StringRedisTemplate redisTemplate;

    public SeqAllocator(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public long next(long userId) {
        return redisTemplate.opsForValue().increment(SEQ_KEY_PREFIX + userId);
    }
}
