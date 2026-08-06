package com.im.platform.core.grpc;

import com.im.platform.biz.interfaces.grpc.GroupGrpcService;
import com.im.platform.biz.interfaces.grpc.UserGrpcService;
import com.im.platform.common.protocol.grpc.BizExceptionInterceptor;
import com.im.platform.dfs.interfaces.grpc.FileGrpcService;
import com.im.platform.msg.MessageGrpcService;
import com.im.platform.push.interfaces.grpc.PushTokenGrpcService;
import com.im.platform.session.interfaces.grpc.SessionGrpcService;
import com.im.platform.status.interfaces.grpc.StatusGrpcService;
import com.im.platform.sync.interfaces.grpc.SyncGrpcService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * im-core 只起一个 gRPC server,把原来分散在 session/biz/msg/sync/status/dfs 六个进程里的
 * gRPC 服务全部注册到同一个端口上。这是合并成单体服务之后最直接的运维收益:
 * 一个端口、一个进程、一次部署,不再需要 Nacos 服务发现和六套独立的服务间调用配置。
 */
@Component
public class GrpcServerLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(GrpcServerLifecycle.class);

    private final int port;
    private final SessionGrpcService sessionGrpcService;
    private final UserGrpcService userGrpcService;
    private final GroupGrpcService groupGrpcService;
    private final MessageGrpcService messageGrpcService;
    private final SyncGrpcService syncGrpcService;
    private final StatusGrpcService statusGrpcService;
    private final FileGrpcService fileGrpcService;
    private final PushTokenGrpcService pushTokenGrpcService;

    private Server server;
    private volatile boolean running;

    public GrpcServerLifecycle(@Value("${grpc.server.port:9080}") int port,
                                SessionGrpcService sessionGrpcService,
                                UserGrpcService userGrpcService,
                                GroupGrpcService groupGrpcService,
                                MessageGrpcService messageGrpcService,
                                SyncGrpcService syncGrpcService,
                                StatusGrpcService statusGrpcService,
                                FileGrpcService fileGrpcService,
                                PushTokenGrpcService pushTokenGrpcService) {
        this.port = port;
        this.sessionGrpcService = sessionGrpcService;
        this.userGrpcService = userGrpcService;
        this.groupGrpcService = groupGrpcService;
        this.messageGrpcService = messageGrpcService;
        this.syncGrpcService = syncGrpcService;
        this.statusGrpcService = statusGrpcService;
        this.fileGrpcService = fileGrpcService;
        this.pushTokenGrpcService = pushTokenGrpcService;
    }

    @Override
    public void start() {
        try {
            server = ServerBuilder.forPort(port)
                    .addService(sessionGrpcService)
                    .addService(userGrpcService)
                    .addService(groupGrpcService)
                    .addService(messageGrpcService)
                    .addService(syncGrpcService)
                    .addService(statusGrpcService)
                    .addService(fileGrpcService)
                    .addService(pushTokenGrpcService)
                    .intercept(new BizExceptionInterceptor())
                    .build()
                    .start();
            running = true;
            log.info("im-core gRPC server started on port {}, services: session/biz.user/biz.group/msg/sync/status/file/push", port);
        } catch (IOException e) {
            throw new IllegalStateException("failed to start im-core gRPC server on port " + port, e);
        }
    }

    @Override
    public void stop() {
        if (server != null) {
            server.shutdown();
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
