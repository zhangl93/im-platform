package com.im.platform.biz.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 拉黑关系单独建表(user_id, blocked_user_id 联合唯一),不塞进 t_user 大字段,
 * 便于按 blocked_user_id 反查"谁拉黑了我"。
 */
@TableName("t_user_block")
public class UserBlockPO {

    private Long userId;
    private Long blockedUserId;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getBlockedUserId() {
        return blockedUserId;
    }

    public void setBlockedUserId(Long blockedUserId) {
        this.blockedUserId = blockedUserId;
    }
}
