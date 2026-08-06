package com.im.platform.common.protocol.grpc;

import com.im.platform.common.core.exception.BizException;
import com.im.platform.common.core.exception.ErrorCode;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

/**
 * 每个 gRPC server 在构建时统一挂上这个拦截器,RPC 方法体直接抛 BizException 即可,
 * 不用在每个方法里手写 try/catch 转 Status。一元调用(unary call)的业务逻辑实际是在
 * onHalfClose() 回调里同步执行的,所以拦截点选在这里。
 */
public class BizExceptionInterceptor implements ServerInterceptor {

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

        ServerCall.Listener<ReqT> delegate = next.startCall(call, headers);
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(delegate) {
            @Override
            public void onHalfClose() {
                try {
                    super.onHalfClose();
                } catch (BizException e) {
                    call.close(toStatus(e).withDescription(e.getMessage()).withCause(e), new Metadata());
                } catch (RuntimeException e) {
                    call.close(Status.INTERNAL.withDescription(e.getMessage()).withCause(e), new Metadata());
                }
            }
        };
    }

    private Status toStatus(BizException e) {
        ErrorCode errorCode = e.getErrorCode();
        return switch (errorCode) {
            case PARAM_INVALID -> Status.INVALID_ARGUMENT;
            case USER_NOT_FOUND, GROUP_NOT_FOUND, MESSAGE_NOT_FOUND -> Status.NOT_FOUND;
            case SESSION_EXPIRED, AUTH_FAILED -> Status.UNAUTHENTICATED;
            case GROUP_NOT_MEMBER -> Status.PERMISSION_DENIED;
            case USER_BLOCKED, GROUP_MEMBER_LIMIT_EXCEEDED, GROUP_OWNER_TRANSFER_INVALID,
                    MESSAGE_RECALL_NOT_OWNER, MESSAGE_RECALL_WINDOW_EXPIRED ->
                    Status.FAILED_PRECONDITION;
            default -> Status.UNKNOWN;
        };
    }
}
