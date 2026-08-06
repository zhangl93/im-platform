package com.im.platform.push.channel;

import com.im.platform.push.domain.PushPlatform;

/**
 * 离线推送厂商通道的抽象(APNs/FCM/国内厂商推送等)。平台不内置任何一家厂商的真实 SDK
 * 调用——APNs 证书、FCM service account 这些凭证是业务自己申请的 App/Firebase 项目下的,
 * 平台代码里没有也不应该有真实可用的凭证。默认只注册 {@link LoggingOfflinePushChannel}
 * (行为等同于"配置了但没接真实厂商",只记日志不真的发送);业务要接真实推送,实现这个接口、
 * 用 @Primary 或 profile 覆盖对应平台的默认 bean 即可,不用改 OfflinePushDispatcher
 * 或调用方的任何代码(开闭原则,跟 core.callback 包的扩展点是同一个思路)。
 */
public interface OfflinePushChannel {

    PushPlatform platform();

    void push(String token, OfflinePushPayload payload);
}
