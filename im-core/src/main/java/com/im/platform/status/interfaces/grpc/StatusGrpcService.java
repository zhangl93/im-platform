package com.im.platform.status.interfaces.grpc;

import com.im.platform.status.grpc.BatchGetStatusRequest;
import com.im.platform.status.grpc.BatchStatusResponse;
import com.im.platform.status.grpc.GetStatusRequest;
import com.im.platform.status.grpc.SetOfflineRequest;
import com.im.platform.status.grpc.SetOfflineResponse;
import com.im.platform.status.grpc.SetOnlineRequest;
import com.im.platform.status.grpc.SetOnlineResponse;
import com.im.platform.status.grpc.StatusServiceGrpc;
import com.im.platform.status.grpc.UserStatusInfo;
import com.im.platform.status.service.StatusService;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class StatusGrpcService extends StatusServiceGrpc.StatusServiceImplBase {

    private final StatusService statusService;

    public StatusGrpcService(StatusService statusService) {
        this.statusService = statusService;
    }

    @Override
    public void setOnline(SetOnlineRequest request, StreamObserver<SetOnlineResponse> responseObserver) {
        long serverTime = statusService.setOnline(
                request.getUserId(), request.getDeviceId(), request.getHeartbeatIntervalSec());
        responseObserver.onNext(SetOnlineResponse.newBuilder().setServerTime(serverTime).build());
        responseObserver.onCompleted();
    }

    @Override
    public void setOffline(SetOfflineRequest request, StreamObserver<SetOfflineResponse> responseObserver) {
        statusService.setOffline(request.getUserId(), request.getDeviceId());
        responseObserver.onNext(SetOfflineResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void getStatus(GetStatusRequest request, StreamObserver<UserStatusInfo> responseObserver) {
        boolean online = statusService.isOnline(request.getUserId());
        responseObserver.onNext(UserStatusInfo.newBuilder()
                .setUserId(request.getUserId())
                .setOnline(online)
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void batchGetStatus(BatchGetStatusRequest request, StreamObserver<BatchStatusResponse> responseObserver) {
        Map<Long, Boolean> statuses = statusService.batchIsOnline(request.getUserIdsList());

        BatchStatusResponse.Builder builder = BatchStatusResponse.newBuilder();
        statuses.forEach((userId, online) -> builder.addStatuses(UserStatusInfo.newBuilder()
                .setUserId(userId)
                .setOnline(online)
                .build()));

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }
}
