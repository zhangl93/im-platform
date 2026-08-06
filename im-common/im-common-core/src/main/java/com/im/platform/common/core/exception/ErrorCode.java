package com.im.platform.common.core.exception;

/**
 * 全平台统一错误码枚举,各业务模块在此基础上扩展自己的错误码段。
 * 约定:1000~1999 会话/鉴权,2000~2999 用户/群组(biz),
 * 3000~3999 消息(msg),4000~4999 文件/媒体,5000~5999 内容处理管道。
 */
public enum ErrorCode {

    SUCCESS(0, "success"),
    UNKNOWN_ERROR(-1, "unknown error"),
    PARAM_INVALID(-2, "invalid parameter"),

    SESSION_EXPIRED(1001, "session expired"),
    AUTH_FAILED(1002, "authentication failed"),

    USER_NOT_FOUND(2001, "user not found"),
    USER_BLOCKED(2002, "user blocked"),
    GROUP_NOT_FOUND(2101, "group not found"),
    GROUP_MEMBER_LIMIT_EXCEEDED(2102, "group member limit exceeded"),
    GROUP_OWNER_TRANSFER_INVALID(2103, "invalid group owner transfer"),
    GROUP_JOIN_NOT_OPEN(2104, "group requires approval to join"),
    GROUP_JOIN_REQUEST_NOT_FOUND(2105, "group join request not found"),
    GROUP_MEMBER_MUTED(2106, "sender is muted in this group"),

    MESSAGE_SEND_FAILED(3001, "message send failed"),
    MESSAGE_NOT_FOUND(3002, "message not found"),
    MESSAGE_RECALL_NOT_OWNER(3003, "only the sender can recall this message"),
    MESSAGE_RECALL_WINDOW_EXPIRED(3004, "recall window has expired"),

    FILE_UPLOAD_FAILED(4001, "file upload failed");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
