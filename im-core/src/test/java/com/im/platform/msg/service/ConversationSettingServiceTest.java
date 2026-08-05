package com.im.platform.msg.service;

import com.im.platform.msg.mapper.ConversationSettingMapper;
import com.im.platform.sync.service.SyncEventTypes;
import com.im.platform.sync.service.UpdateLogService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 落库(upsert)之后只给操作者自己写一条 CONVERSATION_SETTING_UPDATED 更新日志——纯个人偏好,不广播给会话里的其他参与者。 */
class ConversationSettingServiceTest {

    @Test
    void updateSetting_persistsAndNotifiesOperatorSelfOnly() {
        ConversationSettingMapper mapper = mock(ConversationSettingMapper.class);
        UpdateLogService updateLogService = mock(UpdateLogService.class);

        ConversationSettingService service = new ConversationSettingService(mapper, updateLogService);
        service.updateSetting(1001L, 9001L, true, false);

        verify(mapper).upsert(eq(1001L), eq(9001L), eq(true), eq(false), anyLong());
        verify(updateLogService, times(1)).appendForUser(
                eq(1001L), eq(SyncEventTypes.CONVERSATION_SETTING_UPDATED), eq("9001:true:false".getBytes()));
    }

    @Test
    void getSettings_returnsRowsFromMapper() {
        ConversationSettingMapper mapper = mock(ConversationSettingMapper.class);
        UpdateLogService updateLogService = mock(UpdateLogService.class);

        ConversationSettingMapper.ConversationSettingRow row = new ConversationSettingMapper.ConversationSettingRow();
        row.setChatId(9001L);
        row.setIsMuted(true);
        row.setIsPinned(false);
        row.setUpdatedAt(12345L);
        when(mapper.selectAllForUser(1001L)).thenReturn(List.of(row));

        ConversationSettingService service = new ConversationSettingService(mapper, updateLogService);
        List<ConversationSettingMapper.ConversationSettingRow> result = service.getSettings(1001L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getChatId()).isEqualTo(9001L);
        assertThat(result.get(0).getIsMuted()).isTrue();
        assertThat(result.get(0).getIsPinned()).isFalse();
    }
}
