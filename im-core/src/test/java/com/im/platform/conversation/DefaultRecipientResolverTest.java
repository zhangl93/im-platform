package com.im.platform.conversation;

import com.im.platform.biz.domain.group.Group;
import com.im.platform.biz.domain.group.GroupRepository;
import com.im.platform.conversation.entity.ConversationEntity;
import com.im.platform.conversation.mapper.ConversationMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultRecipientResolverTest {

    private static final long OWNER = 1L;
    private static final long MEMBER = 2L;
    private static final long CHAT_ID = 100L;

    @Test
    void twoArgVersion_looksUpGroupItself() {
        GroupRepository groupRepository = mock(GroupRepository.class);
        ConversationMapper conversationMapper = mock(ConversationMapper.class);
        Group group = Group.create(CHAT_ID, "g", OWNER, System.currentTimeMillis(), "");
        group.addMember(OWNER, MEMBER, System.currentTimeMillis());
        when(groupRepository.findById(CHAT_ID)).thenReturn(Optional.of(group));

        DefaultRecipientResolver resolver = new DefaultRecipientResolver(groupRepository, conversationMapper);
        assertThat(resolver.resolveRecipients(CHAT_ID, OWNER)).containsExactly(MEMBER);
        verify(groupRepository).findById(CHAT_ID);
    }

    @Test
    void threeArgVersion_withPreResolvedGroup_skipsRepositoryLookup() {
        GroupRepository groupRepository = mock(GroupRepository.class);
        ConversationMapper conversationMapper = mock(ConversationMapper.class);
        Group group = Group.create(CHAT_ID, "g", OWNER, System.currentTimeMillis(), "");
        group.addMember(OWNER, MEMBER, System.currentTimeMillis());

        DefaultRecipientResolver resolver = new DefaultRecipientResolver(groupRepository, conversationMapper);
        assertThat(resolver.resolveRecipients(CHAT_ID, OWNER, Optional.of(group))).containsExactly(MEMBER);

        // 调用方已经把 Group 解析好传进来了,这里不该再去查一次数据库。
        verify(groupRepository, never()).findById(CHAT_ID);
    }

    @Test
    void threeArgVersion_withEmptyPreResolvedGroup_fallsBackToConversationLookup() {
        GroupRepository groupRepository = mock(GroupRepository.class);
        ConversationMapper conversationMapper = mock(ConversationMapper.class);
        ConversationEntity conversation = new ConversationEntity();
        conversation.setChatId(CHAT_ID);
        conversation.setUserA(OWNER);
        conversation.setUserB(MEMBER);
        when(conversationMapper.selectById(CHAT_ID)).thenReturn(conversation);

        DefaultRecipientResolver resolver = new DefaultRecipientResolver(groupRepository, conversationMapper);
        assertThat(resolver.resolveRecipients(CHAT_ID, OWNER, Optional.empty())).containsExactly(MEMBER);
        verify(groupRepository, never()).findById(CHAT_ID);
    }
}
