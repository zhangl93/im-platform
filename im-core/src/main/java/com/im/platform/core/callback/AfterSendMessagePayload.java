package com.im.platform.core.callback;

/**
 * 消息落库成功后触发,通知类,不影响发送结果。业务可以在这里接翻译、AI 分析、
 * 推荐信号提取等——这些原来是 im-moderation/im-translation/im-ai-analysis 独立服务做的事情,
 * 现在收拢成"业务自己决定要不要接、接了自己维护"。
 */
public record AfterSendMessagePayload(long messageId, long chatId, long senderId, int msgType, long serverTime)
        implements CallbackPayload {

    @Override
    public CallbackType type() {
        return CallbackType.AFTER_SEND_MESSAGE;
    }
}
