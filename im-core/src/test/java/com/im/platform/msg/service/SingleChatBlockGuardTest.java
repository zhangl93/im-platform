package com.im.platform.msg.service;

import com.im.platform.biz.domain.group.Group;
import com.im.platform.biz.domain.user.User;
import com.im.platform.biz.domain.user.UserRepository;
import com.im.platform.biz.domain.user.UserStatus;
import com.im.platform.common.core.exception.BizException;
import com.im.platform.conversation.entity.ConversationEntity;
import com.im.platform.conversation.mapper.ConversationMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 消息发送链路的拉黑检查,只对单聊生效,群聊不受影响。是不是群聊由调用方解析好了传进来。 */
class SingleChatBlockGuardTest {

    private static final long SENDER = 1L;
    private static final long PEER = 2L;
    private static final long CHAT_ID = 100L;

    @Test
    void singleChat_peerHasBlockedSender_rejected() {
        ConversationMapper conversationMapper = mock(ConversationMapper.class);
        UserRepository userRepository = mock(UserRepository.class);

        ConversationEntity conversation = new ConversationEntity();
        conversation.setChatId(CHAT_ID);
        conversation.setUserA(SENDER);
        conversation.setUserB(PEER);
        when(conversationMapper.selectById(CHAT_ID)).thenReturn(conversation);

        User peer = new User(PEER, "peer", "", UserStatus.NORMAL, Set.of(SENDER), "");
        when(userRepository.findById(PEER)).thenReturn(Optional.of(peer));

        SingleChatBlockGuard guard = new SingleChatBlockGuard(conversationMapper, userRepository);
        assertThatThrownBy(() -> guard.checkNotBlocked(CHAT_ID, SENDER, Optional.empty())).isInstanceOf(BizException.class);
    }

    @Test
    void singleChat_notBlocked_passes() {
        ConversationMapper conversationMapper = mock(ConversationMapper.class);
        UserRepository userRepository = mock(UserRepository.class);

        ConversationEntity conversation = new ConversationEntity();
        conversation.setChatId(CHAT_ID);
        conversation.setUserA(SENDER);
        conversation.setUserB(PEER);
        when(conversationMapper.selectById(CHAT_ID)).thenReturn(conversation);

        User peer = new User(PEER, "peer", "", UserStatus.NORMAL, Set.of(), "");
        when(userRepository.findById(PEER)).thenReturn(Optional.of(peer));

        SingleChatBlockGuard guard = new SingleChatBlockGuard(conversationMapper, userRepository);
        assertThatCode(() -> guard.checkNotBlocked(CHAT_ID, SENDER, Optional.empty())).doesNotThrowAnyException();
    }

    @Test
    void groupChat_blockCheckSkipped_evenIfSomeMemberHasBlockedSender() {
        ConversationMapper conversationMapper = mock(ConversationMapper.class);
        UserRepository userRepository = mock(UserRepository.class);
        Group group = Group.create(CHAT_ID, "g", SENDER, System.currentTimeMillis(), "");

        SingleChatBlockGuard guard = new SingleChatBlockGuard(conversationMapper, userRepository);
        assertThatCode(() -> guard.checkNotBlocked(CHAT_ID, SENDER, Optional.of(group))).doesNotThrowAnyException();
    }

    @Test
    void unknownChatId_passes() {
        ConversationMapper conversationMapper = mock(ConversationMapper.class);
        UserRepository userRepository = mock(UserRepository.class);
        when(conversationMapper.selectById(CHAT_ID)).thenReturn(null);

        SingleChatBlockGuard guard = new SingleChatBlockGuard(conversationMapper, userRepository);
        assertThatCode(() -> guard.checkNotBlocked(CHAT_ID, SENDER, Optional.empty())).doesNotThrowAnyException();
    }
}
