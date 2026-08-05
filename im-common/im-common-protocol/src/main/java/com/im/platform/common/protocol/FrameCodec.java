package com.im.platform.common.protocol;

/**
 * 二进制帧编解码接口。TL 序列化 + AES 加密的具体实现放在 im-gateway 里
 * (gateway 是唯一直接接触客户端裸协议的模块),这里只定义契约,便于单测替换实现。
 */
public interface FrameCodec {

    EncryptedFrame decode(byte[] raw);

    byte[] encode(EncryptedFrame frame);
}
