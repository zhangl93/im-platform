package com.im.platform.session.manager;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;

/**
 * 会话状态记录,存储于 Redis(key = session_token),支持多网关实例共享校验。
 */
public class SessionRecord implements Serializable {

    private String sessionToken;
    private long userId;
    private String deviceId;
    private long authKeyId;
    private long expireAt;

    public SessionRecord() {
    }

    public SessionRecord(String sessionToken, long userId, String deviceId, long authKeyId, long expireAt) {
        this.sessionToken = sessionToken;
        this.userId = userId;
        this.deviceId = deviceId;
        this.authKeyId = authKeyId;
        this.expireAt = expireAt;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public long getAuthKeyId() {
        return authKeyId;
    }

    public void setAuthKeyId(long authKeyId) {
        this.authKeyId = authKeyId;
    }

    public long getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(long expireAt) {
        this.expireAt = expireAt;
    }

    /**
     * @JsonIgnore 是必须的:这是个派生属性,不是真实字段。没有这个注解,
     * Jackson(GenericJackson2JsonRedisSerializer 用来做 Redis 序列化)会把它当成
     * 一个叫 "expired" 的 JSON 属性写进去,反序列化时又找不到对应的字段/setter,直接报错。
     */
    @JsonIgnore
    public boolean isExpired() {
        return System.currentTimeMillis() > expireAt;
    }
}
