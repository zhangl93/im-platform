package com.im.platform.gateway.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class FixedWindowRateLimiterTest {

    @Test
    void tryAcquire_underLimit_allAllowed() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(5, Duration.ofSeconds(10));

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("key-a")).isTrue();
        }
    }

    @Test
    void tryAcquire_exceedsLimit_rejectedWithinSameWindow() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(3, Duration.ofSeconds(10));

        assertThat(limiter.tryAcquire("key-a")).isTrue();
        assertThat(limiter.tryAcquire("key-a")).isTrue();
        assertThat(limiter.tryAcquire("key-a")).isTrue();
        assertThat(limiter.tryAcquire("key-a")).isFalse();
        assertThat(limiter.tryAcquire("key-a")).isFalse();
    }

    @Test
    void tryAcquire_differentKeys_independentBudgets() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(1, Duration.ofSeconds(10));

        assertThat(limiter.tryAcquire("key-a")).isTrue();
        assertThat(limiter.tryAcquire("key-a")).isFalse();
        // 另一个 key 的预算是独立的,不受 key-a 用超的影响
        assertThat(limiter.tryAcquire("key-b")).isTrue();
    }

    @Test
    void tryAcquire_afterWindowElapses_resets() throws InterruptedException {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(1, Duration.ofMillis(50));

        assertThat(limiter.tryAcquire("key-a")).isTrue();
        assertThat(limiter.tryAcquire("key-a")).isFalse();

        Thread.sleep(80);

        assertThat(limiter.tryAcquire("key-a")).isTrue();
    }
}
