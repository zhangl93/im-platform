package com.im.platform.gateway.push;

import com.im.platform.common.protocol.EncryptedFrame;
import com.im.platform.common.protocol.crypto.AesGcmCipher;
import com.im.platform.common.protocol.grpc.PushMessage;
import com.im.platform.common.protocol.grpc.ServerFrame;
import com.im.platform.gateway.crypto.ConnectionKeyStore;
import com.im.platform.gateway.handler.ChannelAttributes;
import com.im.platform.gateway.metrics.GatewayMetrics;
import com.im.platform.gateway.session.ChannelRegistry;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.Set;

/**
 * 订阅 im-core 发布的 im:push 频道,收到 PushMessage 后只处理 target_user_id 在本实例
 * ChannelRegistry 里有本地连接的那部分——这就是"跨网关路由"的落地方式:所有 gateway 实例
 * 都订阅同一个频道,谁的本地连接表里有这个用户,谁负责真正往下推,其余实例直接忽略。
 *
 * 一个用户可能同时有多端连接(多设备在线),每一端都要收到推送,所以是遍历
 * ChannelRegistry.getChannels() 挨个投递,不是投一条了事。
 *
 * 某个本地连接如果 ConnectionKeyStore 里查不到密钥(理论上不该发生:连接还在
 * ChannelRegistry 里说明没走 onChannelClosed,密钥不该被清),跳过这一条、记警告日志,
 * 不影响给其他端投递。
 *
 * 连接异常断开(拔网线、进程被杀)不一定会立刻触发 channelInactive——在被 IdleStateHandler
 * 判定超时之前,ChannelRegistry 里可能还留着一条已经死掉但还没被检测到的 Channel。
 * 所以这里不能假设"写进去就算投递成功":写之前先查 isActive() 跳过明显已经死的连接,
 * 写之后还要看 ChannelFuture 是否真的成功,失败就主动从 ChannelRegistry 摘掉(不用等
 * 90 秒空闲超时才清理),metrics 也只在真正写成功时才计数,不然"推送成功率"这个指标本身就是假的。
 */
@Component
public class PushRouter implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(PushRouter.class);

    private final ChannelRegistry channelRegistry;
    private final ConnectionKeyStore keyStore;
    private final GatewayMetrics metrics;

    public PushRouter(ChannelRegistry channelRegistry, ConnectionKeyStore keyStore, GatewayMetrics metrics) {
        this.channelRegistry = channelRegistry;
        this.keyStore = keyStore;
        this.metrics = metrics;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            byte[] raw = Base64.getDecoder().decode(message.getBody());
            PushMessage pushMessage = PushMessage.parseFrom(raw);
            deliver(pushMessage);
        } catch (Exception e) {
            log.warn("failed to handle push message from redis", e);
        }
    }

    private void deliver(PushMessage pushMessage) {
        Set<Channel> channels = channelRegistry.getChannels(pushMessage.getTargetUserId());
        if (channels.isEmpty()) {
            return; // 本实例没有这个用户的连接,交给别的 gateway 实例处理(或者用户根本不在线,由离线补偿兜底)
        }

        ServerFrame serverFrame = ServerFrame.newBuilder().setPush(pushMessage).build();
        byte[] plaintext = serverFrame.toByteArray();

        long targetUserId = pushMessage.getTargetUserId();
        for (Channel channel : channels) {
            if (!channel.isActive()) {
                log.warn("push skipped: channel {} already inactive, removing from registry", channel.id());
                channelRegistry.unbind(targetUserId, channel);
                continue;
            }
            Long authKeyId = channel.attr(ChannelAttributes.AUTH_KEY_ID).get();
            if (authKeyId == null) {
                continue;
            }
            SecretKeySpec aesKey = keyStore.get(authKeyId);
            if (aesKey == null) {
                log.warn("push skipped: no connection key for authKeyId={}, channel={}", authKeyId, channel.id());
                continue;
            }
            byte[] iv = AesGcmCipher.randomIv();
            byte[] ciphertext = AesGcmCipher.encrypt(aesKey, iv, plaintext);
            channel.writeAndFlush(new EncryptedFrame(authKeyId, iv, ciphertext)).addListener(future -> {
                if (future.isSuccess()) {
                    metrics.recordPushDelivered(pushMessage.getServerTime());
                } else {
                    log.warn("push write failed on channel {}, removing from registry", channel.id(), future.cause());
                    channelRegistry.unbind(targetUserId, channel);
                }
            });
        }
    }
}
