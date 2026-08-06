package com.im.platform.msg.service;

import com.im.platform.msg.mapper.ConversationSettingMapper;
import com.im.platform.sync.service.SyncEventTypes;
import com.im.platform.sync.service.UpdateLogService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 会话级用户偏好(免打扰/置顶)。跟 ReadCursorService 结构对称,但只写增量同步日志给
 * 操作者自己的其它设备——这是纯个人偏好,不是"对方也要看到"的已读回执,不广播给
 * 会话里的其他参与者。
 */
@Service
public class ConversationSettingService {

    private final ConversationSettingMapper conversationSettingMapper;
    private final UpdateLogService updateLogService;

    public ConversationSettingService(ConversationSettingMapper conversationSettingMapper,
                                       UpdateLogService updateLogService) {
        this.conversationSettingMapper = conversationSettingMapper;
        this.updateLogService = updateLogService;
    }

    public void updateSetting(long userId, long chatId, boolean muted, boolean pinned) {
        long now = System.currentTimeMillis();
        conversationSettingMapper.upsert(userId, chatId, muted, pinned, now);

        byte[] payload = (chatId + ":" + muted + ":" + pinned).getBytes(StandardCharsets.UTF_8);
        updateLogService.appendForUser(userId, SyncEventTypes.CONVERSATION_SETTING_UPDATED, payload);
    }

    public List<ConversationSettingMapper.ConversationSettingRow> getSettings(long userId) {
        return conversationSettingMapper.selectAllForUser(userId);
    }

    /** 行不存在就是没设置过,等价于默认值(不免打扰)。供离线推送触发前判断"这个用户要不要收这条会话的推送通知"用。 */
    public boolean isMuted(long userId, long chatId) {
        return Boolean.TRUE.equals(conversationSettingMapper.selectIsMuted(userId, chatId));
    }
}
