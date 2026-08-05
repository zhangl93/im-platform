package com.im.platform.gateway.security;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 key(IP / userId)分桶的定长窗口限流器:每个 key 独立计数,窗口到期整体重置。
 * 不是严格的滑动窗口(窗口边界处允许短暂超过 2 倍阈值的突发),但实现简单、无锁竞争小,
 * 对"挡住持续超额请求"这个目标够用——真要做精确速率控制再换令牌桶,没必要在这提前优化。
 */
public class FixedWindowRateLimiter {

    private final int limit;
    private final long windowMillis;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    private static final class Window {
        volatile long windowStartMillis;
        int count;
    }

    public FixedWindowRateLimiter(int limit, Duration window) {
        this.limit = limit;
        this.windowMillis = window.toMillis();
    }

    /** @return true 表示放行,false 表示这个 key 在当前窗口内已经超限。 */
    public boolean tryAcquire(String key) {
        long now = System.currentTimeMillis();
        Window window = windows.computeIfAbsent(key, k -> new Window());
        synchronized (window) {
            if (now - window.windowStartMillis >= windowMillis) {
                window.windowStartMillis = now;
                window.count = 0;
            }
            window.count++;
            return window.count <= limit;
        }
    }
}
