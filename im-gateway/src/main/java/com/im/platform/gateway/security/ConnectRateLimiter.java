package com.im.platform.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 按源 IP 限制每秒新建连接数,防止单个来源的连接风暴打满 accept 队列/句柄资源。 */
@Component
public class ConnectRateLimiter {

    private final FixedWindowRateLimiter limiter;

    public ConnectRateLimiter(@Value("${gateway.security.connect-rate-limit-per-ip:1000}") int limitPerSecond) {
        this.limiter = new FixedWindowRateLimiter(limitPerSecond, Duration.ofSeconds(1));
    }

    public boolean tryAcquire(String ip) {
        return limiter.tryAcquire(ip);
    }
}
