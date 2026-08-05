package com.im.platform.gateway.codec;

import com.im.platform.common.protocol.EncryptedFrame;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 帧格式:[4字节总长][8字节authKeyId][12字节msgKey][剩余encryptedData]。
 * 用 EmbeddedChannel 测,不需要真起端口/真连接。
 */
class FrameMessageCodecTest {

    private EmbeddedChannel channel;

    @BeforeEach
    void setUp() {
        channel = new EmbeddedChannel(new FrameMessageCodec());
    }

    private EncryptedFrame sampleFrame(String text) {
        byte[] msgKey = new byte[12];
        new Random(42).nextBytes(msgKey);
        return new EncryptedFrame(123456789L, msgKey, text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void encodeThenDecode_roundTripsExactly() {
        EncryptedFrame original = sampleFrame("hello frame codec");

        assertThat(channel.writeOutbound(original)).isTrue();
        ByteBuf wire = channel.readOutbound();

        assertThat(channel.writeInbound(wire)).isTrue();
        EncryptedFrame decoded = channel.readInbound();

        assertThat(decoded.getAuthKeyId()).isEqualTo(original.getAuthKeyId());
        assertThat(decoded.getMsgKey()).isEqualTo(original.getMsgKey());
        assertThat(decoded.getEncryptedData()).isEqualTo(original.getEncryptedData());
    }

    @Test
    void splitPacket_acrossMultipleWrites_onlyDecodesOnceFullFrameArrives() {
        EncryptedFrame original = sampleFrame("split across the wire in pieces");
        channel.writeOutbound(original);
        ByteBuf wire = channel.readOutbound();
        int totalBytes = wire.readableBytes();

        // 模拟 TCP 拆包:每次只喂 5 个字节,中途不应该产生任何解码结果
        while (wire.readableBytes() > 5) {
            ByteBuf chunk = wire.readBytes(5);
            channel.writeInbound(chunk);
            assertThat((EncryptedFrame) channel.readInbound()).isNull();
        }
        channel.writeInbound(wire.readBytes(wire.readableBytes()));

        EncryptedFrame decoded = channel.readInbound();
        assertThat(decoded).isNotNull();
        assertThat(decoded.getEncryptedData()).isEqualTo(original.getEncryptedData());
        assertThat(totalBytes).isGreaterThan(0);
    }

    @Test
    void stickyPacket_twoFramesInOneWrite_bothDecodeInOrder() {
        EncryptedFrame first = sampleFrame("first frame");
        EncryptedFrame second = sampleFrame("second frame, glued to the first one");

        channel.writeOutbound(first);
        ByteBuf firstWire = channel.readOutbound();
        channel.writeOutbound(second);
        ByteBuf secondWire = channel.readOutbound();

        ByteBuf glued = Unpooled.wrappedBuffer(firstWire, secondWire);
        channel.writeInbound(glued);

        EncryptedFrame decodedFirst = channel.readInbound();
        EncryptedFrame decodedSecond = channel.readInbound();

        assertThat(decodedFirst.getEncryptedData()).isEqualTo(first.getEncryptedData());
        assertThat(decodedSecond.getEncryptedData()).isEqualTo(second.getEncryptedData());
    }

    @Test
    void incompleteLengthPrefix_doesNotThrow_waitsForMoreBytes() {
        // 连 4 字节长度前缀都没凑齐,decode 应该直接返回等下一次,不能抛异常/越界
        channel.writeInbound(Unpooled.wrappedBuffer(new byte[]{0x00, 0x01}));
        assertThat((EncryptedFrame) channel.readInbound()).isNull();
    }
}
