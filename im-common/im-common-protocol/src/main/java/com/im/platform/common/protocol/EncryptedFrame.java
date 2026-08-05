package com.im.platform.common.protocol;

/**
 * 客户端 &lt;-&gt; gateway 自研二进制协议的帧结构(类 MTProto)。
 * gateway 收到该帧后用 authKeyId 查会话密钥解密,再按 methodId 路由到 session 层 gRPC 调用。
 */
public class EncryptedFrame {

    private final long authKeyId;
    private final byte[] msgKey;
    private final byte[] encryptedData;

    public EncryptedFrame(long authKeyId, byte[] msgKey, byte[] encryptedData) {
        this.authKeyId = authKeyId;
        this.msgKey = msgKey;
        this.encryptedData = encryptedData;
    }

    public long getAuthKeyId() {
        return authKeyId;
    }

    public byte[] getMsgKey() {
        return msgKey;
    }

    public byte[] getEncryptedData() {
        return encryptedData;
    }
}
