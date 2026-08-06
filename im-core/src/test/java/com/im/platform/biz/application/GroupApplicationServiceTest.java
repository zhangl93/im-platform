package com.im.platform.biz.application;

import com.im.platform.biz.domain.group.Group;
import com.im.platform.biz.domain.group.GroupRepository;
import com.im.platform.biz.domain.user.UserRepository;
import com.im.platform.biz.infrastructure.persistence.GroupJoinRequestPO;
import com.im.platform.biz.infrastructure.persistence.mapper.GroupJoinRequestMapper;
import com.im.platform.common.core.exception.BizException;
import com.im.platform.biz.domain.group.JoinGroupResult;
import com.im.platform.idgen.IdGenClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** createGroup 新加的 ex 参数有没有真的传到 Group 聚合根、再到 repository.save()。 */
class GroupApplicationServiceTest {

    @Test
    void createGroup_setsExOnGroup_andPersists() {
        GroupRepository groupRepository = mock(GroupRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        GroupJoinRequestMapper groupJoinRequestMapper = mock(GroupJoinRequestMapper.class);
        IdGenClient idGenClient = mock(IdGenClient.class);
        when(groupRepository.nextGroupId()).thenReturn(555L);

        GroupApplicationService service = new GroupApplicationService(
                groupRepository, userRepository, groupJoinRequestMapper, idGenClient);
        Group created = service.createGroup(1001L, "my group", "{\"category\":\"work\"}");

        assertThat(created.getEx()).isEqualTo("{\"category\":\"work\"}");
        assertThat(created.getGroupId()).isEqualTo(555L);

        ArgumentCaptor<Group> captor = ArgumentCaptor.forClass(Group.class);
        verify(groupRepository).save(captor.capture());
        assertThat(captor.getValue().getEx()).isEqualTo("{\"category\":\"work\"}");
    }

    @Test
    void createGroup_emptyEx_persistsAsGiven() {
        GroupRepository groupRepository = mock(GroupRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        GroupJoinRequestMapper groupJoinRequestMapper = mock(GroupJoinRequestMapper.class);
        IdGenClient idGenClient = mock(IdGenClient.class);
        when(groupRepository.nextGroupId()).thenReturn(556L);

        GroupApplicationService service = new GroupApplicationService(
                groupRepository, userRepository, groupJoinRequestMapper, idGenClient);
        Group created = service.createGroup(1001L, "my group", "");

        assertThat(created.getEx()).isEmpty();
    }

    @Test
    void requestJoinGroup_openMode_joinsImmediately_noRequestRow() {
        GroupRepository groupRepository = mock(GroupRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        GroupJoinRequestMapper groupJoinRequestMapper = mock(GroupJoinRequestMapper.class);
        IdGenClient idGenClient = mock(IdGenClient.class);
        Group group = Group.create(700L, "open group", 9001L, System.currentTimeMillis(), "");
        when(groupRepository.findById(700L)).thenReturn(Optional.of(group));
        when(userRepository.findById(9002L)).thenReturn(Optional.of(
                new com.im.platform.biz.domain.user.User(9002L, "u", "", com.im.platform.biz.domain.user.UserStatus.NORMAL, java.util.Set.of(), "")));

        GroupApplicationService service = new GroupApplicationService(
                groupRepository, userRepository, groupJoinRequestMapper, idGenClient);
        JoinGroupResult result = service.requestJoinGroup(700L, 9002L, "hi");

        assertThat(result.joinedImmediately()).isTrue();
        assertThat(group.getMembers()).anyMatch(m -> m.getUserId() == 9002L);
        verify(groupJoinRequestMapper, never()).insert(any(GroupJoinRequestPO.class));
        verify(groupRepository).save(group);
    }

    @Test
    void requestJoinGroup_approvalMode_createsPendingRequest_idempotent() {
        GroupRepository groupRepository = mock(GroupRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        GroupJoinRequestMapper groupJoinRequestMapper = mock(GroupJoinRequestMapper.class);
        IdGenClient idGenClient = mock(IdGenClient.class);
        Group group = Group.create(701L, "approval group", 9001L, System.currentTimeMillis(), "");
        group.updateJoinMode(9001L, com.im.platform.biz.domain.group.GroupJoinMode.APPROVAL);
        when(groupRepository.findById(701L)).thenReturn(Optional.of(group));
        when(userRepository.findById(9003L)).thenReturn(Optional.of(
                new com.im.platform.biz.domain.user.User(9003L, "u", "", com.im.platform.biz.domain.user.UserStatus.NORMAL, java.util.Set.of(), "")));
        when(idGenClient.generateId(com.im.platform.common.core.constant.BizType.GROUP_JOIN_REQUEST_ID)).thenReturn(8001L);

        GroupApplicationService service = new GroupApplicationService(
                groupRepository, userRepository, groupJoinRequestMapper, idGenClient);
        JoinGroupResult first = service.requestJoinGroup(701L, 9003L, "let me in");

        assertThat(first.joinedImmediately()).isFalse();
        assertThat(first.requestId()).isEqualTo(8001L);
        assertThat(group.getMembers()).noneMatch(m -> m.getUserId() == 9003L);

        GroupJoinRequestPO existingPending = new GroupJoinRequestPO();
        existingPending.setRequestId(8001L);
        when(groupJoinRequestMapper.selectOne(any())).thenReturn(existingPending);

        JoinGroupResult second = service.requestJoinGroup(701L, 9003L, "let me in again");
        assertThat(second.requestId()).isEqualTo(8001L);
        verify(groupJoinRequestMapper, times(1)).insert(any(GroupJoinRequestPO.class));
    }

    @Test
    void handleJoinRequest_accept_addsMemberAndMarksAccepted() {
        GroupRepository groupRepository = mock(GroupRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        GroupJoinRequestMapper groupJoinRequestMapper = mock(GroupJoinRequestMapper.class);
        IdGenClient idGenClient = mock(IdGenClient.class);
        Group group = Group.create(702L, "g", 9001L, System.currentTimeMillis(), "");
        when(groupRepository.findById(702L)).thenReturn(Optional.of(group));

        GroupJoinRequestPO request = new GroupJoinRequestPO();
        request.setRequestId(8002L);
        request.setGroupId(702L);
        request.setUserId(9004L);
        request.setStatus(0); // PENDING
        when(groupJoinRequestMapper.selectById(8002L)).thenReturn(request);

        GroupApplicationService service = new GroupApplicationService(
                groupRepository, userRepository, groupJoinRequestMapper, idGenClient);
        service.handleJoinRequest(8002L, 9001L, true);

        assertThat(group.getMembers()).anyMatch(m -> m.getUserId() == 9004L);
        assertThat(request.getStatus()).isEqualTo(1); // ACCEPTED
        verify(groupJoinRequestMapper).updateById(request);
    }

    @Test
    void handleJoinRequest_rejectByNonManager_throws() {
        GroupRepository groupRepository = mock(GroupRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        GroupJoinRequestMapper groupJoinRequestMapper = mock(GroupJoinRequestMapper.class);
        IdGenClient idGenClient = mock(IdGenClient.class);
        Group group = Group.create(703L, "g", 9001L, System.currentTimeMillis(), "");
        when(groupRepository.findById(703L)).thenReturn(Optional.of(group));

        GroupJoinRequestPO request = new GroupJoinRequestPO();
        request.setRequestId(8003L);
        request.setGroupId(703L);
        request.setUserId(9005L);
        request.setStatus(0);
        when(groupJoinRequestMapper.selectById(8003L)).thenReturn(request);

        GroupApplicationService service = new GroupApplicationService(
                groupRepository, userRepository, groupJoinRequestMapper, idGenClient);

        assertThatThrownBy(() -> service.handleJoinRequest(8003L, 9999L, false))
                .isInstanceOf(BizException.class);
        verify(groupJoinRequestMapper, never()).updateById(any(GroupJoinRequestPO.class));
    }

    @Test
    void muteMember_thenIsMuted_true_untilExpiry() {
        GroupRepository groupRepository = mock(GroupRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        GroupJoinRequestMapper groupJoinRequestMapper = mock(GroupJoinRequestMapper.class);
        IdGenClient idGenClient = mock(IdGenClient.class);
        long now = System.currentTimeMillis();
        Group group = Group.create(704L, "g", 9001L, now, "");
        group.addMember(9001L, 9006L, now);
        when(groupRepository.findById(704L)).thenReturn(Optional.of(group));

        GroupApplicationService service = new GroupApplicationService(
                groupRepository, userRepository, groupJoinRequestMapper, idGenClient);
        service.muteMember(704L, 9001L, 9006L, now + 60_000);

        assertThat(group.isMuted(9006L, now + 30_000)).isTrue();
        assertThat(group.isMuted(9006L, now + 60_001)).isFalse();
    }

    @Test
    void leaveGroup_delegatesToDomainAndPersists() {
        GroupRepository groupRepository = mock(GroupRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        GroupJoinRequestMapper groupJoinRequestMapper = mock(GroupJoinRequestMapper.class);
        IdGenClient idGenClient = mock(IdGenClient.class);
        long now = System.currentTimeMillis();
        Group group = Group.create(705L, "g", 9001L, now, "");
        group.addMember(9001L, 9007L, now);
        when(groupRepository.findById(705L)).thenReturn(Optional.of(group));

        GroupApplicationService service = new GroupApplicationService(
                groupRepository, userRepository, groupJoinRequestMapper, idGenClient);
        service.leaveGroup(705L, 9007L);

        assertThat(group.getMembers()).noneMatch(m -> m.getUserId() == 9007L);
        verify(groupRepository).save(group);
    }

    @Test
    void leaveGroup_ownerRejected_notPersisted() {
        GroupRepository groupRepository = mock(GroupRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        GroupJoinRequestMapper groupJoinRequestMapper = mock(GroupJoinRequestMapper.class);
        IdGenClient idGenClient = mock(IdGenClient.class);
        long now = System.currentTimeMillis();
        Group group = Group.create(706L, "g", 9001L, now, "");
        when(groupRepository.findById(706L)).thenReturn(Optional.of(group));

        GroupApplicationService service = new GroupApplicationService(
                groupRepository, userRepository, groupJoinRequestMapper, idGenClient);

        assertThatThrownBy(() -> service.leaveGroup(706L, 9001L)).isInstanceOf(BizException.class);
        verify(groupRepository, never()).save(any(Group.class));
    }

    @Test
    void getMyGroups_returnsAllGroupsUserBelongsTo() {
        GroupRepository groupRepository = mock(GroupRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        GroupJoinRequestMapper groupJoinRequestMapper = mock(GroupJoinRequestMapper.class);
        IdGenClient idGenClient = mock(IdGenClient.class);
        long now = System.currentTimeMillis();
        Group groupA = Group.create(707L, "a", 9001L, now, "");
        Group groupB = Group.create(708L, "b", 9002L, now, "");
        when(groupRepository.findGroupIdsByUserId(9008L)).thenReturn(java.util.List.of(707L, 708L));
        when(groupRepository.findById(707L)).thenReturn(Optional.of(groupA));
        when(groupRepository.findById(708L)).thenReturn(Optional.of(groupB));

        GroupApplicationService service = new GroupApplicationService(
                groupRepository, userRepository, groupJoinRequestMapper, idGenClient);
        java.util.List<Group> result = service.getMyGroups(9008L);

        assertThat(result).containsExactlyInAnyOrder(groupA, groupB);
    }

    @Test
    void getMyGroups_skipsGroupIdsThatNoLongerResolve() {
        GroupRepository groupRepository = mock(GroupRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        GroupJoinRequestMapper groupJoinRequestMapper = mock(GroupJoinRequestMapper.class);
        IdGenClient idGenClient = mock(IdGenClient.class);
        long now = System.currentTimeMillis();
        Group groupA = Group.create(709L, "a", 9001L, now, "");
        when(groupRepository.findGroupIdsByUserId(9009L)).thenReturn(java.util.List.of(709L, 710L));
        when(groupRepository.findById(709L)).thenReturn(Optional.of(groupA));
        when(groupRepository.findById(710L)).thenReturn(Optional.empty());

        GroupApplicationService service = new GroupApplicationService(
                groupRepository, userRepository, groupJoinRequestMapper, idGenClient);
        java.util.List<Group> result = service.getMyGroups(9009L);

        assertThat(result).containsExactly(groupA);
    }
}
