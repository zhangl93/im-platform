package com.im.platform.biz.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.im.platform.biz.domain.user.User;
import com.im.platform.biz.domain.user.UserRepository;
import com.im.platform.biz.domain.user.UserStatus;
import com.im.platform.biz.infrastructure.cache.RedisUserCache;
import com.im.platform.biz.infrastructure.persistence.mapper.UserBlockMapper;
import com.im.platform.biz.infrastructure.persistence.mapper.UserMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User 聚合的持久化实现。读路径先查 RedisUserCache,未命中再查 MySQL 并回填缓存;
 * 写路径直接落库并让缓存失效,不做"写缓存"以避免双写不一致。
 */
@Repository
public class UserRepositoryImpl implements UserRepository {

    private final UserMapper userMapper;
    private final UserBlockMapper userBlockMapper;
    private final RedisUserCache userCache;

    public UserRepositoryImpl(UserMapper userMapper, UserBlockMapper userBlockMapper, RedisUserCache userCache) {
        this.userMapper = userMapper;
        this.userBlockMapper = userBlockMapper;
        this.userCache = userCache;
    }

    @Override
    public Optional<User> findById(long userId) {
        Optional<User> cached = userCache.get(userId);
        if (cached.isPresent()) {
            return cached;
        }

        UserPO po = userMapper.selectById(userId);
        if (po == null) {
            return Optional.empty();
        }

        Set<Long> blocked = userBlockMapper.selectList(
                        new LambdaQueryWrapper<UserBlockPO>().eq(UserBlockPO::getUserId, userId))
                .stream().map(UserBlockPO::getBlockedUserId).collect(Collectors.toSet());

        User user = toDomain(po, blocked);
        userCache.put(user);
        return Optional.of(user);
    }

    @Override
    public void save(User user) {
        UserPO po = toPO(user);
        if (userMapper.selectById(user.getUserId()) == null) {
            userMapper.insert(po);
        } else {
            userMapper.updateById(po);
        }

        for (Long blockedUserId : user.getBlockedUserIds()) {
            UserBlockPO exists = userBlockMapper.selectOne(
                    new LambdaQueryWrapper<UserBlockPO>()
                            .eq(UserBlockPO::getUserId, user.getUserId())
                            .eq(UserBlockPO::getBlockedUserId, blockedUserId));
            if (exists == null) {
                UserBlockPO block = new UserBlockPO();
                block.setUserId(user.getUserId());
                block.setBlockedUserId(blockedUserId);
                userBlockMapper.insert(block);
            }
        }

        userCache.evict(user.getUserId());
    }

    private User toDomain(UserPO po, Set<Long> blockedUserIds) {
        return new User(po.getUserId(), po.getNickname(), po.getAvatar(),
                UserStatus.values()[po.getStatus()], blockedUserIds, po.getEx());
    }

    private UserPO toPO(User user) {
        UserPO po = new UserPO();
        po.setUserId(user.getUserId());
        po.setNickname(user.getNickname());
        po.setAvatar(user.getAvatar());
        po.setStatus(user.getStatus().ordinal());
        po.setEx(user.getEx());
        return po;
    }
}
