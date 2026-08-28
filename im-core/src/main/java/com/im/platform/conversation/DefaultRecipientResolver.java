package com.im.platform.conversation;

import com.im.platform.biz.domain.group.Group;
import com.im.platform.biz.domain.group.GroupMember;
import com.im.platform.biz.domain.group.GroupRepository;
import com.im.platform.conversation.entity.ConversationEntity;
import com.im.platform.conversation.mapper.ConversationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 群聊约定 chat_id == group_id,先查 GroupRepository;查不到再当单聊查 t_conversation。
 * 两边都查不到说明 chat_id 是伪造的或者数据不一致,记警告日志、返回空列表,不抛异常
 * ——推送路由失败不应该影响消息已经落库成功这个事实。
 */
@Component
public class DefaultRecipientResolver implements RecipientResolver {

    private static final Logger log = LoggerFactory.getLogger(DefaultRecipientResolver.class);

    private final GroupRepository groupRepository;
    private final ConversationMapper conversationMapper;

    public DefaultRecipientResolver(GroupRepository groupRepository, ConversationMapper conversationMapper) {
        this.groupRepository = groupRepository;
        this.conversationMapper = conversationMapper;
    }

    @Override
    public List<Long> resolveRecipients(long chatId, long senderId) {
        return resolveRecipients(chatId, senderId, groupRepository.findById(chatId));
    }

    @Override
    public List<Long> resolveRecipients(long chatId, long senderId, Optional<Group> preResolvedGroup) {
        if (preResolvedGroup.isPresent()) {
            return preResolvedGroup.get().getMembers().stream()
                    .map(GroupMember::getUserId)
                    .filter(userId -> userId != senderId)
                    .collect(Collectors.toList());
        }

        ConversationEntity conversation = conversationMapper.selectById(chatId);
        if (conversation != null) {
            return List.of(conversation.getUserA(), conversation.getUserB()).stream()
                    .filter(userId -> userId != senderId)
                    .collect(Collectors.toList());
        }

        log.warn("cannot resolve recipients for unknown chat_id={}", chatId);
        return List.of();
    }
}
