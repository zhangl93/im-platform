package com.im.platform.gateway.it;

import com.google.protobuf.ByteString;
import com.im.platform.common.protocol.EncryptedFrame;
import com.im.platform.common.protocol.crypto.AesGcmCipher;
import com.im.platform.common.protocol.crypto.KeyDerivation;
import com.im.platform.common.protocol.crypto.X25519KeyExchange;
import com.im.platform.common.protocol.grpc.GatewayRequest;
import com.im.platform.common.protocol.grpc.GatewayResponse;
import com.im.platform.common.protocol.grpc.ServerFrame;
import com.im.platform.gateway.router.MethodIds;
import com.im.platform.msg.grpc.ChatInfo;
import com.im.platform.msg.grpc.GetOrCreateSingleChatRequest;
import com.im.platform.msg.grpc.MessageServiceGrpc;
import com.im.platform.msg.grpc.SendMessageRequest;
import com.im.platform.msg.grpc.SendMessageResponse;
import com.im.platform.session.grpc.AuthRequest;
import com.im.platform.session.grpc.AuthResponse;
import com.im.platform.session.grpc.NegotiateKeyRequest;
import com.im.platform.session.grpc.NegotiateKeyResponse;
import com.im.platform.sync.grpc.PullUpdatesRequest;
import com.im.platform.sync.grpc.UpdateEvent;
import com.im.platform.sync.grpc.UpdatesResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验收标准 #4:模拟网关实例宕机重启,离线消息不丢。
 *
 * 走真实进程,不是同一个 JVM 里关/开 Spring context 意思一下——im-gateway 和 im-core
 * 本来就是两个独立部署单元,"网关实例宕机重启"就该是真的杀掉网关那个进程再拉起一个新的,
 * im-core(连同 MySQL/Redis)全程不受影响,这才对得上"网关无状态、可以随便重启/扩容"这个
 * 架构前提。用 ProcessBuilder 拉起打包好的 jar,{@link Process#destroyForcibly()} 模拟宕机
 * (不走优雅停机,连接说断就断)。
 *
 * 依赖真实 MySQL(127.0.0.1:3306,im_core 库)和 Redis(127.0.0.1:6379)——跟本项目其它
 * 手工验证方式一致(见 docs/LOAD_TEST_REPORT.md),不是 Testcontainers。这也是为什么这个类
 * 叫 *IT 走 failsafe(mvn verify)而不是 *Test 走 surefire(mvn package):不能让
 * "mvn clean package 一键构建"因为本机没起 Docker 就失败。
 *
 * 用固定端口段(19080/18080 给 im-core,19900/18082 给 im-gateway)避免跟开发环境里
 * 手动跑着的默认端口(9080/8080/8900/8082)实例冲突。
 */
class GatewayCrashRecoveryIT {

    private static final String MYSQL_URL = "jdbc:mysql://127.0.0.1:3306/im_core?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true";
    private static final String MYSQL_USER = "root";
    private static final String MYSQL_PASSWORD = "root";

    private static final int CORE_HTTP_PORT = 18080;
    private static final int CORE_GRPC_PORT = 19080;
    private static final int GATEWAY_HTTP_PORT = 18082;
    private static final int GATEWAY_CLIENT_PORT = 19900;

    private static final int MSG_KEY_LEN = 12;

    private static Process coreProcess;

    @BeforeAll
    static void startCoreOnce() throws Exception {
        File coreJar = resolveJar("../im-core/target", "im-core");
        coreProcess = launchJar(coreJar, "im-core-it.log",
                "--server.port=" + CORE_HTTP_PORT,
                "--grpc.server.port=" + CORE_GRPC_PORT,
                "--spring.cloud.nacos.discovery.enabled=false");
        waitForPort("127.0.0.1", CORE_GRPC_PORT, 30_000);
    }

    @AfterAll
    static void stopCoreOnce() {
        if (coreProcess != null) {
            coreProcess.destroyForcibly();
        }
    }

    private static File resolveJar(String targetDir, String artifactPrefix) {
        File dir = new File(targetDir);
        File[] candidates = dir.listFiles((d, name) ->
                name.startsWith(artifactPrefix) && name.endsWith(".jar")
                        && !name.endsWith("-sources.jar") && !name.contains("original-"));
        if (candidates == null || candidates.length == 0) {
            throw new IllegalStateException("could not find built jar under " + dir.getAbsolutePath()
                    + " — run `mvn package` on the whole reactor first (this IT runs in the `verify` phase, after `package`)");
        }
        return candidates[0];
    }

    private static Process launchJar(File jar, String logFileName, String... args) throws IOException {
        java.util.List<String> command = new java.util.ArrayList<>();
        command.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        command.add("-jar");
        command.add(jar.getAbsolutePath());
        command.addAll(java.util.Arrays.asList(args));

        File logFile = new File(System.getProperty("java.io.tmpdir"), logFileName);
        return new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.to(logFile))
                .redirectErrorStream(true)
                .start();
    }

    private static void waitForPort(String host, int port, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            try (Socket probe = new Socket()) {
                probe.connect(new InetSocketAddress(host, port), 500);
                return;
            } catch (IOException e) {
                Thread.sleep(300);
            }
        }
        throw new IllegalStateException("port " + host + ":" + port + " did not become reachable within " + timeoutMillis + "ms");
    }

    private static void seedUser(long userId, String nickname) throws Exception {
        try (Connection conn = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD);
             Statement st = conn.createStatement()) {
            st.executeUpdate("INSERT INTO t_user (user_id, nickname, status) VALUES (" + userId + ", '" + nickname
                    + "', 0) ON DUPLICATE KEY UPDATE nickname=VALUES(nickname)");
        }
    }

    @Test
    void offlineMessageSurvivesGatewayCrashAndRestart() throws Exception {
        long senderId = 980_000L + ThreadLocalRandom.current().nextInt(5_000);
        long receiverId = 985_001L + ThreadLocalRandom.current().nextInt(5_000);
        seedUser(senderId, "crashtest-sender");
        seedUser(receiverId, "crashtest-receiver");

        File gatewayJar = resolveJar("target", "im-gateway");

        // ===== 阶段 1: 起第一个网关实例,证明连接/鉴权/建会话都正常 =====
        Process gateway1 = launchJar(gatewayJar, "im-gateway-it-1.log",
                "--server.port=" + GATEWAY_HTTP_PORT,
                "--gateway.port=" + GATEWAY_CLIENT_PORT,
                "--core.grpc.host=127.0.0.1",
                "--core.grpc.port=" + CORE_GRPC_PORT,
                "--spring.cloud.nacos.discovery.enabled=false");
        waitForPort("127.0.0.1", GATEWAY_CLIENT_PORT, 30_000);

        long chatId;
        try {
            TestGatewayClient receiver = new TestGatewayClient("127.0.0.1", GATEWAY_CLIENT_PORT);
            receiver.authenticate(receiverId);
            receiver.close(); // 只是证明这条连接在实例 1 上能正常握手鉴权,不需要保持打开

            TestGatewayClient sender = new TestGatewayClient("127.0.0.1", GATEWAY_CLIENT_PORT);
            sender.authenticate(senderId);
            GatewayResponse chatResp = sender.call(MethodIds.GET_OR_CREATE_SINGLE_CHAT,
                    GetOrCreateSingleChatRequest.newBuilder().setUserA(senderId).setUserB(receiverId).build());
            assertThat(chatResp.getStatusCode()).isEqualTo(0);
            chatId = ChatInfo.parseFrom(chatResp.getPayload()).getChatId();
            sender.close();
        } finally {
            // ===== 阶段 2: 模拟网关实例宕机——不是优雅停机,直接强杀 =====
            gateway1.destroyForcibly();
            gateway1.waitFor(10, TimeUnit.SECONDS);
        }

        // 给操作系统一点时间真的把监听端口释放掉,不然新实例 bind 同一个端口可能短暂失败
        Thread.sleep(1_000);

        // ===== 阶段 3: 网关完全不在线的这段时间,消息照样能通过 im-core 落库
        //         (在真实多实例部署里,这条消息本来就是从另一个还活着的网关实例发过来的,
        //         这里直接打 im-core 的 gRPC 是等价的简化,验证的是持久化+补偿链路,
        //         不是"发送"这一步本身——那部分已经在其它任务里验证过了)。 =====
        ManagedChannel coreChannel = ManagedChannelBuilder.forAddress("127.0.0.1", CORE_GRPC_PORT).usePlaintext().build();
        long messageId;
        try {
            MessageServiceGrpc.MessageServiceBlockingStub messageStub = MessageServiceGrpc.newBlockingStub(coreChannel);
            SendMessageResponse sendResp = messageStub.sendMessage(SendMessageRequest.newBuilder()
                    .setChatId(chatId).setSenderId(senderId).setClientMsgId("crash-recovery-it-msg-1")
                    .setContent(ByteString.copyFromUtf8("sent while gateway was down"))
                    .setMsgType(1)
                    .build());
            messageId = sendResp.getMessageId();
        } finally {
            coreChannel.shutdownNow();
        }

        // ===== 阶段 4: 拉起第二个网关实例,模拟"重启"——全新进程,ChannelRegistry/ConnectionKeyStore
        //         这些网关本地内存状态清零,唯一能找回这条消息的路径就是 im-core 侧持久化的 update log。 =====
        Process gateway2 = launchJar(gatewayJar, "im-gateway-it-2.log",
                "--server.port=" + GATEWAY_HTTP_PORT,
                "--gateway.port=" + GATEWAY_CLIENT_PORT,
                "--core.grpc.host=127.0.0.1",
                "--core.grpc.port=" + CORE_GRPC_PORT,
                "--spring.cloud.nacos.discovery.enabled=false");
        try {
            waitForPort("127.0.0.1", GATEWAY_CLIENT_PORT, 30_000);

            TestGatewayClient receiverAfterRestart = new TestGatewayClient("127.0.0.1", GATEWAY_CLIENT_PORT);
            try {
                receiverAfterRestart.authenticate(receiverId);

                GatewayResponse pullResp = receiverAfterRestart.call(MethodIds.PULL_UPDATES,
                        PullUpdatesRequest.newBuilder().setUserId(receiverId).setLastSeq(0).build());
                assertThat(pullResp.getStatusCode()).isEqualTo(0);
                UpdatesResponse updates = UpdatesResponse.parseFrom(pullResp.getPayload());

                boolean recovered = false;
                for (UpdateEvent event : updates.getUpdatesList()) {
                    String payload = new String(event.getPayload().toByteArray(), StandardCharsets.UTF_8);
                    if (payload.startsWith(messageId + ":")) {
                        recovered = true;
                        break;
                    }
                }
                assertThat(recovered)
                        .as("message %s sent while gateway was down must show up in PullUpdates after the new instance starts", messageId)
                        .isTrue();
            } finally {
                receiverAfterRestart.close();
            }
        } finally {
            gateway2.destroyForcibly();
            gateway2.waitFor(10, TimeUnit.SECONDS);
        }
    }

    /** 阻塞式 socket 客户端,走真实协议(X25519 握手 + AES-GCM),只用来跑这一个集成测试,不追求吞吐。 */
    private static final class TestGatewayClient implements AutoCloseable {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private javax.crypto.spec.SecretKeySpec aesKey;
        private long authKeyId;

        TestGatewayClient(String host, int port) throws IOException {
            this.socket = new Socket(host, port);
            this.in = socket.getInputStream();
            this.out = socket.getOutputStream();
        }

        void authenticate(long userId) throws Exception {
            negotiateKey();
            GatewayResponse resp = call(MethodIds.AUTHENTICATE, AuthRequest.newBuilder()
                    .setDeviceId("it-device-" + userId)
                    .setEncryptedCredential(ByteString.copyFromUtf8(String.valueOf(userId)))
                    .setClientVersion("it")
                    .build());
            if (resp.getStatusCode() != 0) {
                throw new IllegalStateException("authenticate failed for user " + userId + ": " + resp.getErrorMessage());
            }
            AuthResponse parsed = AuthResponse.parseFrom(resp.getPayload());
            if (parsed.getUserId() != userId) {
                throw new IllegalStateException("authenticated as wrong user: expected " + userId + " got " + parsed.getUserId());
            }
        }

        private void negotiateKey() throws Exception {
            X25519KeyExchange.KeyPairBytes kp = X25519KeyExchange.generateKeyPair();
            PrivateKey clientPrivateKey = kp.privateKey();
            NegotiateKeyRequest req = NegotiateKeyRequest.newBuilder()
                    .setClientPublicKey(ByteString.copyFrom(kp.publicKey()))
                    .setDeviceId("it-client")
                    .build();
            writeFrame(0L, new byte[MSG_KEY_LEN], req.toByteArray());

            EncryptedFrame respFrame = readFrame();
            NegotiateKeyResponse resp = NegotiateKeyResponse.parseFrom(respFrame.getEncryptedData());
            byte[] shared = X25519KeyExchange.computeSharedSecret(clientPrivateKey, resp.getServerPublicKey().toByteArray());
            this.aesKey = KeyDerivation.deriveAesKey(shared);
            this.authKeyId = resp.getAuthKeyId();
        }

        GatewayResponse call(int methodId, com.google.protobuf.Message payload) throws Exception {
            GatewayRequest gatewayRequest = GatewayRequest.newBuilder()
                    .setMethodId(methodId).setPayload(payload.toByteString()).build();
            byte[] iv = AesGcmCipher.randomIv();
            byte[] ciphertext = AesGcmCipher.encrypt(aesKey, iv, gatewayRequest.toByteArray());
            writeFrame(authKeyId, iv, ciphertext);

            EncryptedFrame respFrame = readFrame();
            byte[] plaintext = AesGcmCipher.decrypt(aesKey, respFrame.getMsgKey(), respFrame.getEncryptedData());
            ServerFrame serverFrame = ServerFrame.parseFrom(plaintext);
            return serverFrame.getResponse();
        }

        private void writeFrame(long authKeyId, byte[] msgKey, byte[] encryptedData) throws IOException {
            DataOutputStream dos = new DataOutputStream(out);
            int bodyLen = 8 + MSG_KEY_LEN + encryptedData.length;
            dos.writeInt(bodyLen);
            dos.writeLong(authKeyId);
            dos.write(msgKey);
            dos.write(encryptedData);
            dos.flush();
        }

        private EncryptedFrame readFrame() throws IOException {
            DataInputStream dis = new DataInputStream(in);
            int bodyLen = dis.readInt();
            long frameAuthKeyId = dis.readLong();
            byte[] msgKey = new byte[MSG_KEY_LEN];
            dis.readFully(msgKey);
            byte[] encryptedData = new byte[bodyLen - 8 - MSG_KEY_LEN];
            dis.readFully(encryptedData);
            return new EncryptedFrame(frameAuthKeyId, msgKey, encryptedData);
        }

        @Override
        public void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
                // 测试收尾,连接反正也要断,关不干净不影响断言结果
            }
        }
    }
}
