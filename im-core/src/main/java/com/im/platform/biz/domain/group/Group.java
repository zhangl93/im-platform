package com.im.platform.biz.domain.group;

import com.im.platform.common.core.exception.BizException;
import com.im.platform.common.core.exception.ErrorCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Group 聚合根,群相关的业务不变量全部收敛在这里:
 * - 群主转让必须由当前群主发起,且目标必须已是群成员
 * - 加人受成员上限约束
 * - 移除/改角色必须由 OWNER 或 ADMIN 操作,且不能移除/降级 OWNER 本身(需先转让);
 *   自己退群(leaveGroup)不受此权限约束,但同样不能是 OWNER 身份(需先转让)
 * - 入群模式(joinMode)控制 selfJoin 是否直接放行,还是必须走 GroupJoinRequest 审核
 * - 禁言分两层:groupMuted 全员禁言(OWNER/ADMIN 不受影响)、单个成员的 mutedUntil 定点禁言
 *
 * 跨聚合的校验(如目标用户是否拉黑了操作人)不属于 Group 自己的不变量,
 * 由 application 层在调用前查询 UserRepository 后再决定是否放行。
 */
public class Group {

    private final long groupId;
    private String groupName;
    private final GroupPolicy policy;
    private final List<GroupMember> members;
    private GroupJoinMode joinMode;
    private boolean groupMuted;
    /** 扩展字段,业务自定义属性,平台不解析/不校验内容,只负责原样存取。 */
    private String ex;

    public Group(long groupId, String groupName, GroupPolicy policy, List<GroupMember> members, String ex) {
        this(groupId, groupName, policy, members, GroupJoinMode.OPEN, false, ex);
    }

    public Group(long groupId, String groupName, GroupPolicy policy, List<GroupMember> members,
                  GroupJoinMode joinMode, boolean groupMuted, String ex) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.policy = policy == null ? GroupPolicy.DEFAULT : policy;
        this.members = members == null ? new ArrayList<>() : members;
        this.joinMode = joinMode == null ? GroupJoinMode.OPEN : joinMode;
        this.groupMuted = groupMuted;
        this.ex = ex;
    }

    public static Group create(long groupId, String groupName, long ownerId, long now, String ex) {
        Group group = new Group(groupId, groupName, GroupPolicy.DEFAULT, new ArrayList<>(),
                GroupJoinMode.OPEN, false, ex);
        group.members.add(new GroupMember(ownerId, GroupRole.OWNER, now, null));
        return group;
    }

    public void transferOwner(long operatorId, long newOwnerId) {
        GroupMember operator = requireMember(operatorId);
        if (operator.getRole() != GroupRole.OWNER) {
            throw new BizException(ErrorCode.GROUP_OWNER_TRANSFER_INVALID, "operator is not current owner");
        }
        GroupMember newOwner = requireMember(newOwnerId);

        operator.setRole(GroupRole.ADMIN);
        newOwner.setRole(GroupRole.OWNER);
    }

    public void addMember(long operatorId, long targetUserId, long now) {
        requireManager(operatorId);
        addMemberInternal(targetUserId, now);
    }

    /**
     * 用户自己申请入群直接放行(joinMode=OPEN 时)。不校验操作权限——申请人本来就不是成员,
     * 没有权限可言,能不能进只看 joinMode;APPROVAL 模式下不应该调这个方法,
     * 应该走 GroupJoinRequest 流程,由 application 层根据 joinMode 分流。
     */
    public void selfJoin(long userId, long now) {
        if (joinMode != GroupJoinMode.OPEN) {
            throw new BizException(ErrorCode.GROUP_JOIN_NOT_OPEN);
        }
        addMemberInternal(userId, now);
    }

    private void addMemberInternal(long targetUserId, long now) {
        if (findMember(targetUserId).isPresent()) {
            return; // 已是成员,幂等处理
        }
        if (members.size() >= policy.getMaxMemberCount()) {
            throw new BizException(ErrorCode.GROUP_MEMBER_LIMIT_EXCEEDED);
        }
        members.add(new GroupMember(targetUserId, GroupRole.MEMBER, now, null));
    }

    public void removeMember(long operatorId, long targetUserId) {
        requireManager(operatorId);
        removeMemberInternal(targetUserId);
    }

    /**
     * 用户自己退群,不校验操作权限——权限检查是防"别人把我踢出去",自己对自己没有权限门槛。
     * OWNER 依然必须先转让群主(跟被别人移除时同一条规则,不因为是自己走就放松,不然群会变成
     * 没有群主的孤儿状态)。
     */
    public void leaveGroup(long userId) {
        removeMemberInternal(userId);
    }

    private void removeMemberInternal(long targetUserId) {
        GroupMember target = requireMember(targetUserId);
        if (target.getRole() == GroupRole.OWNER) {
            throw new BizException(ErrorCode.GROUP_OWNER_TRANSFER_INVALID, "owner must transfer ownership before leaving");
        }
        members.removeIf(m -> m.getUserId() == targetUserId);
    }

    public void updateMemberRole(long operatorId, long targetUserId, GroupRole newRole) {
        GroupMember operator = requireMember(operatorId);
        if (operator.getRole() != GroupRole.OWNER) {
            throw new BizException(ErrorCode.GROUP_OWNER_TRANSFER_INVALID, "only owner can change member roles");
        }
        if (newRole == GroupRole.OWNER) {
            throw new BizException(ErrorCode.GROUP_OWNER_TRANSFER_INVALID, "use transferOwner to change owner");
        }
        requireMember(targetUserId).setRole(newRole);
    }

    /** 只有群主能改入群模式——管理员是群主授权出来的角色,改群的准入规则这种事不下放给管理员。 */
    public void updateJoinMode(long operatorId, GroupJoinMode newMode) {
        GroupMember operator = requireMember(operatorId);
        if (operator.getRole() != GroupRole.OWNER) {
            throw new BizException(ErrorCode.GROUP_OWNER_TRANSFER_INVALID, "only owner can change join mode");
        }
        this.joinMode = newMode == null ? GroupJoinMode.OPEN : newMode;
    }

    public void setGroupMuted(long operatorId, boolean muted) {
        requireManager(operatorId);
        this.groupMuted = muted;
    }

    /** mutedUntil=0 表示解除禁言。不能禁言 OWNER;ADMIN 之间可以互相禁言,跟 removeMember 的权限边界一致。 */
    public void muteMember(long operatorId, long targetUserId, long mutedUntil) {
        requireManager(operatorId);
        GroupMember target = requireMember(targetUserId);
        if (target.getRole() == GroupRole.OWNER) {
            throw new BizException(ErrorCode.GROUP_OWNER_TRANSFER_INVALID, "cannot mute the owner");
        }
        target.setMutedUntil(mutedUntil);
    }

    /**
     * 供消息发送链路调用:这个用户现在能不能在本群说话。
     * 不是成员的不归这个方法管(发送链路应该在别处校验成员身份),这里只判断"是成员但被禁言"的情况。
     * OWNER/ADMIN 不受全员禁言影响,但仍然可能被单独 mutedUntil 禁言(理论上管理员之间也能互相禁言)。
     */
    public boolean isMuted(long userId, long now) {
        return findMember(userId)
                .map(member -> {
                    if (member.getMutedUntil() > now) {
                        return true;
                    }
                    return groupMuted && member.getRole() == GroupRole.MEMBER;
                })
                .orElse(false);
    }

    private void requireManager(long operatorId) {
        GroupMember operator = requireMember(operatorId);
        if (operator.getRole() == GroupRole.MEMBER) {
            throw new BizException(ErrorCode.GROUP_OWNER_TRANSFER_INVALID, "operator has no management permission");
        }
    }

    private GroupMember requireMember(long userId) {
        return findMember(userId)
                .orElseThrow(() -> new BizException(ErrorCode.GROUP_NOT_FOUND, "user is not a group member: " + userId));
    }

    private Optional<GroupMember> findMember(long userId) {
        return members.stream().filter(m -> m.getUserId() == userId).findFirst();
    }

    public long getGroupId() {
        return groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void rename(String groupName) {
        this.groupName = groupName;
    }

    public String getEx() {
        return ex;
    }

    public void setEx(String ex) {
        this.ex = ex;
    }

    public List<GroupMember> getMembers() {
        return members;
    }

    public int getMaxMemberCount() {
        return policy.getMaxMemberCount();
    }

    public int getMemberCount() {
        return members.size();
    }

    public Optional<Long> getOwnerId() {
        return members.stream().filter(m -> m.getRole() == GroupRole.OWNER).map(GroupMember::getUserId).findFirst();
    }

    /** OWNER/ADMIN 都算管理员;供 application 层做"能不能审批/驳回"这类跨方法复用的权限判断。 */
    public boolean isManager(long userId) {
        return findMember(userId).map(m -> m.getRole() != GroupRole.MEMBER).orElse(false);
    }

    /** 这个用户现在是不是群成员——供只读接口做"只有群成员能看"这类权限判断(比如 GetGroupMembers)。 */
    public boolean isMember(long userId) {
        return findMember(userId).isPresent();
    }

    public GroupJoinMode getJoinMode() {
        return joinMode;
    }

    public boolean isGroupMuted() {
        return groupMuted;
    }
}
