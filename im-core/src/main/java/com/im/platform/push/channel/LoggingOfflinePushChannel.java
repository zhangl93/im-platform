package com.im.platform.push.channel;

import com.im.platform.push.domain.PushPlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认实现,只打日志、不真的调厂商 API——本地开发和这个项目自身的验证脚本都不需要
 * 真实 APNs/FCM 凭证就能跑通"离线时触发了推送"这条链路。业务要接真实厂商,
 * 参考 {@link OfflinePushChannel} 的说明另外实现、覆盖对应平台的 bean(见
 * {@link OfflinePushChannelConfig})。不是 Spring 组件本身,由 config 类按平台各建一个实例。
 */
public class LoggingOfflinePushChannel implements OfflinePushChannel {

    private static final Logger log = LoggerFactory.getLogger(LoggingOfflinePushChannel.class);

    private final PushPlatform platform;

    public LoggingOfflinePushChannel(PushPlatform platform) {
        this.platform = platform;
    }

    @Override
    public PushPlatform platform() {
        return platform;
    }

    @Override
    public void push(String token, OfflinePushPayload payload) {
        log.info("[offline-push stub, no vendor configured] platform={} token={} chatId={} senderId={} msgType={}",
                platform, token, payload.chatId(), payload.senderId(), payload.msgType());
    }
}
