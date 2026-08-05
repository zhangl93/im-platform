package com.im.platform.gateway.server;

import com.im.platform.gateway.codec.FrameMessageCodec;
import com.im.platform.gateway.handler.GatewayChannelHandler;
import com.im.platform.gateway.router.MethodRouter;
import com.im.platform.gateway.security.ConnectRateLimitHandler;
import com.im.platform.gateway.security.ConnectRateLimiter;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 客户端长连接入口。职责仅限于:接收字节流 -&gt; 解析成 EncryptedFrame -&gt; 转发给 im-core,
 * 不在这里做鉴权、限流以外的业务判断。
 */
@Component
public class NettyGatewayServer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(NettyGatewayServer.class);

    private final int port;
    private final int idleTimeoutSeconds;
    private final int businessThreadPoolSize;
    private final MethodRouter router;
    private final ConnectRateLimiter connectRateLimiter;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ExecutorService businessExecutor;
    private volatile boolean running;

    public NettyGatewayServer(@Value("${gateway.port:8900}") int port,
                               @Value("${gateway.idle-timeout-seconds:90}") int idleTimeoutSeconds,
                               @Value("${gateway.business-thread-pool-size:500}") int businessThreadPoolSize,
                               MethodRouter router,
                               ConnectRateLimiter connectRateLimiter) {
        this.port = port;
        this.idleTimeoutSeconds = idleTimeoutSeconds;
        this.businessThreadPoolSize = businessThreadPoolSize;
        this.router = router;
        this.connectRateLimiter = connectRateLimiter;
    }

    @Override
    public void start() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        // 业务线程池:解密/加密是 CPU 操作,但 AUTHENTICATE/HEARTBEAT/ACK/CLOSE_SESSION 的
        // postProcess 都会同步阻塞调 im-core 的 gRPC(网络 I/O),线程池大小要按 I/O 并发量估,
        // 不能按 CPU 核数估——压测 5 万连接时用 2×CPU 核数(几十个线程)实测会在心跳高峰期把
        // 无关请求的排队时间拖到几秒甚至超时,数百个线程闲着大部分时间在等网络 I/O,开销很小。
        businessExecutor = Executors.newFixedThreadPool(businessThreadPoolSize);

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                // 默认 backlog 在 Windows 上偏小(常见 somaxconn≈200),压测/突发重连风暴时
                // accept 队列瞬间打满,超出的连接直接被 RST——client 端表现为 connect() 失败,
                // 跟网关"扛不住并发连接数"是两回事,不调大这个值,压测数字会失真。
                .option(io.netty.channel.ChannelOption.SO_BACKLOG, 65536)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                // 挂最前面:按源 IP 限流,超限直接关连接,不浪费后面协议解析的开销。
                                .addLast(new ConnectRateLimitHandler(connectRateLimiter))
                                // 只关心"读空闲"——多久没收到客户端任何字节(含心跳帧)。
                                // 触发后 GatewayChannelHandler.userEventTriggered 负责真正踢线。
                                .addLast(new IdleStateHandler(idleTimeoutSeconds, 0, 0))
                                .addLast(new FrameMessageCodec())
                                .addLast(new GatewayChannelHandler(router, businessExecutor));
                    }
                });

        try {
            bootstrap.bind(port).sync();
            running = true;
            log.info("im-gateway listening on port {}", port);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("failed to bind gateway port " + port, e);
        }
    }

    @Override
    public void stop() {
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (businessExecutor != null) {
            businessExecutor.shutdown();
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
