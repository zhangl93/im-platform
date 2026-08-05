package com.im.platform.gateway.security;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * 挂在 pipeline 最前面(在 FrameMessageCodec 之前),TCP 连接一建立就检查限流,
 * 超限直接关连接,不浪费后面协议解析/握手的开销。每条连接一个新实例(不是 @Sharable),
 * 但共享注入进来的 ConnectRateLimiter,计数是全局的。
 */
public class ConnectRateLimitHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(ConnectRateLimitHandler.class);

    private final ConnectRateLimiter rateLimiter;

    public ConnectRateLimitHandler(ConnectRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        String ip = extractIp(ctx.channel().remoteAddress());
        if (ip != null && !rateLimiter.tryAcquire(ip)) {
            log.warn("connect rate limit exceeded for ip={}, closing", ip);
            ctx.close();
            return;
        }
        ctx.fireChannelActive();
    }

    private static String extractIp(SocketAddress remoteAddress) {
        if (remoteAddress instanceof InetSocketAddress inetAddress) {
            return inetAddress.getAddress().getHostAddress();
        }
        return null;
    }
}
