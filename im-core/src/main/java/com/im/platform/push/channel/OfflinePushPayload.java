package com.im.platform.push.channel;

/**
 * 离线推送要展示的内容。跟 PushMessage(在线推送走 Redis Pub/Sub 那条路径)故意不是同一个
 * 类型——在线推送传的是加密后的完整消息帧,客户端自己解密展示;离线推送是厂商通道
 * (APNs/FCM)代为展示的系统通知,不应该把消息原文往厂商服务器上送,只给最少够用的信息
 * (谁发的、大概是什么类型),具体展示文案由客户端 APP 收到厂商推送后自己决定怎么呈现。
 */
public record OfflinePushPayload(long chatId, long senderId, int msgType) {
}
