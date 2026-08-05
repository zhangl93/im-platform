package com.im.platform.biz.infrastructure.cache;

import com.im.platform.biz.domain.user.User;
import com.im.platform.biz.domain.user.UserStatus;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * User 聚合的旁路缓存(Cache-Aside)。只缓存资料字段,拉黑关系单独用 Set 结构存,
 * 便于 block/unblock 时做增量更新而不用整体失效。
 */
@Component
public class RedisUserCache {

    private static final String PROFILE_KEY_PREFIX = "im:biz:user:profile:";
    private static final String BLOCK_KEY_PREFIX = "im:biz:user:blocked:";
    private static final Duration TTL = Duration.ofMinutes(30);

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisUserCache(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Optional<User> get(long userId) {
        HashOperations<String, Object, Object> ops = redisTemplate.opsForHash();
        Map<Object, Object> entries = ops.entries(PROFILE_KEY_PREFIX + userId);
        if (entries.isEmpty()) {
            return Optional.empty();
        }

        Set<Object> rawBlocked = redisTemplate.opsForSet().members(BLOCK_KEY_PREFIX + userId);
        Set<Long> blocked = rawBlocked == null ? new HashSet<>()
                : rawBlocked.stream().map(o -> Long.parseLong(o.toString())).collect(Collectors.toSet());

        User user = new User(userId,
                (String) entries.get("nickname"),
                (String) entries.get("avatar"),
                UserStatus.valueOf((String) entries.get("status")),
                blocked,
                (String) entries.get("ex"));
        return Optional.of(user);
    }

    public void put(User user) {
        String profileKey = PROFILE_KEY_PREFIX + user.getUserId();
        redisTemplate.opsForHash().putAll(profileKey, Map.of(
                "nickname", user.getNickname() == null ? "" : user.getNickname(),
                "avatar", user.getAvatar() == null ? "" : user.getAvatar(),
                "status", user.getStatus().name(),
                "ex", user.getEx() == null ? "" : user.getEx()));
        redisTemplate.expire(profileKey, TTL.toSeconds(), TimeUnit.SECONDS);
    }

    public void evict(long userId) {
        redisTemplate.delete(PROFILE_KEY_PREFIX + userId);
        redisTemplate.delete(BLOCK_KEY_PREFIX + userId);
    }
}
