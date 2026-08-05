package com.im.platform.common.protocol;

/**
 * im-core(发布方)和 im-gateway(订阅方)共用的 Redis Pub/Sub 频道名常量,
 * 两边都依赖 im-common-protocol,放这里避免字符串字面量各写一份、改一边忘改另一边。
 */
public final class PushChannels {

    private PushChannels() {
    }

    /** im-core 落库成功后广播 PushMessage(见 envelope.proto)的频道,所有 gateway 实例都订阅。 */
    public static final String MESSAGE_PUSH = "im:push";
}
