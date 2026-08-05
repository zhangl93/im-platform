package com.im.platform.biz.application;

import com.im.platform.biz.domain.user.User;
import com.im.platform.biz.domain.user.UserRepository;
import com.im.platform.common.core.exception.BizException;
import com.im.platform.common.core.exception.ErrorCode;
import com.im.platform.core.callback.AfterUserBlockedPayload;
import com.im.platform.core.callback.CallbackInvoker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用例编排层:加载聚合 -> 调用聚合方法 -> 持久化 -> 按需触发回调。
 * 不重复领域层已经做过的业务判断。
 */
@Service
public class UserApplicationService {

    private final UserRepository userRepository;
    private final CallbackInvoker callbackInvoker;

    public UserApplicationService(UserRepository userRepository, CallbackInvoker callbackInvoker) {
        this.userRepository = userRepository;
        this.callbackInvoker = callbackInvoker;
    }

    public User getUser(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional
    public void updateProfile(long userId, String nickname, String avatar, String ex) {
        User user = getUser(userId);
        user.updateProfile(nickname, avatar);
        user.setEx(ex);
        userRepository.save(user);
    }

    @Transactional
    public void blockUser(long userId, long targetUserId) {
        User user = getUser(userId);
        user.block(targetUserId);
        userRepository.save(user);
        callbackInvoker.invoke(new AfterUserBlockedPayload(userId, targetUserId));
    }
}
