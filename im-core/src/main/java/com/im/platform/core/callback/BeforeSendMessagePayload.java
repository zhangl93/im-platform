package com.im.platform.core.callback;

/**
 * 消息落库前触发。业务在自己的回调端点里做内容审核/风控判断,
 * 返回 pass=false 就能拒绝这条消息(比如接第三方 AI 审核、自定义敏感词库)。
 */
public record BeforeSendMessagePayload(long chatId, long senderId, String content, int msgType)
        implements CallbackPayload {

    @Override
    public CallbackType type() {
        return CallbackType.BEFORE_SEND_MESSAGE;
    }
}
