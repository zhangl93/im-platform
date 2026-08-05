package com.im.platform.core.callback;

/**
 * 每个具体的钩子事件(BeforeSendMessagePayload 等)都实现这个接口。
 * CallbackInvoker 只依赖这个抽象,任何新的 Payload 实现都能直接传进去调用,
 * 不需要 CallbackInvoker 知道具体是哪种事件(里氏替换:子类型可以在任何用到 CallbackPayload 的地方替换使用)。
 */
public interface CallbackPayload {

    CallbackType type();
}
