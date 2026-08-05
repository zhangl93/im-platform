package com.im.platform.gateway.router;

import com.google.protobuf.ByteString;
import com.im.platform.common.protocol.EncryptedFrame;
import com.im.platform.common.protocol.crypto.AesGcmCipher;
import com.im.platform.common.protocol.grpc.AckRequest;
import com.im.platform.common.protocol.grpc.Empty;
import com.im.platform.common.protocol.grpc.GatewayRequest;
import com.im.platform.common.protocol.grpc.GatewayResponse;
import com.im.platform.common.protocol.grpc.HeartbeatRequest;
import com.im.platform.common.protocol.grpc.HeartbeatResponse;
import com.im.platform.common.protocol.grpc.ServerFrame;
import com.im.platform.gateway.client.CoreGrpcClients;
import com.im.platform.gateway.crypto.ConnectionKeyStore;
import com.im.platform.gateway.handler.ChannelAttributes;
import com.im.platform.gateway.security.AppKeyAdmissionControl;
import com.im.platform.gateway.security.MessageRateLimiter;
import com.im.platform.gateway.session.ChannelRegistry;
import com.im.platform.msg.grpc.AckMessageRequest;
import com.im.platform.msg.grpc.MessageServiceGrpc;
import com.im.platform.msg.grpc.SendMessageRequest;
import com.im.platform.session.grpc.AuthRequest;
import com.im.platform.session.grpc.AuthResponse;
import com.im.platform.session.grpc.CloseSessionRequest;
import com.im.platform.session.grpc.NegotiateKeyRequest;
import com.im.platform.session.grpc.SessionServiceGrpc;
import com.im.platform.status.grpc.SetOfflineRequest;
import com.im.platform.status.grpc.SetOfflineResponse;
import com.im.platform.status.grpc.SetOnlineRequest;
import com.im.platform.status.grpc.SetOnlineResponse;
import com.im.platform.status.grpc.StatusServiceGrpc;
import io.grpc.Server;
import io.grpc.Status;
// im-gateway 只依赖 grpc-netty-shaded(把 io.grpc.netty.* 也一并 relocate 了),没有独立的
// grpc-netty 构件,所以这里只能拿到 shaded 之后的包路径,不是绕开正常依赖去戳内部实现。
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 路由分发 + ACK 转发的核心逻辑测试。core 不 mock 生成类(gRPC BlockingStub 是 final 方法多、
 * mock 起来容易埋坑),而是起一个真的(但是回环端口、进程内)gRPC server 挂假的
 * session/status/message 实现——比 mock 更贴近真实调用路径,也不需要反射改 final 字段。
 */
class MethodRouterTest {

    private Server fakeCoreServer;
    private CoreGrpcClients core;
    private ConnectionKeyStore keyStore;
    private ChannelRegistry channelRegistry;
    private MethodRouter router;
    private AppKeyAdmissionControl admissionControl;
    private MessageRateLimiter messageRateLimiter;

    private final AtomicReference<AuthResponse> authResponseToReturn = new AtomicReference<>();
    private final AtomicReference<AckMessageRequest> lastAckRequest = new AtomicReference<>();
    private final AtomicReference<SetOfflineRequest> lastSetOfflineRequest = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        fakeCoreServer = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                .addService(new FakeSessionService())
                .addService(new FakeStatusService())
                .addService(new FakeMessageService())
                .build()
                .start();

        core = new CoreGrpcClients("127.0.0.1", fakeCoreServer.getPort());
        keyStore = new ConnectionKeyStore(3600);
        channelRegistry = new ChannelRegistry();
        MethodRegistry methodRegistry = new MethodRegistry(core);
        // 空白名单=不启用准入控制,不影响这里已有的握手/路由测试;限流阈值给得足够宽松,
        // 单独的准入控制/限流测试会自己构造更严格的实例。
        admissionControl = new AppKeyAdmissionControl("");
        messageRateLimiter = new MessageRateLimiter(1000);
        router = new MethodRouter(core, keyStore, methodRegistry, channelRegistry, 30, admissionControl, messageRateLimiter);
    }

    @AfterEach
    void tearDown() throws Exception {
        fakeCoreServer.shutdownNow();
        fakeCoreServer.awaitTermination(5, TimeUnit.SECONDS);
    }

    private SecretKeySpec randomKey() {
        byte[] raw = new byte[32];
        new java.security.SecureRandom().nextBytes(raw);
        return new SecretKeySpec(raw, "AES");
    }

    private EncryptedFrame encryptRequest(long authKeyId, SecretKeySpec key, int methodId, com.google.protobuf.Message payload) {
        GatewayRequest gatewayRequest = GatewayRequest.newBuilder()
                .setMethodId(methodId)
                .setPayload(payload.toByteString())
                .build();
        byte[] iv = AesGcmCipher.randomIv();
        byte[] ciphertext = AesGcmCipher.encrypt(key, iv, gatewayRequest.toByteArray());
        return new EncryptedFrame(authKeyId, iv, ciphertext);
    }

    private ServerFrame decryptResponse(SecretKeySpec key, EncryptedFrame frame) throws Exception {
        byte[] plaintext = AesGcmCipher.decrypt(key, frame.getMsgKey(), frame.getEncryptedData());
        return ServerFrame.parseFrom(plaintext);
    }

    @Test
    void handleBusinessFrame_heartbeat_returnsServerFrameWrappedResponse() throws Exception {
        long authKeyId = 111L;
        SecretKeySpec key = randomKey();
        keyStore.put(authKeyId, key);
        EmbeddedChannel channel = new EmbeddedChannel();

        EncryptedFrame request = encryptRequest(authKeyId, key, MethodIds.HEARTBEAT,
                HeartbeatRequest.newBuilder().setClientTime(123L).build());

        EncryptedFrame responseFrame = router.handleBusinessFrame(channel, request);
        ServerFrame serverFrame = decryptResponse(key, responseFrame);

        assertThat(serverFrame.getBodyCase()).isEqualTo(ServerFrame.BodyCase.RESPONSE);
        GatewayResponse gatewayResponse = serverFrame.getResponse();
        assertThat(gatewayResponse.getStatusCode()).isEqualTo(0);
        HeartbeatResponse heartbeatResponse = HeartbeatResponse.parseFrom(gatewayResponse.getPayload());
        assertThat(heartbeatResponse.getServerTime()).isGreaterThan(0);
    }

    @Test
    void handleBusinessFrame_unknownAuthKeyId_throwsIllegalStateException() {
        EmbeddedChannel channel = new EmbeddedChannel();
        EncryptedFrame request = new EncryptedFrame(999L, new byte[12], new byte[]{1, 2, 3});

        assertThatThrownBy(() -> router.handleBusinessFrame(channel, request))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void handleBusinessFrame_authenticate_bindsUserAndRegistersOnline() throws Exception {
        long authKeyId = 222L;
        SecretKeySpec key = randomKey();
        keyStore.put(authKeyId, key);
        EmbeddedChannel channel = new EmbeddedChannel();

        authResponseToReturn.set(AuthResponse.newBuilder()
                .setUserId(777L).setSessionToken("tok").setExpireAt(0L).build());

        EncryptedFrame request = encryptRequest(authKeyId, key, MethodIds.AUTHENTICATE,
                AuthRequest.newBuilder().setDeviceId("dev-1").setEncryptedCredential(ByteString.copyFromUtf8("777")).build());

        EncryptedFrame responseFrame = router.handleBusinessFrame(channel, request);
        ServerFrame serverFrame = decryptResponse(key, responseFrame);

        assertThat(serverFrame.getResponse().getStatusCode()).isEqualTo(0);
        assertThat(channel.attr(ChannelAttributes.USER_ID).get()).isEqualTo(777L);
        assertThat(channelRegistry.isOnlineLocally(777L)).isTrue();
    }

    @Test
    void handleBusinessFrame_ack_forwardsToCoreWithChannelBoundUserId() throws Exception {
        long authKeyId = 333L;
        SecretKeySpec key = randomKey();
        keyStore.put(authKeyId, key);
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.attr(ChannelAttributes.USER_ID).set(888L);

        EncryptedFrame request = encryptRequest(authKeyId, key, MethodIds.ACK,
                AckRequest.newBuilder().setMessageId(555L).build());

        EncryptedFrame responseFrame = router.handleBusinessFrame(channel, request);
        ServerFrame serverFrame = decryptResponse(key, responseFrame);

        assertThat(serverFrame.getResponse().getStatusCode()).isEqualTo(0);
        assertThat(lastAckRequest.get()).isNotNull();
        assertThat(lastAckRequest.get().getUserId()).isEqualTo(888L);
        assertThat(lastAckRequest.get().getMessageId()).isEqualTo(555L);
    }

    @Test
    void handleBusinessFrame_ack_beforeAuthenticate_isIgnoredNotForwarded() throws Exception {
        long authKeyId = 444L;
        SecretKeySpec key = randomKey();
        keyStore.put(authKeyId, key);
        EmbeddedChannel channel = new EmbeddedChannel(); // 没设 USER_ID,模拟没 Authenticate 就发 ACK

        EncryptedFrame request = encryptRequest(authKeyId, key, MethodIds.ACK,
                AckRequest.newBuilder().setMessageId(1L).build());

        router.handleBusinessFrame(channel, request);

        assertThat(lastAckRequest.get()).isNull();
    }

    @Test
    void handleBusinessFrame_closeSession_unbindsUserAndSetsOffline() throws Exception {
        long authKeyId = 555L;
        SecretKeySpec key = randomKey();
        keyStore.put(authKeyId, key);
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.attr(ChannelAttributes.USER_ID).set(999L);
        channel.attr(ChannelAttributes.DEVICE_ID).set("dev-9");
        channelRegistry.bind(999L, channel);
        assertThat(channelRegistry.isOnlineLocally(999L)).isTrue();

        EncryptedFrame request = encryptRequest(authKeyId, key, MethodIds.CLOSE_SESSION,
                CloseSessionRequest.newBuilder().setSessionToken("tok").build());

        router.handleBusinessFrame(channel, request);

        assertThat(channelRegistry.isOnlineLocally(999L)).isFalse();
        assertThat(lastSetOfflineRequest.get()).isNotNull();
        assertThat(lastSetOfflineRequest.get().getUserId()).isEqualTo(999L);
    }

    @Test
    void onChannelClosed_removesConnectionKeyAndUnbindsUser() {
        long authKeyId = 666L;
        SecretKeySpec key = randomKey();
        keyStore.put(authKeyId, key);
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.attr(ChannelAttributes.AUTH_KEY_ID).set(authKeyId);
        channel.attr(ChannelAttributes.USER_ID).set(1000L);
        channelRegistry.bind(1000L, channel);

        router.onChannelClosed(channel);

        assertThat(keyStore.get(authKeyId)).isNull();
        assertThat(channelRegistry.isOnlineLocally(1000L)).isFalse();
    }

    @Test
    void handleNegotiateKey_appKeyNotInWhitelist_rejected() {
        AppKeyAdmissionControl strictAdmission = new AppKeyAdmissionControl("valid-app-key");
        MethodRouter strictRouter = new MethodRouter(core, keyStore, new MethodRegistry(core), channelRegistry,
                30, strictAdmission, messageRateLimiter);

        NegotiateKeyRequest badRequest = NegotiateKeyRequest.newBuilder()
                .setClientPublicKey(ByteString.copyFrom(new byte[32]))
                .setDeviceId("dev-bad")
                .setAppKey("wrong-key")
                .build();

        assertThatThrownBy(() -> strictRouter.handleNegotiateKey(badRequest.toByteArray()))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(SecurityException.class);
    }

    @Test
    void handleNegotiateKey_appKeyInWhitelist_allowed() {
        AppKeyAdmissionControl strictAdmission = new AppKeyAdmissionControl("valid-app-key, other-key");
        MethodRouter strictRouter = new MethodRouter(core, keyStore, new MethodRegistry(core), channelRegistry,
                30, strictAdmission, messageRateLimiter);

        NegotiateKeyRequest goodRequest = NegotiateKeyRequest.newBuilder()
                .setClientPublicKey(ByteString.copyFrom(
                        com.im.platform.common.protocol.crypto.X25519KeyExchange.generateKeyPair().publicKey()))
                .setDeviceId("dev-good")
                .setAppKey("valid-app-key")
                .build();

        MethodRouter.NegotiateKeyResult result = strictRouter.handleNegotiateKey(goodRequest.toByteArray());

        assertThat(result).isNotNull();
        assertThat(result.authKeyId()).isNotZero();
    }

    @Test
    void handleBusinessFrame_sendMessage_exceedsPerUserRateLimit_returnsResourceExhausted() throws Exception {
        MessageRateLimiter strictLimiter = new MessageRateLimiter(1);
        MethodRouter limitedRouter = new MethodRouter(core, keyStore, new MethodRegistry(core), channelRegistry,
                30, admissionControl, strictLimiter);

        long authKeyId = 888L;
        SecretKeySpec key = randomKey();
        keyStore.put(authKeyId, key);
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.attr(ChannelAttributes.USER_ID).set(2001L);

        SendMessageRequest sendReq = SendMessageRequest.newBuilder()
                .setChatId(1L).setSenderId(2001L).setClientMsgId("m1")
                .setContent(ByteString.copyFromUtf8("hi")).setMsgType(1).build();

        EncryptedFrame first = encryptRequest(authKeyId, key, MethodIds.SEND_MESSAGE, sendReq);
        ServerFrame firstFrame = decryptResponse(key, limitedRouter.handleBusinessFrame(channel, first));
        assertThat(firstFrame.getResponse().getStatusCode()).isEqualTo(0);

        EncryptedFrame second = encryptRequest(authKeyId, key, MethodIds.SEND_MESSAGE, sendReq);
        ServerFrame secondFrame = decryptResponse(key, limitedRouter.handleBusinessFrame(channel, second));
        assertThat(secondFrame.getResponse().getStatusCode()).isEqualTo(Status.Code.RESOURCE_EXHAUSTED.value());
    }

    private class FakeSessionService extends SessionServiceGrpc.SessionServiceImplBase {
        @Override
        public void negotiateKey(NegotiateKeyRequest request,
                                  StreamObserver<com.im.platform.session.grpc.NegotiateKeyResponse> responseObserver) {
            byte[] derivedKey = new byte[32];
            new java.security.SecureRandom().nextBytes(derivedKey);
            responseObserver.onNext(com.im.platform.session.grpc.NegotiateKeyResponse.newBuilder()
                    .setAuthKeyId(System.nanoTime())
                    .setServerPublicKey(request.getClientPublicKey())
                    .setDerivedKey(ByteString.copyFrom(derivedKey))
                    .build());
            responseObserver.onCompleted();
        }

        @Override
        public void authenticate(AuthRequest request, StreamObserver<AuthResponse> responseObserver) {
            AuthResponse response = authResponseToReturn.get();
            if (response == null) {
                response = AuthResponse.newBuilder().setUserId(1L).build();
            }
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        @Override
        public void closeSession(CloseSessionRequest request, StreamObserver<Empty> responseObserver) {
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
    }

    private class FakeStatusService extends StatusServiceGrpc.StatusServiceImplBase {
        @Override
        public void setOnline(SetOnlineRequest request, StreamObserver<SetOnlineResponse> responseObserver) {
            responseObserver.onNext(SetOnlineResponse.newBuilder().setServerTime(System.currentTimeMillis()).build());
            responseObserver.onCompleted();
        }

        @Override
        public void setOffline(SetOfflineRequest request, StreamObserver<SetOfflineResponse> responseObserver) {
            lastSetOfflineRequest.set(request);
            responseObserver.onNext(SetOfflineResponse.newBuilder().build());
            responseObserver.onCompleted();
        }
    }

    private class FakeMessageService extends MessageServiceGrpc.MessageServiceImplBase {
        @Override
        public void ack(AckMessageRequest request, StreamObserver<Empty> responseObserver) {
            lastAckRequest.set(request);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }

        @Override
        public void sendMessage(com.im.platform.msg.grpc.SendMessageRequest request,
                                 StreamObserver<com.im.platform.msg.grpc.SendMessageResponse> responseObserver) {
            responseObserver.onNext(com.im.platform.msg.grpc.SendMessageResponse.newBuilder()
                    .setMessageId(1L).setServerTime(System.currentTimeMillis()).build());
            responseObserver.onCompleted();
        }
    }
}
