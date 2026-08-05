package com.im.platform.core.callback;

/**
 * 业务可以接管的扩展点。新增一个钩子只需要在这里加一个枚举值 + 一个对应的 Payload 类,
 * 不用改 CallbackInvoker 或任何调用点的代码(开闭原则:对扩展开放,对修改关闭)。
 *
 * enabled=false / 没配 URL 时,CallbackInvoker 直接放行,不影响主链路——
 * 业务没接管的钩子,行为等同于这个钩子不存在。
 */
public enum CallbackType {

    /** 消息落库前触发,业务可以在这里做自己的内容审核/风控,返回拒绝可以阻断发送。 */
    BEFORE_SEND_MESSAGE,

    /** 消息落库成功后触发,通知类,业务可以在这里接翻译/推荐信号提取等异步处理,不影响发送结果。 */
    AFTER_SEND_MESSAGE,

    /** 用户被拉黑后触发,通知类。 */
    AFTER_USER_BLOCKED,
}
