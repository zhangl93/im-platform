package com.im.platform.core.push;

import com.im.platform.common.protocol.PushChannels;
import com.im.platform.common.protocol.grpc.PushMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * 把一条已经落库成功的消息,以 PushMessage 的形式广播到 Redis Pub/Sub,让所有 gateway 实例
 * (不管是不是持有目标用户连接的那个实例)都收到,由各自本地的 ChannelRegistry 决定是否要
 * 真正推给客户端。这里只管发布,不关心到底有没有人在线接——离线的话由消息可靠性模块
 * (ACK/离线补偿,见后续任务)兜底,不是这一层的职责。
 *
 * StringRedisTemplate 只能发字符串,protobuf 字节用 Base64 包一层。
 */
@Component
public class PushPublisher {

    private final StringRedisTemplate stringRedisTemplate;

    public PushPublisher(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void publish(long targetUserId, long messageId, long chatId, long senderId,
                         byte[] content, int msgType, long serverTime, List<Long> atUserIds) {
        PushMessage message = PushMessage.newBuilder()
                .setTargetUserId(targetUserId)
                .setMessageId(messageId)
                .setChatId(chatId)
                .setSenderId(senderId)
                .setContent(com.google.protobuf.ByteString.copyFrom(content))
                .setMsgType(msgType)
                .setServerTime(serverTime)
                .addAllAtUserIds(atUserIds == null ? Collections.emptyList() : atUserIds)
                .build();
        String encoded = Base64.getEncoder().encodeToString(message.toByteArray());
        stringRedisTemplate.convertAndSend(PushChannels.MESSAGE_PUSH, encoded);
    }
}
