package com.im.platform.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 按已登录用户限制每秒发消息数,防止单个账号刷消息打满下游(DB 写入、推送扇出)。 */
@Component
public class MessageRateLimiter {

    private final FixedWindowRateLimiter limiter;

    public MessageRateLimiter(@Value("${gateway.security.message-rate-limit-per-user:20}") int limitPerSecond) {
        this.limiter = new FixedWindowRateLimiter(limitPerSecond, Duration.ofSeconds(1));
    }

    public boolean tryAcquire(long userId) {
        return limiter.tryAcquire(String.valueOf(userId));
    }
}
