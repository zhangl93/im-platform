package com.im.platform.push.channel;

import com.im.platform.push.domain.PushPlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 按平台把 OfflinePushChannel 列表建成一张表,dispatch 时查表转发。同一个平台如果注册了
 * 不止一个 bean(理论上不该发生,{@link OfflinePushChannelConfig} 用 @ConditionalOnMissingBean
 * 保证默认场景下每个平台只有一个),后注册的覆盖先注册的,不抛异常——这里只是路由,
 * 不是校验配置合法性的地方。
 */
@Component
public class OfflinePushDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OfflinePushDispatcher.class);

    private final Map<PushPlatform, OfflinePushChannel> channelsByPlatform;

    public OfflinePushDispatcher(List<OfflinePushChannel> channels) {
        this.channelsByPlatform = channels.stream()
                .collect(Collectors.toMap(OfflinePushChannel::platform, Function.identity(),
                        (first, second) -> second));
    }

    public void dispatch(PushPlatform platform, String token, OfflinePushPayload payload) {
        OfflinePushChannel channel = channelsByPlatform.get(platform);
        if (channel == null) {
            log.warn("no offline push channel registered for platform={}, skipped", platform);
            return;
        }
        channel.push(token, payload);
    }
}
