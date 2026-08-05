package com.im.platform.core.callback;

/**
 * 所有钩子类型统一返回这个结果,调用方不用为每种钩子写不同的判断逻辑。
 * "通知类"钩子(如 AFTER_SEND_MESSAGE)调用方通常直接忽略这个结果;
 * "拦截类"钩子(如 BEFORE_SEND_MESSAGE)调用方在 pass()==false 时中断主流程。
 */
public record CallbackResult(boolean pass, String rejectReason) {

    private static final CallbackResult OK = new CallbackResult(true, null);

    /** 静态工厂方法名不能叫 pass(),会和 record 自动生成的 pass() 取值方法冲突。 */
    public static CallbackResult ok() {
        return OK;
    }

    public static CallbackResult reject(String reason) {
        return new CallbackResult(false, reason);
    }
}
