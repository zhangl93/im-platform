package com.im.platform.biz.domain.group;

/**
 * Group 聚合内的实体(非独立聚合根),生命周期完全由 Group 管理。
 */
public class GroupMember {

    private final long userId;
    private GroupRole role;
    private final long joinedAt;
    /** 禁言到该时间点(毫秒时间戳),0 表示未禁言。到期后自动失效,靠比较时间戳判断,不需要定时任务清理。 */
    private long mutedUntil;
    /** 扩展字段(成员备注名、自定义头衔等),业务自定义,平台不解析。 */
    private String ex;

    public GroupMember(long userId, GroupRole role, long joinedAt, String ex) {
        this(userId, role, joinedAt, 0L, ex);
    }

    public GroupMember(long userId, GroupRole role, long joinedAt, long mutedUntil, String ex) {
        this.userId = userId;
        this.role = role;
        this.joinedAt = joinedAt;
        this.mutedUntil = mutedUntil;
        this.ex = ex;
    }

    public long getUserId() {
        return userId;
    }

    public GroupRole getRole() {
        return role;
    }

    void setRole(GroupRole role) {
        this.role = role;
    }

    public long getJoinedAt() {
        return joinedAt;
    }

    public long getMutedUntil() {
        return mutedUntil;
    }

    void setMutedUntil(long mutedUntil) {
        this.mutedUntil = mutedUntil;
    }

    public String getEx() {
        return ex;
    }

    public void setEx(String ex) {
        this.ex = ex;
    }
}
