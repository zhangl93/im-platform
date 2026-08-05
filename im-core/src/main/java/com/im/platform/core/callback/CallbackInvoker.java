package com.im.platform.core.callback;

/**
 * 回调调用的统一契约。默认实现是 {@link HttpCallbackInvoker}(HTTP 回调),
 * 以后要换成消息队列异步通知、本地插件调用等任何形式,只需要新写一个实现类替换掉这个 Bean,
 * 调用方(MessageWriteService 等)不用改一行代码——依赖的是这个接口,不是具体实现(依赖倒置)。
 *
 * 契约(任何实现都必须遵守,保证可替换性):
 * 1. 不抛未受检异常。回调地址不可用、超时、业务没配置等情况都要收敛成 CallbackResult,
 *    不能让"回调机制本身"的问题打断 IM 核心主链路(除非业务显式配置为 fail-closed)。
 * 2. 未针对某个 CallbackType 配置或未启用时,视为业务未接管该钩子,直接 pass()。
 */
public interface CallbackInvoker {

    CallbackResult invoke(CallbackPayload payload);
}
