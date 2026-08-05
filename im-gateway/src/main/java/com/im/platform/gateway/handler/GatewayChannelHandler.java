package com.im.platform.gateway.handler;

import com.im.platform.common.protocol.EncryptedFrame;
import com.im.platform.gateway.router.MethodRouter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

/**
 * 单条连接的业务无关处理器:识别出 NegotiateKey(authKeyId==0,帧不加密)还是普通业务帧
 * (要用 authKeyId 对应的连接密钥解密),丢给 MethodRouter 处理。
 *
 * 解密/gRPC 调用/加密这些是阻塞操作,不能直接在 Netty 的 I/O 线程里做(会卡住这个 EventLoop
 * 上其他连接的读写),所以统一提交到独立的业务线程池执行,处理完再通过 ctx 写回
 * (Channel.write 是线程安全的,Netty 会自动把写操作调度回对应的 EventLoop)。
 */
public class GatewayChannelHandler extends SimpleChannelInboundHandler<EncryptedFrame> {

    private static final Logger log = LoggerFactory.getLogger(GatewayChannelHandler.class);

    private final MethodRouter router;
    private final ExecutorService businessExecutor;

    public GatewayChannelHandler(MethodRouter router, ExecutorService businessExecutor) {
        this.router = router;
        this.businessExecutor = businessExecutor;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, EncryptedFrame frame) {
        businessExecutor.execute(() -> handle(ctx, frame));
    }

    private void handle(ChannelHandlerContext ctx, EncryptedFrame frame) {
        try {
            if (frame.getAuthKeyId() == 0L) {
                MethodRouter.NegotiateKeyResult result = router.handleNegotiateKey(frame.getEncryptedData());
                ctx.channel().attr(ChannelAttributes.AUTH_KEY_ID).set(result.authKeyId());
                ctx.writeAndFlush(result.responseFrame());
            } else {
                ctx.writeAndFlush(router.handleBusinessFrame(ctx.channel(), frame));
            }
        } catch (Exception e) {
            log.warn("failed to handle frame from {}, closing connection", ctx.channel().remoteAddress(), e);
            ctx.close();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        // onChannelClosed 里有阻塞的 gRPC 调用(status.setOffline),不能直接在 Netty 的
        // I/O 线程(channelInactive 默认在这上面跑)里做,统一丢业务线程池,和 channelRead0 一致。
        businessExecutor.execute(() -> router.onChannelClosed(ctx.channel()));
        ctx.fireChannelInactive();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            // 超过 idle-timeout-seconds 没收到客户端任何字节(含心跳帧),判定连接死了,踢掉。
            // channelInactive 会在 close() 之后自动触发,清理逻辑不用在这里重复做。
            log.info("connection {} idle timeout, closing", ctx.channel().remoteAddress());
            ctx.close();
        } else {
            ctx.fireUserEventTriggered(evt);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("gateway channel error, closing connection", cause);
        ctx.close();
    }
}
