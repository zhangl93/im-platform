package com.im.platform.push.service;

import com.im.platform.push.domain.PushPlatform;
import com.im.platform.push.mapper.PushTokenMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 离线推送设备 token 登记。一台设备重复登记直接覆盖旧 token(设备换绑/APNs token 轮换的
 * 常见场景),不需要业务先查一遍是否存在。
 */
@Service
public class PushTokenService {

    private final PushTokenMapper pushTokenMapper;

    public PushTokenService(PushTokenMapper pushTokenMapper) {
        this.pushTokenMapper = pushTokenMapper;
    }

    public void register(long userId, String deviceId, PushPlatform platform, String token) {
        pushTokenMapper.upsert(userId, deviceId, platform.name(), token, System.currentTimeMillis());
    }

    public void unregister(long userId, String deviceId) {
        pushTokenMapper.delete(userId, deviceId);
    }

    public List<PushTokenMapper.PushTokenRow> getTokens(long userId) {
        return pushTokenMapper.selectAllForUser(userId);
    }
}
