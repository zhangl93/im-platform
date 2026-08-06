package com.im.platform.msg.service;

import com.im.platform.biz.domain.group.GroupRepository;
import com.im.platform.biz.domain.user.UserRepository;
import com.im.platform.common.core.exception.BizException;
import com.im.platform.common.core.exception.ErrorCode;
import com.im.platform.conversation.entity.ConversationEntity;
import com.im.platform.conversation.mapper.ConversationMapper;
import org.springframework.stereotype.Component;

/**
 * 消息发送链路里挂的拉黑检查,只对单聊生效——群消息是群内广播,不因为群里有人拉黑过发送者
 * 就不让发(那样每条群消息都要对全体成员做一次拉黑检查,而且语义上也不对:群成员身份
 * 本身就是双方都认可的,拉黑关系管的是"要不要单独收你的消息"这件事)。
 * chat_id 是不是群聊、单聊场景下"另一方"是谁,判断逻辑跟 GroupMuteGuard/DefaultRecipientResolver
 * 用的是同一个约定(chat_id==group_id 判断群聊),这里只是另开一条独立检查,不复用它们的返回值
 * (各自关心的失败语义不同,合并到一起会让任何一边改动都要小心影响另一边)。
 */
@Component
public class SingleChatBlockGuard {

    private final GroupRepository groupRepository;
    private final ConversationMapper conversationMapper;
    private final UserRepository userRepository;

    public SingleChatBlockGuard(GroupRepository groupRepository, ConversationMapper conversationMapper,
                                 UserRepository userRepository) {
        this.groupRepository = groupRepository;
        this.conversationMapper = conversationMapper;
        this.userRepository = userRepository;
    }

    public void checkNotBlocked(long chatId, long senderId) {
        if (groupRepository.findById(chatId).isPresent()) {
            return;
        }

        ConversationEntity conversation = conversationMapper.selectById(chatId);
        if (conversation == null) {
            return;
        }
        long peerId = conversation.getUserA() == senderId ? conversation.getUserB() : conversation.getUserA();

        userRepository.findById(peerId).ifPresent(peer -> {
            if (peer.hasBlocked(senderId)) {
                throw new BizException(ErrorCode.USER_BLOCKED, "recipient has blocked the sender");
            }
        });
    }
}
