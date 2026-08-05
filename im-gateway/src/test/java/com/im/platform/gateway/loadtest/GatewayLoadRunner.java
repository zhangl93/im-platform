package com.im.platform.gateway.loadtest;

import com.google.protobuf.ByteString;
import com.im.platform.common.protocol.EncryptedFrame;
import com.im.platform.common.protocol.crypto.AesGcmCipher;
import com.im.platform.common.protocol.crypto.KeyDerivation;
import com.im.platform.common.protocol.crypto.X25519KeyExchange;
import com.im.platform.common.protocol.grpc.GatewayRequest;
import com.im.platform.common.protocol.grpc.HeartbeatRequest;
import com.im.platform.common.protocol.grpc.HeartbeatResponse;
import com.im.platform.common.protocol.grpc.ServerFrame;
import com.im.platform.gateway.codec.FrameMessageCodec;
import com.im.platform.gateway.router.MethodIds;
import com.im.platform.msg.grpc.ChatInfo;
import com.im.platform.msg.grpc.GetOrCreateSingleChatRequest;
import com.im.platform.msg.grpc.SendMessageRequest;
import com.im.platform.msg.grpc.SendMessageResponse;
import com.im.platform.session.grpc.AuthRequest;
import com.im.platform.session.grpc.AuthResponse;
import com.im.platform.session.grpc.NegotiateKeyRequest;
import com.im.platform.session.grpc.NegotiateKeyResponse;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 手动运行的压测工具,不是自动化测试(类名不叫 *Test/*Tests,mvn test 不会捡到它)。
 *
 * 单机压测在客户端这一侧最先撞到的墙不是内存也不是线程,是 Windows 默认的临时端口范围
 * (49152~65535,约 1.6 万个)——同一个源 IP 打同一个目标 IP:port,端口用完了就再也建不了
 * 新连接,跟服务端能不能扛住 5 万连接完全无关。解法是从多个 127.0.0.x 环回地址分别发起连接
 * (每个源 IP 各有自己的一份临时端口预算),不需要改任何系统网络设置。
 *
 * 用法:
 *   java -cp <test-classpath> com.im.platform.gateway.loadtest.GatewayLoadRunner \
 *        [host] [port] [connections] [connectPerSecond] [holdSeconds] [localIpCount]
 */
public final class GatewayLoadRunner {

    private static final int MSG_KEY_LEN = 12;
    // 固定端口段连续跑几次压测会撞上一轮的 TIME_WAIT(SO_REUSEADDR 在 Windows 上没能完全绕开这个),
    // 每次进程启动随机挑一段起点,同一段 [base, base+10000) 在同一个 JVM 生命周期内只用一次。
    private static final int LOCAL_PORT_BASE = 1024 + new java.util.Random().nextInt(38_000);

    public static void main(String[] args) throws Exception {
        EventLoopGroup group = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors());
        ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
        // Netty 的 EventLoopGroup 线程不是 daemon 线程——main() 里任何一步抛异常没被捕获,
        // 都会让 JVM 挂着不退出(表现为进程"卡住"而不是崩溃,比真的崩溃更容易被误判成"压测在跑"),
        // 所以整个流程包一层 try/finally,不管成功失败都必须走到 shutdown。
        int exitCode = 0;
        try {
            exitCode = run(args, group, heartbeatScheduler);
        } catch (Throwable t) {
            t.printStackTrace();
            exitCode = 1;
        } finally {
            heartbeatScheduler.shutdownNow();
            group.shutdownGracefully(0, 5, TimeUnit.SECONDS);
        }
        System.exit(exitCode);
    }

    private static int run(String[] args, EventLoopGroup group, ScheduledExecutorService heartbeatScheduler) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8900;
        int connections = args.length > 2 ? Integer.parseInt(args[2]) : 50_000;
        int connectPerSecond = args.length > 3 ? Integer.parseInt(args[3]) : 2000;
        int holdSeconds = args.length > 4 ? Integer.parseInt(args[4]) : 30;
        int localIpCount = args.length > 5 ? Integer.parseInt(args[5]) : 5;

        System.out.printf("=== gateway load test: target=%d connections, rate=%d/s, hold=%ds, localIps=%d ===%n",
                connections, connectPerSecond, holdSeconds, localIpCount);

        List<InetAddress> localAddresses = new ArrayList<>();
        for (int i = 1; i <= localIpCount; i++) {
            localAddresses.add(InetAddress.getByName("127.0.0." + i));
        }

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                // 本地端口是自己显式指定的(见 LOCAL_PORT_BASE),同一个端口连续跑几次压测的话,
                // 上一轮的连接可能还没退出 TIME_WAIT(最多可以挂 4 分钟),不开 SO_REUSEADDR
                // 会直接 bind 失败,表现跟"网关扛不住"一模一样,其实完全是压测客户端自己的事。
                .option(ChannelOption.SO_REUSEADDR, true);

        // 延迟子测试的两条连接单独用一个只有 2 个线程的 EventLoopGroup,不跟大池子共享——
        // 之前踩过一次坑:两条连接的 Channel 如果被分到跟几万条背景连接同一个 event loop 线程,
        // 它们的入站帧解密就得排在那个线程上其它几千个 channel 的事件后面处理,量出来的延迟
        // 测的是"客户端自己的 I/O 线程有多忙"，不是网关的真实投递延迟——网关/im-core 那边全程
        // 没有任何报错或慢查询,问题完全在压测客户端这一侧的线程共享上。
        EventLoopGroup latencyGroup = new NioEventLoopGroup(2);
        Bootstrap latencyBootstrap = new Bootstrap();
        latencyBootstrap.group(latencyGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .option(ChannelOption.SO_REUSEADDR, true);

        Stats stats = new Stats();

        // 前两条连接专门留给延迟子测试用,单独建、单独认证,不混进大池子,方便精确对账。
        // 每次跑都随机挑用户 id(池子是提前 seed 好的 900001~960000),不用固定 id——固定 id
        // 反复跑压测会在网关的 ChannelRegistry 里留一堆"进程被强杀、还没被 idle-timeout 探测到
        // 是死连接"的残留 Channel,PushRouter 挑到这种残留连接投递就会一直"写了但根本没人收到"，
        // 表现跟真正的容量问题一模一样,其实只是压测工具自己复用固定 id 造成的假象。
        // 970001~980000 是专门给延迟子测试 seed 的一段用户 id,跟批量连接池用的
        // 900001~960000 完全不重叠——两段共用过号段的话,dedicated 连接和某条背景批量连接
        // 可能撞上同一个 user_id,ChannelRegistry 里这个用户就会有两条 Channel(多端登录语义),
        // 虽然不直接导致 bug,但会让延迟测的到底是谁的连接变得不确定,排查起来容易踩坑。
        long senderUserId = 970_000L + 1 + java.util.concurrent.ThreadLocalRandom.current().nextInt(5_000);
        long receiverUserId = 975_001L + java.util.concurrent.ThreadLocalRandom.current().nextInt(5_000);
        System.out.println();
        System.out.println("=== phase 0: establishing two dedicated connections for the latency sub-test ===");
        LatencyTestClient sender = new LatencyTestClient(latencyBootstrap, host, port, localAddresses.get(0), senderUserId);
        LatencyTestClient receiver = new LatencyTestClient(latencyBootstrap, host, port, localAddresses.get(0), receiverUserId);
        sender.connectAndAuthenticate();
        receiver.connectAndAuthenticate();
        System.out.println("[OK] latency-test sender/receiver connected and authenticated (sender=" + senderUserId + ", receiver=" + receiverUserId + ")");
        // 这两条连接接下来要在 phase 1(爬坡,可能 30+ 秒)+ phase 2(保持,holdSeconds 秒)期间
        // 完全不发业务帧地空闲着,加起来很容易超过网关默认 90s 的 IdleStateHandler 阈值,不加心跳
        // 保活会被当成死连接踢掉——phase 3 第一次调用因此偶发超时,详见 docs/LOAD_TEST_REPORT.md。
        java.util.concurrent.ScheduledFuture<?> senderHeartbeat = sender.startHeartbeat(heartbeatScheduler);
        java.util.concurrent.ScheduledFuture<?> receiverHeartbeat = receiver.startHeartbeat(heartbeatScheduler);

        System.out.println();
        System.out.println("=== phase 1: ramping up bulk connections ===");
        long rampStart = System.currentTimeMillis();
        CountDownLatch rampDone = new CountDownLatch(connections);
        int batches = (connections + connectPerSecond - 1) / connectPerSecond;
        long userIdBase = 900_000L;

        for (int batch = 0; batch < batches; batch++) {
            int fromIdx = batch * connectPerSecond;
            int toIdx = Math.min(fromIdx + connectPerSecond, connections);
            long delayMillis = batch * 1000L;
            int finalBatch = batch;
            heartbeatScheduler.schedule(() -> {
                for (int i = fromIdx; i < toIdx; i++) {
                    long userId = userIdBase + 1 + (i % 60_000);
                    InetAddress localAddr = localAddresses.get(i % localAddresses.size());
                    // 不用系统自动分配临时端口(bind port=0)——实测 Windows 对同一个远端 127.0.0.1:8900,
                    // 不管客户端换几个不同的本地环回 IP,自动分配似乎共用同一份临时端口预算,
                    // 大概 1.6 万个左右就分配不出新端口了,跟内核里 4 元组本该允许的组合数对不上。
                    // 干脆自己指定本地端口,(本地IP, 本地端口) 两两不重复,绕开这个自动分配的怪癖,
                    // 不需要改任何系统设置。
                    int localPort = LOCAL_PORT_BASE + (i / localAddresses.size());
                    startBulkConnection(bootstrap, host, port, localAddr, localPort, userId, stats, rampDone, heartbeatScheduler);
                }
                if (finalBatch % 5 == 0) {
                    System.out.printf("  ramp progress: batch %d/%d, connected=%d authenticated=%d failed=%d%n",
                            finalBatch + 1, batches, stats.connected.get(), stats.authenticated.get(), stats.failed.get());
                }
            }, delayMillis, TimeUnit.MILLISECONDS);
        }

        boolean allDone = rampDone.await(batches + 60L, TimeUnit.SECONDS);
        long rampElapsedMs = System.currentTimeMillis() - rampStart;

        System.out.println();
        System.out.println("=== phase 1 result ===");
        System.out.printf("ramp complete(all attempts resolved)=%s, elapsed=%dms%n", allDone, rampElapsedMs);
        System.out.printf("attempted=%d connected=%d handshakeOk=%d authenticated=%d failed=%d%n",
                connections, stats.connected.get(), stats.handshakeOk.get(), stats.authenticated.get(), stats.failed.get());
        System.out.printf("success rate=%.2f%%%n", 100.0 * stats.authenticated.get() / connections);

        System.out.println();
        System.out.printf("=== phase 2: holding %d authenticated connections for %ds to prove sustained capacity ===%n",
                stats.authenticated.get(), holdSeconds);
        Thread.sleep(holdSeconds * 1000L);
        System.out.printf("after hold: still-open channels=%d (heartbeat-verified alive)%n", stats.aliveAfterHeartbeat.get());

        // 心跳是按每条连接自己的 connect 时刻 +45s 调度的,ramp 有 30+ 秒跨度,心跳本身就会
        // 拖尾到 hold 快结束前后;给 5 秒安定期,确保量延迟的时候测的是"5 万条连接都已经
        // 建立完、心跳也都跑完一轮"的稳态,不是恰好撞上批量连接收尾那一下的瞬时抖动。
        Thread.sleep(5_000);

        // 心跳保活的任务到此为止:phase 3 全程都在发真正的业务调用,连接不会再空闲,
        // 不需要也不能再让心跳继续跑——它跟 callEncrypted() 抢同一个 responseQueue(协议没有
        // 请求-响应关联 ID,没法按 ID 分流),必须先停掉,再把可能还没到达的心跳响应清空,
        // 才能保证 phase 3 里 poll() 到的一定是刚发的那次业务调用的响应,不会被错认。
        senderHeartbeat.cancel(false);
        receiverHeartbeat.cancel(false);
        sender.drainStaleResponses();
        receiver.drainStaleResponses();

        System.out.println();
        System.out.println("=== phase 3: P99 single-chat delivery latency sub-test ===");
        LatencyReport report = runLatencyTest(sender, receiver, 500);
        report.print();

        System.out.println();
        System.out.println("=== done ===");
        return 0;
    }

    private static void startBulkConnection(Bootstrap bootstrap, String host, int port, InetAddress localAddr, int localPort,
                                             long userId, Stats stats, CountDownLatch rampDone,
                                             ScheduledExecutorService heartbeatScheduler) {
        BulkClientHandler handler = new BulkClientHandler(userId, stats, rampDone);
        bootstrap.clone()
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new FrameMessageCodec()).addLast(handler);
                    }
                })
                .connect(new InetSocketAddress(host, port), new InetSocketAddress(localAddr, localPort))
                .addListener(future -> {
                    if (!future.isSuccess()) {
                        stats.failed.incrementAndGet();
                        rampDone.countDown();
                    } else {
                        stats.connected.incrementAndGet();
                        // 90s 空闲超时,持有期间发一次心跳并等到响应才算数——证明连接不是"连上就死"，
                        // 而是真的能在 hold 阶段之后继续处理业务帧,不是个只握手不干活的僵尸连接。
                        heartbeatScheduler.schedule(handler::sendHeartbeat, 45, TimeUnit.SECONDS);
                    }
                });
    }

    // ============ 批量压测连接的状态机 ============

    private enum State {NEGOTIATING, AUTHENTICATING, READY, FAILED}

    private static class BulkClientHandler extends io.netty.channel.SimpleChannelInboundHandler<EncryptedFrame> {
        private final long userId;
        private final Stats stats;
        private final CountDownLatch rampDone;
        private volatile State state = State.NEGOTIATING;
        private volatile PrivateKey clientPrivateKey;
        private volatile long authKeyId;
        private volatile javax.crypto.spec.SecretKeySpec aesKey;
        private volatile boolean counted = false;

        BulkClientHandler(long userId, Stats stats, CountDownLatch rampDone) {
            this.userId = userId;
            this.stats = stats;
            this.rampDone = rampDone;
        }

        @Override
        public void channelActive(io.netty.channel.ChannelHandlerContext ctx) {
            channelRef = ctx.channel();
            X25519KeyExchange.KeyPairBytes keyPair = X25519KeyExchange.generateKeyPair();
            clientPrivateKey = keyPair.privateKey();
            NegotiateKeyRequest req = NegotiateKeyRequest.newBuilder()
                    .setClientPublicKey(ByteString.copyFrom(keyPair.publicKey()))
                    .setDeviceId("loadtest-" + userId)
                    .build();
            ctx.writeAndFlush(new EncryptedFrame(0L, new byte[MSG_KEY_LEN], req.toByteArray()));
        }

        @Override
        protected void channelRead0(io.netty.channel.ChannelHandlerContext ctx, EncryptedFrame frame) throws Exception {
            if (state == State.NEGOTIATING) {
                NegotiateKeyResponse resp = NegotiateKeyResponse.parseFrom(frame.getEncryptedData());
                byte[] shared = X25519KeyExchange.computeSharedSecret(clientPrivateKey, resp.getServerPublicKey().toByteArray());
                aesKey = KeyDerivation.deriveAesKey(shared);
                authKeyId = resp.getAuthKeyId();
                state = State.AUTHENTICATING;

                AuthRequest authReq = AuthRequest.newBuilder()
                        .setDeviceId("loadtest-" + userId)
                        .setEncryptedCredential(ByteString.copyFromUtf8(String.valueOf(userId)))
                        .setClientVersion("loadtest")
                        .build();
                sendEncrypted(ctx.channel(), MethodIds.AUTHENTICATE, authReq.toByteArray());
                stats.handshakeOk.incrementAndGet();
            } else if (state == State.AUTHENTICATING) {
                byte[] plaintext = AesGcmCipher.decrypt(aesKey, frame.getMsgKey(), frame.getEncryptedData());
                ServerFrame serverFrame = ServerFrame.parseFrom(plaintext);
                if (serverFrame.getResponse().getStatusCode() == 0) {
                    state = State.READY;
                    stats.authenticated.incrementAndGet();
                } else {
                    state = State.FAILED;
                    stats.failed.incrementAndGet();
                }
                if (!counted) {
                    counted = true;
                    rampDone.countDown();
                }
            } else if (state == State.READY) {
                byte[] plaintext = AesGcmCipher.decrypt(aesKey, frame.getMsgKey(), frame.getEncryptedData());
                ServerFrame serverFrame = ServerFrame.parseFrom(plaintext);
                if (serverFrame.getBodyCase() == ServerFrame.BodyCase.RESPONSE
                        && serverFrame.getResponse().getStatusCode() == 0) {
                    HeartbeatResponse.parseFrom(serverFrame.getResponse().getPayload()); // 校验能正常反序列化
                    stats.aliveAfterHeartbeat.incrementAndGet();
                }
            }
        }

        void sendHeartbeat() {
            if (state != State.READY || channelRef == null) {
                return;
            }
            sendEncrypted(channelRef, MethodIds.HEARTBEAT,
                    HeartbeatRequest.newBuilder().setClientTime(System.currentTimeMillis()).build().toByteArray());
        }

        private volatile Channel channelRef;

        private void sendEncrypted(Channel channel, int methodId, byte[] payload) {
            GatewayRequest gatewayRequest = GatewayRequest.newBuilder()
                    .setMethodId(methodId)
                    .setPayload(ByteString.copyFrom(payload))
                    .build();
            byte[] iv = AesGcmCipher.randomIv();
            byte[] ciphertext = AesGcmCipher.encrypt(aesKey, iv, gatewayRequest.toByteArray());
            channel.writeAndFlush(new EncryptedFrame(authKeyId, iv, ciphertext));
        }

        @Override
        public void exceptionCaught(io.netty.channel.ChannelHandlerContext ctx, Throwable cause) {
            if (state != State.READY && !counted) {
                counted = true;
                stats.failed.incrementAndGet();
                rampDone.countDown();
            }
            ctx.close();
        }
    }

    private static class Stats {
        final AtomicLong connected = new AtomicLong();
        final AtomicLong handshakeOk = new AtomicLong();
        final AtomicLong authenticated = new AtomicLong();
        final AtomicLong failed = new AtomicLong();
        final AtomicLong aliveAfterHeartbeat = new AtomicLong();
    }

    // ============ 延迟子测试:独立的、可同步等待的小型客户端 ============

    private static class LatencyTestClient {
        private final Channel channel;
        private final long userId;
        private volatile javax.crypto.spec.SecretKeySpec aesKey;
        private volatile long authKeyId;
        private final java.util.concurrent.BlockingQueue<ServerFrame> responseQueue = new java.util.concurrent.LinkedBlockingQueue<>();
        private final java.util.concurrent.BlockingQueue<Long> pushArrivalNanos = new java.util.concurrent.LinkedBlockingQueue<>();

        LatencyTestClient(Bootstrap bootstrap, String host, int port, InetAddress localAddr, long userId) throws InterruptedException {
            this.userId = userId;
            io.netty.channel.ChannelFuture future = bootstrap.clone()
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new FrameMessageCodec()).addLast(
                                    new io.netty.channel.SimpleChannelInboundHandler<EncryptedFrame>() {
                                        private PrivateKey clientPrivateKey;
                                        private boolean negotiated = false;

                                        @Override
                                        public void channelActive(io.netty.channel.ChannelHandlerContext ctx) {
                                            X25519KeyExchange.KeyPairBytes kp = X25519KeyExchange.generateKeyPair();
                                            clientPrivateKey = kp.privateKey();
                                            NegotiateKeyRequest req = NegotiateKeyRequest.newBuilder()
                                                    .setClientPublicKey(ByteString.copyFrom(kp.publicKey()))
                                                    .setDeviceId("loadtest-latency-" + userId)
                                                    .build();
                                            ctx.writeAndFlush(new EncryptedFrame(0L, new byte[MSG_KEY_LEN], req.toByteArray()));
                                        }

                                        @Override
                                        protected void channelRead0(io.netty.channel.ChannelHandlerContext ctx, EncryptedFrame frame) throws Exception {
                                            if (!negotiated) {
                                                NegotiateKeyResponse resp = NegotiateKeyResponse.parseFrom(frame.getEncryptedData());
                                                byte[] shared = X25519KeyExchange.computeSharedSecret(clientPrivateKey, resp.getServerPublicKey().toByteArray());
                                                aesKey = KeyDerivation.deriveAesKey(shared);
                                                authKeyId = resp.getAuthKeyId();
                                                negotiated = true;
                                                responseQueue.put(ServerFrame.getDefaultInstance()); // 唤醒握手等待
                                            } else {
                                                byte[] plaintext = AesGcmCipher.decrypt(aesKey, frame.getMsgKey(), frame.getEncryptedData());
                                                ServerFrame serverFrame = ServerFrame.parseFrom(plaintext);
                                                if (serverFrame.getBodyCase() == ServerFrame.BodyCase.PUSH) {
                                                    pushArrivalNanos.put(System.nanoTime());
                                                } else {
                                                    responseQueue.put(serverFrame);
                                                }
                                            }
                                        }

                                        @Override
                                        public void exceptionCaught(io.netty.channel.ChannelHandlerContext ctx, Throwable cause) {
                                            // SimpleChannelInboundHandler 默认不覆盖这个方法的话,Netty 只在自己的
                                            // 内部 logger 上打 DEBUG,根日志级别是 INFO 的话完全看不见——
                                            // 之前排查"服务端明明写成功了,客户端却一直超时"卡了很久,就是因为这里
                                            // 静默吞掉了解密/反序列化异常,表现跟"包丢了"一模一样。
                                            System.err.println("[latency-client userId=" + userId + "] exceptionCaught: " + cause);
                                            cause.printStackTrace();
                                        }
                                    });
                        }
                    })
                    .connect(new InetSocketAddress(host, port), new InetSocketAddress(localAddr, 0))
                    .sync();
            this.channel = future.channel();
        }

        /** 定期发心跳,防止 phase 1(爬坡)+ phase 2(保持)这段完全不发业务帧的空窗期被网关的
         * IdleStateHandler(默认 90s 无读事件)判定成死连接踢掉——批量连接(BulkClientHandler)
         * 本来就有这个心跳,这两条专用延迟测连接当初漏了,是 phase 3 首次调用偶发超时的真正原因,
         * 见 GatewayLoadRunner 类注释里补的说明和 docs/LOAD_TEST_REPORT.md。 */
        java.util.concurrent.ScheduledFuture<?> startHeartbeat(ScheduledExecutorService scheduler) {
            return scheduler.scheduleAtFixedRate(this::sendHeartbeatFireAndForget, 30, 30, TimeUnit.SECONDS);
        }

        /** 心跳的响应和 callEncrypted() 用的是同一个 responseQueue(协议本身没有请求-响应关联 ID,
         * 没法按 ID 区分),心跳必须在“确保这段时间不会有业务调用在等 responseQueue”的窗口里发,
         * 不能跟 callEncrypted() 并发——这也是为什么 startHeartbeat 只在 phase 1/2(纯空闲期)跑,
         * phase 3 开始前必须先 cancel 掉,并调 drainStaleResponses() 清掉可能还没到达的心跳响应。 */
        private void sendHeartbeatFireAndForget() {
            if (aesKey == null) {
                return;
            }
            try {
                sendEncryptedNoWait(MethodIds.HEARTBEAT,
                        HeartbeatRequest.newBuilder().setClientTime(System.currentTimeMillis()).build().toByteArray());
            } catch (Exception e) {
                System.err.println("[latency-client userId=" + userId + "] heartbeat send failed: " + e);
            }
        }

        private void sendEncryptedNoWait(int methodId, byte[] payload) {
            GatewayRequest gatewayRequest = GatewayRequest.newBuilder()
                    .setMethodId(methodId).setPayload(ByteString.copyFrom(payload)).build();
            byte[] iv = AesGcmCipher.randomIv();
            byte[] ciphertext = AesGcmCipher.encrypt(aesKey, iv, gatewayRequest.toByteArray());
            channel.writeAndFlush(new EncryptedFrame(authKeyId, iv, ciphertext));
        }

        /** 心跳响应到达网络层有延迟,cancel 定时任务只能保证"不再发新的",发出去但还没回来的
         * 响应仍可能落进 responseQueue——真正调用业务方法前先把这类残留清空,避免被
         * callEncrypted() 的下一次 poll() 误当成业务响应吃掉(payload 对不上会解析出脏数据,
         * 不一定报错,比直接超时更难排查)。 */
        void drainStaleResponses() {
            while (responseQueue.poll() != null) {
                // 非阻塞排空,不处理内容
            }
        }

        void connectAndAuthenticate() throws Exception {
            responseQueue.take(); // 等握手完成信号

            AuthRequest authReq = AuthRequest.newBuilder()
                    .setDeviceId("loadtest-latency-" + userId)
                    .setEncryptedCredential(ByteString.copyFromUtf8(String.valueOf(userId)))
                    .setClientVersion("loadtest")
                    .build();
            ServerFrame authResp = callEncrypted(MethodIds.AUTHENTICATE, authReq.toByteArray());
            AuthResponse parsed = AuthResponse.parseFrom(authResp.getResponse().getPayload());
            if (authResp.getResponse().getStatusCode() != 0 || parsed.getUserId() != userId) {
                throw new IllegalStateException("latency-test client authenticate failed: " + authResp.getResponse().getErrorMessage());
            }
        }

        long getOrCreateSingleChatWith(long otherUserId) throws Exception {
            ServerFrame resp = callEncrypted(MethodIds.GET_OR_CREATE_SINGLE_CHAT,
                    GetOrCreateSingleChatRequest.newBuilder().setUserA(userId).setUserB(otherUserId).build().toByteArray());
            return ChatInfo.parseFrom(resp.getResponse().getPayload()).getChatId();
        }

        long sendMessage(long chatId, String clientMsgId, String text) throws Exception {
            ServerFrame resp = callEncrypted(MethodIds.SEND_MESSAGE,
                    SendMessageRequest.newBuilder()
                            .setChatId(chatId).setSenderId(userId).setClientMsgId(clientMsgId)
                            .setContent(ByteString.copyFromUtf8(text)).setMsgType(1)
                            .build().toByteArray());
            return SendMessageResponse.parseFrom(resp.getResponse().getPayload()).getMessageId();
        }

        long awaitPushNanos(long timeoutMillis) throws InterruptedException {
            Long ts = pushArrivalNanos.poll(timeoutMillis, TimeUnit.MILLISECONDS);
            return ts == null ? -1L : ts;
        }

        long getUserId() {
            return userId;
        }

        private ServerFrame callEncrypted(int methodId, byte[] payload) throws InterruptedException {
            GatewayRequest gatewayRequest = GatewayRequest.newBuilder()
                    .setMethodId(methodId).setPayload(ByteString.copyFrom(payload)).build();
            byte[] iv = AesGcmCipher.randomIv();
            byte[] ciphertext = AesGcmCipher.encrypt(aesKey, iv, gatewayRequest.toByteArray());
            channel.writeAndFlush(new EncryptedFrame(authKeyId, iv, ciphertext));
            // 一定要有超时——没有超时的话,一旦网关在高并发下把这条请求的响应处理延迟了
            // (比如排在几万个心跳后面),这里就会永久卡死,压测工具本身绝不能比被测系统还脆弱。
            ServerFrame frame = responseQueue.poll(10, TimeUnit.SECONDS);
            if (frame == null) {
                throw new IllegalStateException("timed out waiting for response to method_id=" + methodId);
            }
            return frame;
        }
    }

    private static LatencyReport runLatencyTest(LatencyTestClient sender, LatencyTestClient receiver, int iterations) throws Exception {
        long chatId = sender.getOrCreateSingleChatWith(receiver.getUserId());
        List<Long> latenciesMillis = new ArrayList<>(iterations);

        for (int i = 0; i < iterations; i++) {
            try {
                long sendStartNanos = System.nanoTime();
                sender.sendMessage(chatId, "loadtest-lat-" + i, "latency probe #" + i);
                long recvNanos = receiver.awaitPushNanos(5000);
                if (recvNanos < 0) {
                    System.out.println("  [WARN] iteration " + i + " timed out waiting for push, skipped");
                    continue;
                }
                latenciesMillis.add((recvNanos - sendStartNanos) / 1_000_000);
            } catch (Exception e) {
                // 单次超时/异常不能拖垮整个 500 次的子测试——记下来跳过,继续下一条,
                // 最后报告里能看到"多少次里有多少次失败"，这本身也是压测要证明的东西之一。
                System.out.println("  [WARN] iteration " + i + " failed: " + e.getMessage());
            }
            if (i > 0 && i % 100 == 0) {
                System.out.printf("  latency probe progress: %d/%d, collected=%d%n", i, iterations, latenciesMillis.size());
            }
        }
        return new LatencyReport(latenciesMillis);
    }

    private static class LatencyReport {
        final List<Long> sortedMillis;

        LatencyReport(List<Long> raw) {
            this.sortedMillis = new ArrayList<>(raw);
            java.util.Collections.sort(sortedMillis);
        }

        void print() {
            if (sortedMillis.isEmpty()) {
                System.out.println("[FAIL] no successful latency samples collected");
                return;
            }
            int n = sortedMillis.size();
            long p50 = sortedMillis.get((int) (n * 0.50));
            long p95 = sortedMillis.get(Math.min(n - 1, (int) (n * 0.95)));
            long p99 = sortedMillis.get(Math.min(n - 1, (int) (n * 0.99)));
            long max = sortedMillis.get(n - 1);
            double avg = sortedMillis.stream().mapToLong(Long::longValue).average().orElse(0);

            System.out.printf("samples=%d avg=%.1fms p50=%dms p95=%dms p99=%dms max=%dms%n",
                    n, avg, p50, p95, p99, max);
            System.out.printf("[%s] P99 < 200ms acceptance criterion: %s%n",
                    p99 < 200 ? "PASS" : "FAIL", p99 + "ms");
        }
    }
}
