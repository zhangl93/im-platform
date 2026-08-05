package com.im.platform.sync.service;

import java.util.List;

/**
 * msg 模块发消息成功后,直接(同进程内方法调用,不再走 Kafka)通知 sync 模块写增量更新日志。
 * 这是 IM 平台自己的多端同步功能,不是业务可插拔的扩展点,所以不走 callback 机制,
 * 而是走这个内部接口——即使以后要把 sync 拆回独立服务,也只需要换一个实现类
 * (比如改成发 MQ 消息),msg 侧调用代码不用动。
 *
 * recipients 由调用方(msg 模块)传入,而不是这里自己再解析一遍——调用方(MessageWriteService)
 * 已经为了推送路由解析过一次接收者列表,不用重复查两遍。
 */
public interface MessageSyncNotifier {

    void onMessageSent(long messageId, long chatId, long senderId, int msgType, long serverTime, List<Long> recipients);
}
