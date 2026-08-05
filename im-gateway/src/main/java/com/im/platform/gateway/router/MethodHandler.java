package com.im.platform.gateway.router;

/** 一个 method_id 对应的处理器:输入解密后的 payload 字节,输出待加密回传的 payload 字节。 */
@FunctionalInterface
public interface MethodHandler {

    byte[] handle(byte[] payload) throws Exception;
}
