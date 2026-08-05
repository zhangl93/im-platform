package com.im.platform.gateway.metrics;

import com.im.platform.gateway.session.ChannelRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 在线连接数走 Gauge(直接读 ChannelRegistry 当前值,不用自己维护计数器,天然不会跟真实状态漂移);
 * 吞吐量走 Counter;投递延迟走 Timer(带百分位直方图,/actuator/prometheus 能直接抓出 P99)。
 *
 * 延迟的起点是 PushMessage.server_time(消息落库成功的时刻,在 im-core 生成),终点是
 * PushRouter 真正把加密帧写进 Netty channel 的这一刻——覆盖了 Redis Pub/Sub 跨进程传播
 * + 本地路由查找 + 加密这整段路径,就是压测报告要证明的"端到端单聊投递延迟"那个口径。
 */
@Component
public class GatewayMetrics {

    private final Counter pushDeliveredCounter;
    private final Timer pushDeliveryLatencyTimer;

    public GatewayMetrics(MeterRegistry registry, ChannelRegistry channelRegistry) {
        registry.gauge("im_gateway_online_connections", channelRegistry, ChannelRegistry::onlineConnectionCount);
        registry.gauge("im_gateway_online_users", channelRegistry, ChannelRegistry::onlineUserCount);

        this.pushDeliveredCounter = Counter.builder("im_gateway_messages_pushed_total")
                .description("推送消息成功写入本地连接的总数(单聊直投 + 群聊扩散累加)")
                .register(registry);

        this.pushDeliveryLatencyTimer = Timer.builder("im_gateway_push_delivery_latency")
                .description("从消息落库(server_time)到本地推送写入连接的耗时")
                .publishPercentileHistogram()
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    public void recordPushDelivered(long serverTimeMillis) {
        pushDeliveredCounter.increment();
        long latencyMillis = Math.max(0, System.currentTimeMillis() - serverTimeMillis);
        pushDeliveryLatencyTimer.record(Duration.ofMillis(latencyMillis));
    }
}
