package com.im.platform.msg.service;

import com.im.platform.conversation.RecipientResolver;
import com.im.platform.msg.mapper.ReadCursorMapper;
import com.im.platform.sync.service.SyncEventTypes;
import com.im.platform.sync.service.UpdateLogService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * 已读游标:落库(单调递增,见 ReadCursorMapper.upsert)之后往两类人写增量同步日志——
 * 1) 操作者自己的其它设备(多端已读状态同步:这台设备读到哪,那台设备也得知道)
 * 2) 会话里的其它参与者(已读回执:对方能看到"消息已读到第几条")
 * 两者复用同一条 update_log 写入路径,payload 格式一样,客户端按 event_type 区分处理即可。
 * t_read_cursor 固定在 MySQL 上(一个用户一个会话一行,量级远小于消息数据,不需要分片),
 * 跟存到 MongoDB 里的消息数据是两套独立的存储,互不影响。
 */
@Service
public class ReadCursorService {

    private final ReadCursorMapper readCursorMapper;
    private final UpdateLogService updateLogService;
    private final RecipientResolver recipientResolver;

    public ReadCursorService(ReadCursorMapper readCursorMapper,
                              UpdateLogService updateLogService,
                              RecipientResolver recipientResolver) {
        this.readCursorMapper = readCursorMapper;
        this.updateLogService = updateLogService;
        this.recipientResolver = recipientResolver;
    }

    public void updateReadCursor(long chatId, long userId, long readToMessageId) {
        readCursorMapper.upsert(chatId, userId, readToMessageId, System.currentTimeMillis());

        byte[] payload = (chatId + ":" + userId + ":" + readToMessageId).getBytes(StandardCharsets.UTF_8);
        updateLogService.appendForUser(userId, SyncEventTypes.READ_CURSOR_UPDATED, payload);
        for (Long otherParticipant : recipientResolver.resolveRecipients(chatId, userId)) {
            updateLogService.appendForUser(otherParticipant, SyncEventTypes.READ_CURSOR_UPDATED, payload);
        }
    }

    /** 供业务查询"这个用户在这个会话读到哪了"用,比如群里展示"已读 N 人"。 */
    public long getReadToMessageId(long chatId, long userId) {
        Long value = readCursorMapper.selectReadToMessageId(chatId, userId);
        return value == null ? 0L : value;
    }
}
