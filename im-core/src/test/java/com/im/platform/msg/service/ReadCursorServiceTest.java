package com.im.platform.msg.service;

import com.im.platform.conversation.RecipientResolver;
import com.im.platform.msg.mapper.ReadCursorMapper;
import com.im.platform.msg.store.MessageStore;
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
        MessageStore messageStore = mock(MessageStore.class);
        when(recipientResolver.resolveRecipients(9001L, 1001L)).thenReturn(List.of(1002L, 1003L));

        ReadCursorService service = new ReadCursorService(readCursorMapper, updateLogService, recipientResolver, messageStore);
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
        MessageStore messageStore = mock(MessageStore.class);
        when(recipientResolver.resolveRecipients(9002L, 2001L)).thenReturn(List.of());

        ReadCursorService service = new ReadCursorService(readCursorMapper, updateLogService, recipientResolver, messageStore);
        service.updateReadCursor(9002L, 2001L, 7000L);

        verify(updateLogService, times(1)).appendForUser(
                eq(2001L), eq(SyncEventTypes.READ_CURSOR_UPDATED), eq("9002:2001:7000".getBytes()));
    }

    @Test
    void getUnreadCount_countsMessagesAfterReadCursor() {
        ReadCursorMapper readCursorMapper = mock(ReadCursorMapper.class);
        UpdateLogService updateLogService = mock(UpdateLogService.class);
        RecipientResolver recipientResolver = mock(RecipientResolver.class);
        MessageStore messageStore = mock(MessageStore.class);
        when(readCursorMapper.selectReadToMessageId(9003L, 3001L)).thenReturn(5000L);
        when(messageStore.countAfter(9003L, 5000L, 3001L)).thenReturn(7L);

        ReadCursorService service = new ReadCursorService(readCursorMapper, updateLogService, recipientResolver, messageStore);
        assertThat(service.getUnreadCount(9003L, 3001L)).isEqualTo(7L);
    }

    @Test
    void getUnreadCount_neverRead_countsFromZero() {
        ReadCursorMapper readCursorMapper = mock(ReadCursorMapper.class);
        UpdateLogService updateLogService = mock(UpdateLogService.class);
        RecipientResolver recipientResolver = mock(RecipientResolver.class);
        MessageStore messageStore = mock(MessageStore.class);
        when(readCursorMapper.selectReadToMessageId(9004L, 4001L)).thenReturn(null);
        when(messageStore.countAfter(9004L, 0L, 4001L)).thenReturn(12L);

        ReadCursorService service = new ReadCursorService(readCursorMapper, updateLogService, recipientResolver, messageStore);
        assertThat(service.getUnreadCount(9004L, 4001L)).isEqualTo(12L);
    }

    @Test
    void getUnreadCount_excludesQueryingUsersOwnSentMessages() {
        // 自己刚发出去的消息不该算进自己的未读数——countAfter 第三个参数就是让实现层
        // 排掉"发送者是自己"的消息,这里断言 excludeSenderId 确实传的是查询者本人。
        ReadCursorMapper readCursorMapper = mock(ReadCursorMapper.class);
        UpdateLogService updateLogService = mock(UpdateLogService.class);
        RecipientResolver recipientResolver = mock(RecipientResolver.class);
        MessageStore messageStore = mock(MessageStore.class);
        when(readCursorMapper.selectReadToMessageId(9005L, 5001L)).thenReturn(0L);
        when(messageStore.countAfter(9005L, 0L, 5001L)).thenReturn(0L);

        ReadCursorService service = new ReadCursorService(readCursorMapper, updateLogService, recipientResolver, messageStore);
        assertThat(service.getUnreadCount(9005L, 5001L)).isEqualTo(0L);
        verify(messageStore).countAfter(9005L, 0L, 5001L);
    }
}
