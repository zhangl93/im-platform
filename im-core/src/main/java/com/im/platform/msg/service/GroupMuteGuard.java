package com.im.platform.msg.service;

import com.im.platform.biz.domain.group.Group;
import com.im.platform.common.core.exception.BizException;
import com.im.platform.common.core.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 消息发送链路里挂的禁言检查。发送者在群里是不是被禁言,委托给 Group 聚合根判断——这里
 * 只是个薄适配层,不重复业务规则。是不是群聊、chat_id 对应哪个 Group,由调用方
 * (MessageWriteService)统一解析一次再传进来,这里不自己查 GroupRepository——避免消息
 * 发送链路上每个检查各自查一遍"这是不是群聊",见 MessageWriteService.send 的调用方式。
 * 单聊场景下 group 是空的,直接放行。
 */
@Component
public class GroupMuteGuard {

    public void checkNotMuted(Optional<Group> group, long senderId) {
        group.ifPresent(g -> {
            if (g.isMuted(senderId, System.currentTimeMillis())) {
                throw new BizException(ErrorCode.GROUP_MEMBER_MUTED);
            }
        });
    }
}
