package com.im.platform.biz.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("t_group")
public class GroupPO {

    @TableId(type = IdType.INPUT)
    private Long groupId;

    private String groupName;
    private Integer maxMemberCount;
    private Integer joinMode; // 0=OPEN, 1=APPROVAL,对应 GroupJoinMode 枚举 ordinal
    private Boolean groupMuted;
    private String ex; // 扩展字段,业务自定义属性,平台不解析

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public Integer getMaxMemberCount() {
        return maxMemberCount;
    }

    public void setMaxMemberCount(Integer maxMemberCount) {
        this.maxMemberCount = maxMemberCount;
    }

    public String getEx() {
        return ex;
    }

    public void setEx(String ex) {
        this.ex = ex;
    }

    public Integer getJoinMode() {
        return joinMode;
    }

    public void setJoinMode(Integer joinMode) {
        this.joinMode = joinMode;
    }

    public Boolean getGroupMuted() {
        return groupMuted;
    }

    public void setGroupMuted(Boolean groupMuted) {
        this.groupMuted = groupMuted;
    }
}
