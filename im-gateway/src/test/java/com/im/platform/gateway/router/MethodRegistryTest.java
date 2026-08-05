package com.im.platform.gateway.router;

import com.im.platform.common.protocol.grpc.HeartbeatRequest;
import com.im.platform.common.protocol.grpc.HeartbeatResponse;
import com.im.platform.gateway.client.CoreGrpcClients;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CoreGrpcClients 指向一个没有服务端在监听的端口——ManagedChannelBuilder.build() 是惰性的,
 * 不会在构造时真的发起连接,这里测的方法(HEARTBEAT/ACK 是纯本地处理,unknown method_id/
 * 畸形 payload 在到达 core 之前就失败了)也确实都不会真的用到这条 channel。
 * (不用 Mockito mock(CoreGrpcClients.class):这台机器上 Byte Buddy 自attach 不可用,
 * mock 具体类会直接报 MockMaker 初始化失败,构造一个不会被调用的真实 client 更省事也更可靠。)
 */
class MethodRegistryTest {

    private final MethodRegistry registry = new MethodRegistry(new CoreGrpcClients("127.0.0.1", 1));

    @Test
    void dispatch_heartbeat_returnsLocalResponse_withoutTouchingCore() throws Exception {
        HeartbeatRequest request = HeartbeatRequest.newBuilder().setClientTime(1000L).build();

        byte[] responseBytes = registry.dispatch(MethodIds.HEARTBEAT, request.toByteArray());

        HeartbeatResponse response = HeartbeatResponse.parseFrom(responseBytes);
        assertThat(response.getServerTime()).isGreaterThan(0);
    }

    @Test
    void dispatch_unknownMethodId_throwsUnknownMethodException() {
        assertThatThrownBy(() -> registry.dispatch(999999, new byte[0]))
                .isInstanceOf(MethodRegistry.UnknownMethodException.class)
                .hasMessageContaining("999999");
    }

    @Test
    void dispatch_ack_returnsOk_withoutTouchingCore() throws Exception {
        com.im.platform.common.protocol.grpc.AckRequest request =
                com.im.platform.common.protocol.grpc.AckRequest.newBuilder().setMessageId(42L).build();

        byte[] responseBytes = registry.dispatch(MethodIds.ACK, request.toByteArray());

        com.im.platform.common.protocol.grpc.AckResponse response =
                com.im.platform.common.protocol.grpc.AckResponse.parseFrom(responseBytes);
        assertThat(response.getOk()).isTrue();
    }

    @Test
    void dispatch_malformedPayload_propagatesParseException() {
        byte[] garbage = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF};

        assertThatThrownBy(() -> registry.dispatch(MethodIds.HEARTBEAT, garbage))
                .isInstanceOf(Exception.class);
    }
}
