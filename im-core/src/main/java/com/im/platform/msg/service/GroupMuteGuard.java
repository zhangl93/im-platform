package com.im.platform.msg.service;

import com.im.platform.biz.domain.group.GroupRepository;
import com.im.platform.common.core.exception.BizException;
import com.im.platform.common.core.exception.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 消息发送链路里挂的禁言检查。chat_id 是不是群聊、发送者在群里是不是被禁言,
 * 全部委托给 Group 聚合根判断——这里只是个薄适配层,不重复业务规则。
 * 单聊场景下 groupRepository.findById(chatId) 查不到(chat_id 不是任何 group_id),
 * 直接放行,跟 DefaultRecipientResolver 判断单聊/群聊用的是同一个约定(chat_id==group_id)。
 */
@Component
public class GroupMuteGuard {

    private final GroupRepository groupRepository;

    public GroupMuteGuard(GroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }

    public void checkNotMuted(long chatId, long senderId) {
        groupRepository.findById(chatId).ifPresent(group -> {
            if (group.isMuted(senderId, System.currentTimeMillis())) {
                throw new BizException(ErrorCode.GROUP_MEMBER_MUTED);
            }
        });
    }
}
