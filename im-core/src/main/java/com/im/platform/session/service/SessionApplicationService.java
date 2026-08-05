package com.im.platform.session.service;

import com.im.platform.biz.application.UserApplicationService;
import com.im.platform.biz.domain.user.User;
import com.im.platform.biz.domain.user.UserStatus;
import com.im.platform.common.core.exception.BizException;
import com.im.platform.common.core.exception.ErrorCode;
import com.im.platform.session.auth.CredentialAuthenticator;
import com.im.platform.session.manager.SessionManager;
import com.im.platform.session.manager.SessionRecord;
import org.springframework.stereotype.Service;

/**
 * session.proto 四个 RPC 的编排逻辑,gRPC 适配层(interfaces/grpc)只做入参/出参转换,
 * 不直接操作 SessionManager/CredentialAuthenticator。
 *
 * session -&gt; biz 的调用链:session 和 biz 现在在同一个 JVM 里,不再需要 gRPC 客户端/服务端往返,
 * 直接注入 biz 的应用服务、拿领域对象判断即可——这是合并成 im-core 之后最直接的收益之一。
 */
@Service
public class SessionApplicationService {

    private final SessionManager sessionManager;
    private final CredentialAuthenticator authenticator;
    private final UserApplicationService userApplicationService;

    public SessionApplicationService(SessionManager sessionManager,
                                      CredentialAuthenticator authenticator,
                                      UserApplicationService userApplicationService) {
        this.sessionManager = sessionManager;
        this.authenticator = authenticator;
        this.userApplicationService = userApplicationService;
    }

    public SessionRecord authenticate(String deviceId, byte[] encryptedCredential, String clientVersion, long authKeyId) {
        long userId = authenticator.authenticate(deviceId, encryptedCredential, clientVersion);

        // 凭证只能证明"这串数字是谁提交的",用户是否存在/是否已注销要问 biz 域。
        // getUser 找不到用户时直接抛 BizException(USER_NOT_FOUND),往上抛给 gRPC 拦截器处理。
        User user = userApplicationService.getUser(userId);
        if (user.getStatus() == UserStatus.DEACTIVATED) {
            throw new BizException(ErrorCode.AUTH_FAILED, "user deactivated: " + userId);
        }

        return sessionManager.create(userId, deviceId, authKeyId);
    }

    public SessionRecord validate(String sessionToken) {
        SessionRecord record = sessionManager.get(sessionToken);
        if (record == null || record.isExpired()) {
            return null;
        }
        return record;
    }

    public void close(String sessionToken) {
        sessionManager.remove(sessionToken);
    }

    public CredentialAuthenticator.IssuedCredential issueUserCredential(String appKey, String appSecret,
                                                                          long userId, long expireSeconds) {
        return authenticator.issue(appKey, appSecret, userId, expireSeconds);
    }
}
