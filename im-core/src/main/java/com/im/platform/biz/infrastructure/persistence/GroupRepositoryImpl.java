package com.im.platform.biz.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.im.platform.biz.domain.group.Group;
import com.im.platform.biz.domain.group.GroupJoinMode;
import com.im.platform.biz.domain.group.GroupMember;
import com.im.platform.biz.domain.group.GroupPolicy;
import com.im.platform.biz.domain.group.GroupRepository;
import com.im.platform.biz.domain.group.GroupRole;
import com.im.platform.biz.infrastructure.persistence.mapper.GroupMapper;
import com.im.platform.biz.infrastructure.persistence.mapper.GroupMemberMapper;
import com.im.platform.common.core.constant.BizType;
import com.im.platform.idgen.IdGenClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class GroupRepositoryImpl implements GroupRepository {

    private final GroupMapper groupMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final IdGenClient idGenClient;

    public GroupRepositoryImpl(GroupMapper groupMapper, GroupMemberMapper groupMemberMapper, IdGenClient idGenClient) {
        this.groupMapper = groupMapper;
        this.groupMemberMapper = groupMemberMapper;
        this.idGenClient = idGenClient;
    }

    @Override
    public Optional<Group> findById(long groupId) {
        GroupPO po = groupMapper.selectById(groupId);
        if (po == null) {
            return Optional.empty();
        }

        List<GroupMember> members = groupMemberMapper.selectList(
                        new LambdaQueryWrapper<GroupMemberPO>().eq(GroupMemberPO::getGroupId, groupId))
                .stream()
                .map(m -> new GroupMember(m.getUserId(), GroupRole.valueOf(m.getRole()), m.getJoinedAt(),
                        m.getMutedUntil() == null ? 0L : m.getMutedUntil(), m.getEx()))
                .collect(Collectors.toList());

        GroupPolicy policy = new GroupPolicy(po.getMaxMemberCount() == null
                ? GroupPolicy.DEFAULT.getMaxMemberCount() : po.getMaxMemberCount());
        GroupJoinMode joinMode = po.getJoinMode() == null
                ? GroupJoinMode.OPEN : GroupJoinMode.values()[po.getJoinMode()];
        boolean groupMuted = Boolean.TRUE.equals(po.getGroupMuted());

        return Optional.of(new Group(po.getGroupId(), po.getGroupName(), policy, members, joinMode, groupMuted, po.getEx()));
    }

    @Override
    public List<Long> findGroupIdsByUserId(long userId) {
        return groupMemberMapper.selectList(
                        new LambdaQueryWrapper<GroupMemberPO>().eq(GroupMemberPO::getUserId, userId))
                .stream()
                .map(GroupMemberPO::getGroupId)
                .collect(Collectors.toList());
    }

    @Override
    public long nextGroupId() {
        return idGenClient.generateId(BizType.GROUP_ID);
    }

    @Override
    public void save(Group group) {
        GroupPO po = new GroupPO();
        po.setGroupId(group.getGroupId());
        po.setGroupName(group.getGroupName());
        po.setMaxMemberCount(group.getMaxMemberCount());
        po.setJoinMode(group.getJoinMode().ordinal());
        po.setGroupMuted(group.isGroupMuted());
        po.setEx(group.getEx());

        if (groupMapper.selectById(group.getGroupId()) == null) {
            groupMapper.insert(po);
        } else {
            groupMapper.updateById(po);
        }

        // 群成员表整体重建,简单可靠;群规模大时可优化为差量更新。
        groupMemberMapper.delete(
                new LambdaQueryWrapper<GroupMemberPO>().eq(GroupMemberPO::getGroupId, group.getGroupId()));
        for (GroupMember member : group.getMembers()) {
            GroupMemberPO memberPO = new GroupMemberPO();
            memberPO.setGroupId(group.getGroupId());
            memberPO.setUserId(member.getUserId());
            memberPO.setRole(member.getRole().name());
            memberPO.setJoinedAt(member.getJoinedAt());
            memberPO.setMutedUntil(member.getMutedUntil());
            memberPO.setEx(member.getEx());
            groupMemberMapper.insert(memberPO);
        }
    }
}
