package com.im.platform.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 握手阶段的客户端准入控制:校验 NegotiateKeyRequest.app_key 是不是配置的白名单里的一个。
 * 这是"App 级"凭证(证明这是官方/授权的客户端在连,不是随便什么人拿协议格式糊一个包过来),
 * 不是"用户级"凭证——用户身份还是靠后面的 Authenticate 校验,两层职责不混。
 *
 * 白名单为空(没配置)时默认放行:本地开发和现有测试工具(压测脚本、单测里手搓的
 * NegotiateKeyRequest)都不带 app_key,不能因为加了这层校验就全部失效——
 * 部署到生产环境前必须显式配置 gateway.security.allowed-app-keys 才会真正生效。
 */
@Component
public class AppKeyAdmissionControl {

    private final Set<String> allowedAppKeys;

    public AppKeyAdmissionControl(@Value("${gateway.security.allowed-app-keys:}") String allowedAppKeysCsv) {
        this.allowedAppKeys = Arrays.stream(allowedAppKeysCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    public boolean isAllowed(String appKey) {
        if (allowedAppKeys.isEmpty()) {
            return true;
        }
        return appKey != null && allowedAppKeys.contains(appKey);
    }

    public boolean isEnabled() {
        return !allowedAppKeys.isEmpty();
    }
}
