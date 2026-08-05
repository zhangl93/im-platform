package com.im.platform.gateway.handler;

import io.netty.util.AttributeKey;

/**
 * 挂在 Netty Channel 上的连接级状态,GatewayChannelHandler 和 MethodRouter 都要用,
 * 抽出来避免两边各自定义一份不一致的 key。
 */
public final class ChannelAttributes {

    private ChannelAttributes() {
    }

    /** 握手成功后设置,连接关闭时用它清 ConnectionKeyStore。 */
    public static final AttributeKey<Long> AUTH_KEY_ID = AttributeKey.valueOf("authKeyId");

    /** Authenticate 成功后设置,连接关闭时用它清 ChannelRegistry / 更新在线状态。 */
    public static final AttributeKey<Long> USER_ID = AttributeKey.valueOf("userId");

    /** Authenticate 请求里带的设备标识,多端登录用它区分同一用户的不同连接。 */
    public static final AttributeKey<String> DEVICE_ID = AttributeKey.valueOf("deviceId");
}
