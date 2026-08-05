package com.im.platform.biz.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("t_group_member")
public class GroupMemberPO {

    private Long groupId;
    private Long userId;
    private String role; // OWNER / ADMIN / MEMBER
    private Long joinedAt;
    private Long mutedUntil; // 禁言到该时间点(毫秒时间戳),0=未禁言
    private String ex; // 扩展字段(成员备注名、自定义头衔等),业务自定义,平台不解析

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Long getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Long joinedAt) {
        this.joinedAt = joinedAt;
    }

    public String getEx() {
        return ex;
    }

    public void setEx(String ex) {
        this.ex = ex;
    }

    public Long getMutedUntil() {
        return mutedUntil;
    }

    public void setMutedUntil(Long mutedUntil) {
        this.mutedUntil = mutedUntil;
    }
}
