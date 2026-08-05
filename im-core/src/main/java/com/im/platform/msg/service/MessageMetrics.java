package com.im.platform.msg.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** 消息吞吐量:每次落库成功(MessageWriteService.send 正常返回)计一次。 */
@Component
public class MessageMetrics {

    private final Counter messagesSentCounter;

    public MessageMetrics(MeterRegistry registry) {
        this.messagesSentCounter = Counter.builder("im_core_messages_sent_total")
                .description("消息发送成功(落库)总数")
                .register(registry);
    }

    public void recordMessageSent() {
        messagesSentCounter.increment();
    }
}
