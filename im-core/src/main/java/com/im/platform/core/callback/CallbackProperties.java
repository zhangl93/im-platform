package com.im.platform.core.callback;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * 对应 application.yml 里的 im.callback.hooks.*,key 是 CallbackType 的 kebab-case
 * (比如 before-send-message),Spring Boot relaxed binding 自动转成枚举。
 */
@Component
@ConfigurationProperties(prefix = "im.callback")
public class CallbackProperties {

    private Map<CallbackType, CallbackEndpoint> hooks = new EnumMap<>(CallbackType.class);

    public Map<CallbackType, CallbackEndpoint> getHooks() {
        return hooks;
    }

    public void setHooks(Map<CallbackType, CallbackEndpoint> hooks) {
        this.hooks = hooks;
    }

    public CallbackEndpoint get(CallbackType type) {
        return hooks.get(type);
    }
}
