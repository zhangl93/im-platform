package com.im.platform.push.channel;

import com.im.platform.push.domain.PushPlatform;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 每个平台默认注册一个 {@link LoggingOfflinePushChannel}。业务要接真实厂商推送(APNs/FCM),
 * 提供自己的 {@code OfflinePushChannel} 实现、用一模一样的 bean 名字(见下面两个方法名)
 * 注册进 Spring 容器,{@code @ConditionalOnMissingBean(name=...)} 会自动跳过这里的默认实现,
 * 不用改这个类、也不用改 OfflinePushDispatcher。
 */
@Configuration
public class OfflinePushChannelConfig {

    @Bean
    @ConditionalOnMissingBean(name = "iosOfflinePushChannel")
    public OfflinePushChannel iosOfflinePushChannel() {
        return new LoggingOfflinePushChannel(PushPlatform.IOS);
    }

    @Bean
    @ConditionalOnMissingBean(name = "androidOfflinePushChannel")
    public OfflinePushChannel androidOfflinePushChannel() {
        return new LoggingOfflinePushChannel(PushPlatform.ANDROID);
    }
}
