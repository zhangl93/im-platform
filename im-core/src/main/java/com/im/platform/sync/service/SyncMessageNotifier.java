package com.im.platform.sync.service;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MessageSyncNotifier 的默认实现:给发送者本人(多端同步"我发过这条消息")和每个接收者
 * (离线补偿——接收者重新上线后 PullUpdates 能拉到这条消息)各写一条更新日志。
 */
@Component
public class SyncMessageNotifier implements MessageSyncNotifier {

    private final UpdateLogService updateLogService;

    public SyncMessageNotifier(UpdateLogService updateLogService) {
        this.updateLogService = updateLogService;
    }

    @Override
    public void onMessageSent(long messageId, long chatId, long senderId, int msgType, long serverTime, List<Long> recipients) {
        byte[] payload = (messageId + ":" + chatId + ":" + msgType).getBytes();
        updateLogService.appendForUser(senderId, SyncEventTypes.NEW_MESSAGE, payload);
        for (Long recipientId : recipients) {
            updateLogService.appendForUser(recipientId, SyncEventTypes.NEW_MESSAGE, payload);
        }
    }
}
