package com.im.platform.core.callback;

/** 用户拉黑关系建立后触发,通知类。 */
public record AfterUserBlockedPayload(long userId, long blockedUserId) implements CallbackPayload {

    @Override
    public CallbackType type() {
        return CallbackType.AFTER_USER_BLOCKED;
    }
}
