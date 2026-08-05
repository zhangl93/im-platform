package com.im.platform.common.core.exception;

/**
 * 业务异常基类。gRPC 层由 im-common-protocol 的 BizExceptionInterceptor 统一转换为 io.grpc.Status,
 * HTTP/bff 层负责将其转换为 Result.fail(...)。两边都靠 {@link #getErrorCode()} 做映射,
 * 不用在每个 RPC 方法里手写 try/catch。
 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public int getCode() {
        return errorCode.getCode();
    }
}
