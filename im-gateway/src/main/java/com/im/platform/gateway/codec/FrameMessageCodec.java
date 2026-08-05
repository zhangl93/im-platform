package com.im.platform.gateway.codec;

import com.im.platform.common.protocol.EncryptedFrame;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;

import java.util.List;

/**
 * 长度前缀帧解析 + EncryptedFrame 结构解析。只做字节层面的拆包/拼包,
 * 不关心 msgKey 是不是密钥/IV、encryptedData 是不是真的加密过——那是 MethodRouter
 * (配合 AesGcmCipher)的职责,authKeyId==0 时 encryptedData 其实是未加密的 payload。
 *
 * 帧格式: [4字节总长][8字节authKeyId][12字节msgKey(即 AES-GCM 的 IV)][剩余 encryptedData]
 */
public class FrameMessageCodec extends ByteToMessageCodec<EncryptedFrame> {

    private static final int MSG_KEY_LEN = 12;

    @Override
    protected void encode(ChannelHandlerContext ctx, EncryptedFrame frame, ByteBuf out) {
        int bodyLen = 8 + MSG_KEY_LEN + frame.getEncryptedData().length;
        out.writeInt(bodyLen);
        out.writeLong(frame.getAuthKeyId());
        out.writeBytes(frame.getMsgKey());
        out.writeBytes(frame.getEncryptedData());
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.readableBytes() < 4) {
            return;
        }
        in.markReaderIndex();
        int bodyLen = in.readInt();
        if (in.readableBytes() < bodyLen) {
            in.resetReaderIndex();
            return;
        }

        long authKeyId = in.readLong();
        byte[] msgKey = new byte[MSG_KEY_LEN];
        in.readBytes(msgKey);
        byte[] encryptedData = new byte[bodyLen - 8 - MSG_KEY_LEN];
        in.readBytes(encryptedData);

        out.add(new EncryptedFrame(authKeyId, msgKey, encryptedData));
    }
}
