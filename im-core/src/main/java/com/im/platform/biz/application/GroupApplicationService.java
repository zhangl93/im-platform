package com.im.platform.biz.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.im.platform.biz.domain.group.Group;
import com.im.platform.biz.domain.group.GroupJoinMode;
import com.im.platform.biz.domain.group.GroupJoinRequestRecord;
import com.im.platform.biz.domain.group.GroupJoinRequestStatus;
import com.im.platform.biz.domain.group.GroupMember;
import com.im.platform.biz.domain.group.GroupRepository;
import com.im.platform.biz.domain.group.GroupRole;
import com.im.platform.biz.domain.group.JoinGroupResult;
import com.im.platform.biz.domain.user.User;
import com.im.platform.biz.domain.user.UserRepository;
import com.im.platform.biz.infrastructure.persistence.GroupJoinRequestPO;
import com.im.platform.biz.infrastructure.persistence.mapper.GroupJoinRequestMapper;
import com.im.platform.common.core.constant.BizType;
import com.im.platform.common.core.exception.BizException;
import com.im.platform.common.core.exception.ErrorCode;
import com.im.platform.idgen.IdGenClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 群组用例编排。跨聚合校验(如目标用户是否拉黑了操作人)在这里做,
 * 因为它不属于 Group 自身的不变量,而是 Group 与 User 两个聚合之间的关系。
 * 入群申请(GroupJoinRequest)生命周期独立于 Group 聚合,但强依赖 Group 状态
 * (joinMode 决定分流、审批通过要落到 Group 成员列表),放在同一个 service 里编排,
 * 不单独拆服务——不像好友关系那样是完全独立的另一套聚合。
 */
@Service
public class GroupApplicationService {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final GroupJoinRequestMapper groupJoinRequestMapper;
    private final IdGenClient idGenClient;

    public GroupApplicationService(GroupRepository groupRepository, UserRepository userRepository,
                                    GroupJoinRequestMapper groupJoinRequestMapper, IdGenClient idGenClient) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.groupJoinRequestMapper = groupJoinRequestMapper;
        this.idGenClient = idGenClient;
    }

    @Transactional
    public Group createGroup(long ownerId, String groupName, String ex) {
        long groupId = groupRepository.nextGroupId();
        Group group = Group.create(groupId, groupName, ownerId, System.currentTimeMillis(), ex);
        groupRepository.save(group);
        return group;
    }

    @Transactional
    public void transferOwner(long groupId, long operatorId, long newOwnerId) {
        Group group = getGroup(groupId);
        group.transferOwner(operatorId, newOwnerId);
        groupRepository.save(group);
    }

    @Transactional
    public void addMember(long groupId, long operatorId, long targetUserId) {
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
        if (target.hasBlocked(operatorId)) {
            throw new BizException(ErrorCode.USER_BLOCKED, "target user has blocked the operator");
        }

        Group group = getGroup(groupId);
        group.addMember(operatorId, targetUserId, System.currentTimeMillis());
        groupRepository.save(group);
    }

    @Transactional
    public void removeMember(long groupId, long operatorId, long targetUserId) {
        Group group = getGroup(groupId);
        group.removeMember(operatorId, targetUserId);
        groupRepository.save(group);
    }

    /** 用户自己退群,跟 removeMember 的区别是不要求 operator 有管理权限——见 Group.leaveGroup。 */
    @Transactional
    public void leaveGroup(long groupId, long userId) {
        Group group = getGroup(groupId);
        group.leaveGroup(userId);
        groupRepository.save(group);
    }

    /** 这个用户当前所在的全部群,用于客户端"我的群聊"列表。跟 FriendshipApplicationService.getFriends
     * 同样的取舍:先查出 group_id 列表,再逐个加载完整 Group——群数量级远小于消息量级,不需要为了
     * 省这几次查询单独设计一个"轻量群摘要"投影。 */
    public List<Group> getMyGroups(long userId) {
        return groupRepository.findGroupIdsByUserId(userId).stream()
                .map(groupRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    /** 群成员名单(含每个人的角色、禁言状态),只有群成员本人能看——完整名单比 GroupInfo
     * 暴露的 member_count 敏感得多,不能对任意调用方开放(见架构评审发现的授权缺口)。 */
    public List<GroupMember> getGroupMembers(long groupId, long operatorId) {
        Group group = getGroup(groupId);
        if (!group.isMember(operatorId)) {
            throw new BizException(ErrorCode.GROUP_NOT_MEMBER);
        }
        return group.getMembers();
    }

    @Transactional
    public void updateMemberRole(long groupId, long operatorId, long targetUserId, GroupRole newRole) {
        Group group = getGroup(groupId);
        group.updateMemberRole(operatorId, targetUserId, newRole);
        groupRepository.save(group);
    }

    public Group getGroup(long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new BizException(ErrorCode.GROUP_NOT_FOUND));
    }

    /** joinMode=OPEN(或已经是成员)直接进群;APPROVAL 模式下建一条待审批申请,幂等——重复申请返回同一个 request_id。 */
    @Transactional
    public JoinGroupResult requestJoinGroup(long groupId, long userId, String greeting) {
        userRepository.findById(userId).orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
        Group group = getGroup(groupId);
        boolean alreadyMember = group.getMembers().stream().anyMatch(m -> m.getUserId() == userId);
        if (alreadyMember) {
            return new JoinGroupResult(true, 0L);
        }

        if (group.getJoinMode() == GroupJoinMode.OPEN) {
            group.selfJoin(userId, System.currentTimeMillis());
            groupRepository.save(group);
            return new JoinGroupResult(true, 0L);
        }

        GroupJoinRequestPO existingPending = groupJoinRequestMapper.selectOne(new LambdaQueryWrapper<GroupJoinRequestPO>()
                .eq(GroupJoinRequestPO::getGroupId, groupId)
                .eq(GroupJoinRequestPO::getUserId, userId)
                .eq(GroupJoinRequestPO::getStatus, GroupJoinRequestStatus.PENDING.ordinal()));
        if (existingPending != null) {
            return new JoinGroupResult(false, existingPending.getRequestId());
        }

        long requestId = idGenClient.generateId(BizType.GROUP_JOIN_REQUEST_ID);
        GroupJoinRequestPO po = new GroupJoinRequestPO();
        po.setRequestId(requestId);
        po.setGroupId(groupId);
        po.setUserId(userId);
        po.setStatus(GroupJoinRequestStatus.PENDING.ordinal());
        po.setGreeting(greeting);
        po.setCreatedAt(System.currentTimeMillis());
        groupJoinRequestMapper.insert(po);
        return new JoinGroupResult(false, requestId);
    }

    /** 同意走 Group.addMember(天然要求 operator 是 OWNER/ADMIN);拒绝没有落到聚合根上,这里单独校验一次管理权限。 */
    @Transactional
    public void handleJoinRequest(long requestId, long operatorId, boolean accept) {
        GroupJoinRequestPO request = groupJoinRequestMapper.selectById(requestId);
        if (request == null) {
            throw new BizException(ErrorCode.GROUP_JOIN_REQUEST_NOT_FOUND);
        }
        if (request.getStatus() != GroupJoinRequestStatus.PENDING.ordinal()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "join request already handled");
        }

        Group group = getGroup(request.getGroupId());
        long now = System.currentTimeMillis();
        if (accept) {
            group.addMember(operatorId, request.getUserId(), now);
            groupRepository.save(group);
            request.setStatus(GroupJoinRequestStatus.ACCEPTED.ordinal());
        } else {
            if (!group.isManager(operatorId)) {
                throw new BizException(ErrorCode.GROUP_OWNER_TRANSFER_INVALID, "operator has no management permission");
            }
            request.setStatus(GroupJoinRequestStatus.REJECTED.ordinal());
        }
        request.setHandledAt(now);
        request.setHandledBy(operatorId);
        groupJoinRequestMapper.updateById(request);
    }

    public List<GroupJoinRequestRecord> getJoinRequests(long groupId) {
        return groupJoinRequestMapper.selectList(new LambdaQueryWrapper<GroupJoinRequestPO>()
                        .eq(GroupJoinRequestPO::getGroupId, groupId)
                        .orderByDesc(GroupJoinRequestPO::getCreatedAt))
                .stream()
                .map(po -> new GroupJoinRequestRecord(po.getRequestId(), po.getGroupId(), po.getUserId(),
                        po.getGreeting(), GroupJoinRequestStatus.values()[po.getStatus()],
                        po.getCreatedAt(), po.getHandledAt()))
                .collect(Collectors.toList());
    }

    @Transactional
    public void updateJoinMode(long groupId, long operatorId, GroupJoinMode joinMode) {
        Group group = getGroup(groupId);
        group.updateJoinMode(operatorId, joinMode);
        groupRepository.save(group);
    }

    @Transactional
    public void updateGroupMuteAll(long groupId, long operatorId, boolean muted) {
        Group group = getGroup(groupId);
        group.setGroupMuted(operatorId, muted);
        groupRepository.save(group);
    }

    @Transactional
    public void muteMember(long groupId, long operatorId, long targetUserId, long mutedUntil) {
        Group group = getGroup(groupId);
        group.muteMember(operatorId, targetUserId, mutedUntil);
        groupRepository.save(group);
    }
}
