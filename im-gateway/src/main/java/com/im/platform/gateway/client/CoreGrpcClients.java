package com.im.platform.gateway.client;

import com.im.platform.biz.grpc.GroupServiceGrpc;
import com.im.platform.biz.grpc.UserServiceGrpc;
import com.im.platform.dfs.grpc.FileServiceGrpc;
import com.im.platform.msg.grpc.MessageServiceGrpc;
import com.im.platform.push.grpc.PushTokenServiceGrpc;
import com.im.platform.session.grpc.SessionServiceGrpc;
import com.im.platform.status.grpc.StatusServiceGrpc;
import com.im.platform.sync.grpc.SyncServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 持有到 im-core 的单个 gRPC 连接和 7 个服务的 BlockingStub。
 * gateway 是瘦网关,不做业务判断,只负责把解密后的调用转发给 im-core 并把结果加密回传。
 */
@Component
public class CoreGrpcClients {

    private final ManagedChannel channel;

    public final SessionServiceGrpc.SessionServiceBlockingStub session;
    public final UserServiceGrpc.UserServiceBlockingStub user;
    public final GroupServiceGrpc.GroupServiceBlockingStub group;
    public final MessageServiceGrpc.MessageServiceBlockingStub message;
    public final SyncServiceGrpc.SyncServiceBlockingStub sync;
    public final StatusServiceGrpc.StatusServiceBlockingStub status;
    public final FileServiceGrpc.FileServiceBlockingStub file;
    public final PushTokenServiceGrpc.PushTokenServiceBlockingStub pushToken;

    public CoreGrpcClients(@Value("${core.grpc.host:127.0.0.1}") String host,
                            @Value("${core.grpc.port:9080}") int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext() // 内网调用,先不做 mTLS
                .build();
        this.session = SessionServiceGrpc.newBlockingStub(channel);
        this.user = UserServiceGrpc.newBlockingStub(channel);
        this.group = GroupServiceGrpc.newBlockingStub(channel);
        this.message = MessageServiceGrpc.newBlockingStub(channel);
        this.sync = SyncServiceGrpc.newBlockingStub(channel);
        this.status = StatusServiceGrpc.newBlockingStub(channel);
        this.file = FileServiceGrpc.newBlockingStub(channel);
        this.pushToken = PushTokenServiceGrpc.newBlockingStub(channel);
    }

    @PreDestroy
    void shutdown() {
        channel.shutdown();
    }
}
