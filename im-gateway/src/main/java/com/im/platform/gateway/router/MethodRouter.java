package com.im.platform.gateway.router;

import com.google.protobuf.ByteString;
import com.im.platform.common.protocol.EncryptedFrame;
import com.im.platform.common.protocol.crypto.AesGcmCipher;
import com.im.platform.common.protocol.grpc.AckRequest;
import com.im.platform.common.protocol.grpc.GatewayRequest;
import com.im.platform.common.protocol.grpc.GatewayResponse;
import com.im.platform.common.protocol.grpc.ServerFrame;
import com.im.platform.msg.grpc.AckMessageRequest;
import com.im.platform.gateway.client.CoreGrpcClients;
import com.im.platform.gateway.crypto.ConnectionKeyStore;
import com.im.platform.gateway.handler.ChannelAttributes;
import com.im.platform.gateway.security.AppKeyAdmissionControl;
import com.im.platform.gateway.security.MessageRateLimiter;
import com.im.platform.gateway.session.ChannelRegistry;
import com.im.platform.session.grpc.AuthRequest;
import com.im.platform.session.grpc.AuthResponse;
import com.im.platform.session.grpc.CloseSessionRequest;
import com.im.platform.session.grpc.NegotiateKeyRequest;
import com.im.platform.session.grpc.NegotiateKeyResponse;
import com.im.platform.status.grpc.SetOfflineRequest;
import com.im.platform.status.grpc.SetOnlineRequest;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;

/**
 * 解密后按 method_id 路由到 im-core 对应的 gRPC 调用,再把结果加密回传。
 *
 * NegotiateKey 是唯一的例外:握手阶段还没有连接密钥,帧本身不加密,payload 直接是
 * NegotiateKeyRequest 的字节(不经过 GatewayRequest 信封);响应里的 derived_key 字段
 * 必须在这里被提取进 ConnectionKeyStore 并从转发给客户端的响应里剥掉,不能让客户端看到。
 *
 * Authenticate/Heartbeat/CloseSession 还多做一件事:维护本地 ChannelRegistry 和
 * im-status 的 Redis 在线状态,这些不算"业务转发",单独摘出 postProcess 处理,
 * 不污染 MethodRegistry 的纯转发逻辑。
 */
@Component
public class MethodRouter {

    private static final Logger log = LoggerFactory.getLogger(MethodRouter.class);

    private final CoreGrpcClients core;
    private final ConnectionKeyStore keyStore;
    private final MethodRegistry methodRegistry;
    private final ChannelRegistry channelRegistry;
    private final int heartbeatIntervalSeconds;
    private final AppKeyAdmissionControl admissionControl;
    private final MessageRateLimiter messageRateLimiter;

    public MethodRouter(CoreGrpcClients core,
                         ConnectionKeyStore keyStore,
                         MethodRegistry methodRegistry,
                         ChannelRegistry channelRegistry,
                         @Value("${gateway.heartbeat-interval-seconds:30}") int heartbeatIntervalSeconds,
                         AppKeyAdmissionControl admissionControl,
                         MessageRateLimiter messageRateLimiter) {
        this.core = core;
        this.keyStore = keyStore;
        this.methodRegistry = methodRegistry;
        this.channelRegistry = channelRegistry;
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
        this.admissionControl = admissionControl;
        this.messageRateLimiter = messageRateLimiter;
    }

    /** authKeyId 一起返回,调用方(GatewayChannelHandler)要把它记在这条连接上,连接关闭时清理 ConnectionKeyStore。 */
    public record NegotiateKeyResult(EncryptedFrame responseFrame, long authKeyId) {
    }

    public NegotiateKeyResult handleNegotiateKey(byte[] rawPayload) {
        try {
            NegotiateKeyRequest request = NegotiateKeyRequest.parseFrom(rawPayload);
            if (!admissionControl.isAllowed(request.getAppKey())) {
                throw new SecurityException("app_key not allowed");
            }
            NegotiateKeyResponse fullResponse = core.session.negotiateKey(request);

            SecretKeySpec aesKey = new SecretKeySpec(
                    fullResponse.getDerivedKey().toByteArray(), "AES");
            keyStore.put(fullResponse.getAuthKeyId(), aesKey);

            // 剥掉 derived_key 再转发给客户端——这个字段只用于 gateway 内部建立连接密钥。
            // signature 字段不剥,客户端要靠它校验 server_public_key 真的是持有身份私钥的服务端签发的。
            NegotiateKeyResponse clientSafeResponse = fullResponse.toBuilder()
                    .clearDerivedKey()
                    .build();
            EncryptedFrame frame = new EncryptedFrame(0L, new byte[AesGcmCipher.IV_LENGTH_BYTES], clientSafeResponse.toByteArray());
            return new NegotiateKeyResult(frame, fullResponse.getAuthKeyId());
        } catch (Exception e) {
            log.warn("negotiateKey failed", e);
            throw new IllegalStateException("negotiateKey failed: " + e.getMessage(), e);
        }
    }

    /** 处理握手完成之后的普通业务帧:解密 -&gt; 路由 -&gt; 加密。 */
    public EncryptedFrame handleBusinessFrame(Channel channel, EncryptedFrame frame) {
        SecretKeySpec aesKey = keyStore.get(frame.getAuthKeyId());
        if (aesKey == null) {
            throw new IllegalStateException("unknown auth_key_id, connection must renegotiate: " + frame.getAuthKeyId());
        }

        byte[] plaintext = AesGcmCipher.decrypt(aesKey, frame.getMsgKey(), frame.getEncryptedData());

        GatewayResponse response;
        int methodId = -1;
        byte[] requestPayload = null;
        try {
            GatewayRequest request = GatewayRequest.parseFrom(plaintext);
            methodId = request.getMethodId();
            requestPayload = request.getPayload().toByteArray();
            if (methodId == MethodIds.SEND_MESSAGE && isRateLimited(channel)) {
                throw new StatusRuntimeException(Status.RESOURCE_EXHAUSTED.withDescription("message rate limit exceeded"));
            }
            byte[] responseBytes = methodRegistry.dispatch(methodId, requestPayload);
            response = GatewayResponse.newBuilder()
                    .setStatusCode(0)
                    .setPayload(ByteString.copyFrom(responseBytes))
                    .build();
            postProcess(channel, methodId, requestPayload, responseBytes);
        } catch (StatusRuntimeException e) {
            response = errorResponse(e.getStatus());
        } catch (MethodRegistry.UnknownMethodException e) {
            response = errorResponse(Status.UNIMPLEMENTED.withDescription(e.getMessage()));
        } catch (Exception e) {
            log.warn("dispatch failed, methodId={}", methodId, e);
            response = errorResponse(Status.INTERNAL.withDescription(e.getMessage()));
        }

        ServerFrame serverFrame = ServerFrame.newBuilder().setResponse(response).build();
        byte[] iv = AesGcmCipher.randomIv();
        byte[] ciphertext = AesGcmCipher.encrypt(aesKey, iv, serverFrame.toByteArray());
        return new EncryptedFrame(frame.getAuthKeyId(), iv, ciphertext);
    }

    /**
     * 会话管理相关的副作用,只在对应方法成功之后触发,失败(比如 Authenticate 凭证不对)
     * 不会走到这里,response 已经是错误响应了。
     */
    private void postProcess(Channel channel, int methodId, byte[] requestPayload, byte[] responseBytes) throws Exception {
        if (methodId == MethodIds.AUTHENTICATE) {
            AuthRequest req = AuthRequest.parseFrom(requestPayload);
            AuthResponse resp = AuthResponse.parseFrom(responseBytes);
            bindUser(channel, resp.getUserId(), req.getDeviceId());
        } else if (methodId == MethodIds.HEARTBEAT) {
            refreshOnline(channel);
        } else if (methodId == MethodIds.CLOSE_SESSION) {
            unbindUser(channel);
        } else if (methodId == MethodIds.ACK) {
            ackMessage(channel, requestPayload);
        }
    }

    /** 没登录(还没 USER_ID)的连接不做用户级限流,交给上面的连接级/心跳等其它约束兜底。 */
    private boolean isRateLimited(Channel channel) {
        Long userId = channel.attr(ChannelAttributes.USER_ID).get();
        return userId != null && !messageRateLimiter.tryAcquire(userId);
    }

    private void ackMessage(Channel channel, byte[] requestPayload) throws Exception {
        Long userId = channel.attr(ChannelAttributes.USER_ID).get();
        if (userId == null) {
            return; // 没 Authenticate 就发 ACK,忽略——正常客户端不会这么做
        }
        AckRequest ackRequest = AckRequest.parseFrom(requestPayload);
        core.message.ack(AckMessageRequest.newBuilder()
                .setUserId(userId)
                .setMessageId(ackRequest.getMessageId())
                .build());
    }

    private void bindUser(Channel channel, long userId, String deviceId) {
        channel.attr(ChannelAttributes.USER_ID).set(userId);
        channel.attr(ChannelAttributes.DEVICE_ID).set(deviceId);
        channelRegistry.bind(userId, channel);
        // 多端登录:每个设备的连接都各自绑定,不互相顶掉;在线状态是"至少一端在线"就 online,
        // 每次新连接/心跳都刷新 TTL,内部幂等,重复调用无副作用。
        core.status.setOnline(SetOnlineRequest.newBuilder()
                .setUserId(userId)
                .setDeviceId(deviceId == null ? "" : deviceId)
                .setHeartbeatIntervalSec(heartbeatIntervalSeconds)
                .build());
        log.debug("user {} bound to channel {} (device={})", userId, channel.id(), deviceId);
    }

    private void refreshOnline(Channel channel) {
        Long userId = channel.attr(ChannelAttributes.USER_ID).get();
        if (userId == null) {
            return; // 还没 Authenticate 就发心跳,只保活连接,不刷新在线状态
        }
        String deviceId = channel.attr(ChannelAttributes.DEVICE_ID).get();
        core.status.setOnline(SetOnlineRequest.newBuilder()
                .setUserId(userId)
                .setDeviceId(deviceId == null ? "" : deviceId)
                .setHeartbeatIntervalSec(heartbeatIntervalSeconds)
                .build());
    }

    private void unbindUser(Channel channel) {
        Long userId = channel.attr(ChannelAttributes.USER_ID).get();
        if (userId == null) {
            return;
        }
        boolean fullyOffline = channelRegistry.unbind(userId, channel);
        if (fullyOffline) {
            String deviceId = channel.attr(ChannelAttributes.DEVICE_ID).get();
            core.status.setOffline(SetOfflineRequest.newBuilder()
                    .setUserId(userId)
                    .setDeviceId(deviceId == null ? "" : deviceId)
                    .build());
        }
    }

    /** 连接关闭时调用(不管是不是先发了 CloseSession):清密钥、解绑用户、按需更新在线状态。 */
    public void onChannelClosed(Channel channel) {
        Long authKeyId = channel.attr(ChannelAttributes.AUTH_KEY_ID).get();
        if (authKeyId != null) {
            keyStore.remove(authKeyId);
        }
        unbindUser(channel);
    }

    private GatewayResponse errorResponse(Status status) {
        return GatewayResponse.newBuilder()
                .setStatusCode(status.getCode().value())
                .setErrorMessage(status.getDescription() == null ? status.getCode().name() : status.getDescription())
                .build();
    }
}
