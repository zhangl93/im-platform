package com.im.platform.biz.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;

/** 联合主键 (user_id, friend_id),不用 BaseMapper 的 xxById 系方法。 */
@TableName("t_friend")
public class FriendPO {

    private Long userId;
    private Long friendId;
    private String remark;
    private String ex;
    private Long createdAt;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getFriendId() {
        return friendId;
    }

    public void setFriendId(Long friendId) {
        this.friendId = friendId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getEx() {
        return ex;
    }

    public void setEx(String ex) {
        this.ex = ex;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }
}
