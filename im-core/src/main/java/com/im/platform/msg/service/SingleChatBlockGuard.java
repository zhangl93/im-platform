package com.im.platform.msg.service;

import com.im.platform.biz.domain.group.Group;
import com.im.platform.biz.domain.user.UserRepository;
import com.im.platform.common.core.exception.BizException;
import com.im.platform.common.core.exception.ErrorCode;
import com.im.platform.conversation.entity.ConversationEntity;
import com.im.platform.conversation.mapper.ConversationMapper;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 消息发送链路里挂的拉黑检查,只对单聊生效——群消息是群内广播,不因为群里有人拉黑过发送者
 * 就不让发(那样每条群消息都要对全体成员做一次拉黑检查,而且语义上也不对:群成员身份
 * 本身就是双方都认可的,拉黑关系管的是"要不要单独收你的消息"这件事)。
 * 是不是群聊由调用方(MessageWriteService)统一解析一次再传进来,这里不自己查
 * GroupRepository——避免消息发送链路上每个检查各自查一遍"这是不是群聊"。
 * 单聊场景下"另一方"是谁,仍然要查 ConversationMapper——这是群解析覆盖不到的另一份数据。
 */
@Component
public class SingleChatBlockGuard {

    private final ConversationMapper conversationMapper;
    private final UserRepository userRepository;

    public SingleChatBlockGuard(ConversationMapper conversationMapper, UserRepository userRepository) {
        this.conversationMapper = conversationMapper;
        this.userRepository = userRepository;
    }

    public void checkNotBlocked(long chatId, long senderId, Optional<Group> group) {
        if (group.isPresent()) {
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
