package com.im.platform.msg.service;

import com.im.platform.common.core.exception.BizException;
import com.im.platform.common.core.exception.ErrorCode;
import com.im.platform.conversation.RecipientResolver;
import com.im.platform.msg.entity.MessageEntity;
import com.im.platform.msg.store.MessageStore;
import com.im.platform.sync.service.SyncEventTypes;
import com.im.platform.sync.service.UpdateLogService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageRecallServiceTest {

    private static final long CHAT_ID = 100L;
    private static final long SENDER = 1L;
    private static final long OTHER_USER = 2L;
    private static final long MESSAGE_ID = 5000L;

    private MessageEntity newMessage(long serverTime, boolean recalled) {
        MessageEntity entity = new MessageEntity();
        entity.setMessageId(MESSAGE_ID);
        entity.setChatId(CHAT_ID);
        entity.setSenderId(SENDER);
        entity.setServerTime(serverTime);
        entity.setRecalled(recalled);
        return entity;
    }

    @Test
    void recall_withinWindow_marksRecalledAndNotifiesParticipants() {
        MessageStore messageStore = mock(MessageStore.class);
        RecipientResolver recipientResolver = mock(RecipientResolver.class);
        UpdateLogService updateLogService = mock(UpdateLogService.class);
        when(messageStore.findById(CHAT_ID, MESSAGE_ID)).thenReturn(newMessage(System.currentTimeMillis(), false));
        when(recipientResolver.resolveRecipients(CHAT_ID, SENDER)).thenReturn(List.of(OTHER_USER));

        MessageRecallService service = new MessageRecallService(messageStore, recipientResolver, updateLogService);
        service.recall(CHAT_ID, SENDER, MESSAGE_ID);

        verify(messageStore).markRecalled(CHAT_ID, MESSAGE_ID);
        byte[] expectedPayload = (CHAT_ID + ":" + MESSAGE_ID).getBytes();
        verify(updateLogService).appendForUser(eq(SENDER), eq(SyncEventTypes.MESSAGE_RECALLED), eq(expectedPayload));
        verify(updateLogService).appendForUser(eq(OTHER_USER), eq(SyncEventTypes.MESSAGE_RECALLED), eq(expectedPayload));
    }

    @Test
    void recall_notSender_rejected() {
        MessageStore messageStore = mock(MessageStore.class);
        RecipientResolver recipientResolver = mock(RecipientResolver.class);
        UpdateLogService updateLogService = mock(UpdateLogService.class);
        when(messageStore.findById(CHAT_ID, MESSAGE_ID)).thenReturn(newMessage(System.currentTimeMillis(), false));

        MessageRecallService service = new MessageRecallService(messageStore, recipientResolver, updateLogService);

        assertThatThrownBy(() -> service.recall(CHAT_ID, OTHER_USER, MESSAGE_ID))
                .isInstanceOf(BizException.class)
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.MESSAGE_RECALL_NOT_OWNER));
        verify(messageStore, never()).markRecalled(CHAT_ID, MESSAGE_ID);
    }

    @Test
    void recall_windowExpired_rejected() {
        MessageStore messageStore = mock(MessageStore.class);
        RecipientResolver recipientResolver = mock(RecipientResolver.class);
        UpdateLogService updateLogService = mock(UpdateLogService.class);
        long threeMinutesAgo = System.currentTimeMillis() - java.time.Duration.ofMinutes(3).toMillis();
        when(messageStore.findById(CHAT_ID, MESSAGE_ID)).thenReturn(newMessage(threeMinutesAgo, false));

        MessageRecallService service = new MessageRecallService(messageStore, recipientResolver, updateLogService);

        assertThatThrownBy(() -> service.recall(CHAT_ID, SENDER, MESSAGE_ID))
                .isInstanceOf(BizException.class)
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.MESSAGE_RECALL_WINDOW_EXPIRED));
        verify(messageStore, never()).markRecalled(CHAT_ID, MESSAGE_ID);
    }

    @Test
    void recall_alreadyRecalled_idempotentNoOp() {
        MessageStore messageStore = mock(MessageStore.class);
        RecipientResolver recipientResolver = mock(RecipientResolver.class);
        UpdateLogService updateLogService = mock(UpdateLogService.class);
        when(messageStore.findById(CHAT_ID, MESSAGE_ID)).thenReturn(newMessage(System.currentTimeMillis(), true));

        MessageRecallService service = new MessageRecallService(messageStore, recipientResolver, updateLogService);
        assertThatCode(() -> service.recall(CHAT_ID, SENDER, MESSAGE_ID)).doesNotThrowAnyException();

        verify(messageStore, never()).markRecalled(CHAT_ID, MESSAGE_ID);
        verify(updateLogService, times(0)).appendForUser(eq(SENDER), eq(SyncEventTypes.MESSAGE_RECALLED), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void recall_messageNotFound_rejected() {
        MessageStore messageStore = mock(MessageStore.class);
        RecipientResolver recipientResolver = mock(RecipientResolver.class);
        UpdateLogService updateLogService = mock(UpdateLogService.class);
        when(messageStore.findById(CHAT_ID, MESSAGE_ID)).thenReturn(null);

        MessageRecallService service = new MessageRecallService(messageStore, recipientResolver, updateLogService);

        assertThatThrownBy(() -> service.recall(CHAT_ID, SENDER, MESSAGE_ID))
                .isInstanceOf(BizException.class)
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.MESSAGE_NOT_FOUND));
    }

    @Test
    void recall_chatIdMismatch_rejected() {
        // 消息真实所在的会话是 CHAT_ID,调用方却声称是 OTHER_CHAT_ID —— 不能通过伪造 chat_id
        // 让撤回通知广播给一个不相关会话的参与者。
        long otherChatId = 999L;
        MessageStore messageStore = mock(MessageStore.class);
        RecipientResolver recipientResolver = mock(RecipientResolver.class);
        UpdateLogService updateLogService = mock(UpdateLogService.class);
        when(messageStore.findById(otherChatId, MESSAGE_ID)).thenReturn(newMessage(System.currentTimeMillis(), false));

        MessageRecallService service = new MessageRecallService(messageStore, recipientResolver, updateLogService);

        assertThatThrownBy(() -> service.recall(otherChatId, SENDER, MESSAGE_ID))
                .isInstanceOf(BizException.class)
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.MESSAGE_NOT_FOUND));
        verify(messageStore, never()).markRecalled(otherChatId, MESSAGE_ID);
        verify(recipientResolver, never()).resolveRecipients(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void recall_alreadyRecalled_byNonOwner_stillRejected() {
        // 幂等短路必须排在权限校验之后:已经撤回过的消息,非发送者调用不能返回成功
        // (那样等于绕过"只有发送者能撤回"、还能被当成探测撤回状态的 oracle)。
        MessageStore messageStore = mock(MessageStore.class);
        RecipientResolver recipientResolver = mock(RecipientResolver.class);
        UpdateLogService updateLogService = mock(UpdateLogService.class);
        when(messageStore.findById(CHAT_ID, MESSAGE_ID)).thenReturn(newMessage(System.currentTimeMillis(), true));

        MessageRecallService service = new MessageRecallService(messageStore, recipientResolver, updateLogService);

        assertThatThrownBy(() -> service.recall(CHAT_ID, OTHER_USER, MESSAGE_ID))
                .isInstanceOf(BizException.class)
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.MESSAGE_RECALL_NOT_OWNER));
    }
}
