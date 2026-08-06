package com.im.platform.push.interfaces.grpc;

import com.im.platform.common.protocol.grpc.Empty;
import com.im.platform.push.domain.PushPlatform;
import com.im.platform.push.grpc.PushTokenServiceGrpc;
import com.im.platform.push.grpc.RegisterPushTokenRequest;
import com.im.platform.push.grpc.UnregisterPushTokenRequest;
import com.im.platform.push.service.PushTokenService;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

@Component
public class PushTokenGrpcService extends PushTokenServiceGrpc.PushTokenServiceImplBase {

    private final PushTokenService pushTokenService;

    public PushTokenGrpcService(PushTokenService pushTokenService) {
        this.pushTokenService = pushTokenService;
    }

    @Override
    public void registerPushToken(RegisterPushTokenRequest request, StreamObserver<Empty> responseObserver) {
        PushPlatform platform = PushPlatform.values()[request.getPlatform().getNumber()];
        pushTokenService.register(request.getUserId(), request.getDeviceId(), platform, request.getPushToken());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void unregisterPushToken(UnregisterPushTokenRequest request, StreamObserver<Empty> responseObserver) {
        pushTokenService.unregister(request.getUserId(), request.getDeviceId());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
