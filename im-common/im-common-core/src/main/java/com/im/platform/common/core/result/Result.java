package com.im.platform.common.core.result;

import java.io.Serializable;

/**
 * 统一返回体,供 bff 层向客户端返回、以及内部 HTTP 调试接口使用。
 * 服务间通信以 gRPC status/message 为准,不依赖此类。
 */
public class Result<T> implements Serializable {

    private int code;
    private String message;
    private T data;

    public static <T> Result<T> ok(T data) {
        Result<T> result = new Result<>();
        result.code = 0;
        result.message = "OK";
        result.data = data;
        return result;
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> result = new Result<>();
        result.code = code;
        result.message = message;
        return result;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
