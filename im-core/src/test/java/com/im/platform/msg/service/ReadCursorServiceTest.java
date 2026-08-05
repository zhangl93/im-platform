package com.im.platform.msg.service;

import com.im.platform.conversation.RecipientResolver;
import com.im.platform.msg.mapper.ReadCursorMapper;
import com.im.platform.sync.service.SyncEventTypes;
import com.im.platform.sync.service.UpdateLogService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 落库(upsert)之后要给操作者自己和会话里的其它参与者各写一条 READ_CURSOR_UPDATED 更新日志。 */
class ReadCursorServiceTest {

    @Test
    void updateReadCursor_persistsAndNotifiesReaderAndOtherParticipants() {
        ReadCursorMapper readCursorMapper = mock(ReadCursorMapper.class);
        UpdateLogService updateLogService = mock(UpdateLogService.class);
        RecipientResolver recipientResolver = mock(RecipientResolver.class);
        when(recipientResolver.resolveRecipients(9001L, 1001L)).thenReturn(List.of(1002L, 1003L));

        ReadCursorService service = new ReadCursorService(readCursorMapper, updateLogService, recipientResolver);
        service.updateReadCursor(9001L, 1001L, 5000L);

        verify(readCursorMapper).upsert(eq(9001L), eq(1001L), eq(5000L), anyLong());

        byte[] expectedPayload = "9001:1001:5000".getBytes();
        ArgumentCaptor<Long> userIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(updateLogService, times(3)).appendForUser(
                userIdCaptor.capture(), eq(SyncEventTypes.READ_CURSOR_UPDATED), eq(expectedPayload));
        assertThat(userIdCaptor.getAllValues()).containsExactlyInAnyOrder(1001L, 1002L, 1003L);
    }

    @Test
    void updateReadCursor_noOtherParticipants_onlyNotifiesReaderSelf() {
        ReadCursorMapper readCursorMapper = mock(ReadCursorMapper.class);
        UpdateLogService updateLogService = mock(UpdateLogService.class);
        RecipientResolver recipientResolver = mock(RecipientResolver.class);
        when(recipientResolver.resolveRecipients(9002L, 2001L)).thenReturn(List.of());

        ReadCursorService service = new ReadCursorService(readCursorMapper, updateLogService, recipientResolver);
        service.updateReadCursor(9002L, 2001L, 7000L);

        verify(updateLogService, times(1)).appendForUser(
                eq(2001L), eq(SyncEventTypes.READ_CURSOR_UPDATED), eq("9002:2001:7000".getBytes()));
    }
}
