package com.im.platform.biz.application;

import com.im.platform.biz.domain.friend.FriendRecord;
import com.im.platform.biz.domain.friend.FriendRequestRecord;
import com.im.platform.biz.domain.friend.FriendRequestStatus;
import com.im.platform.biz.domain.user.User;
import com.im.platform.biz.domain.user.UserRepository;
import com.im.platform.biz.domain.user.UserStatus;
import com.im.platform.biz.infrastructure.persistence.FriendPO;
import com.im.platform.biz.infrastructure.persistence.FriendRequestPO;
import com.im.platform.biz.infrastructure.persistence.mapper.FriendMapper;
import com.im.platform.biz.infrastructure.persistence.mapper.FriendRequestMapper;
import com.im.platform.common.core.exception.BizException;
import com.im.platform.idgen.IdGenClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FriendshipApplicationServiceTest {

    private static User user(long id) {
        return new User(id, "user" + id, "", UserStatus.NORMAL, Set.of(), "");
    }

    @Test
    void sendRequest_toSelf_rejected() {
        FriendRequestMapper requestMapper = mock(FriendRequestMapper.class);
        FriendMapper friendMapper = mock(FriendMapper.class);
        UserRepository userRepository = mock(UserRepository.class);
        IdGenClient idGenClient = mock(IdGenClient.class);
        FriendshipApplicationService service =
                new FriendshipApplicationService(requestMapper, friendMapper, userRepository, idGenClient);

        assertThatThrownBy(() -> service.sendRequest(1001L, 1001L, "hi"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void sendRequest_targetNotFound_rejected() {
        FriendRequestMapper requestMapper = mock(FriendRequestMapper.class);
        FriendMapper friendMapper = mock(FriendMapper.class);
        UserRepository userRepository = mock(UserRepository.class);
        IdGenClient idGenClient = mock(IdGenClient.class);
        when(userRepository.findById(2002L)).thenReturn(Optional.empty());
        FriendshipApplicationService service =
                new FriendshipApplicationService(requestMapper, friendMapper, userRepository, idGenClient);

        assertThatThrownBy(() -> service.sendRequest(1001L, 2002L, "hi"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void sendRequest_targetHasBlockedRequester_rejected() {
        FriendRequestMapper requestMapper = mock(FriendRequestMapper.class);
        FriendMapper friendMapper = mock(FriendMapper.class);
        UserRepository userRepository = mock(UserRepository.class);
        IdGenClient idGenClient = mock(IdGenClient.class);
        User blocker = new User(2002L, "u2", "", UserStatus.NORMAL, Set.of(1001L), "");
        when(userRepository.findById(2002L)).thenReturn(Optional.of(blocker));
        when(userRepository.findById(1001L)).thenReturn(Optional.of(user(1001L)));
        FriendshipApplicationService service =
                new FriendshipApplicationService(requestMapper, friendMapper, userRepository, idGenClient);

        assertThatThrownBy(() -> service.sendRequest(1001L, 2002L, "hi"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void sendRequest_alreadyFriends_rejected() {
        FriendRequestMapper requestMapper = mock(FriendRequestMapper.class);
        FriendMapper friendMapper = mock(FriendMapper.class);
        UserRepository userRepository = mock(UserRepository.class);
        IdGenClient idGenClient = mock(IdGenClient.class);
        when(userRepository.findById(2002L)).thenReturn(Optional.of(user(2002L)));
        when(userRepository.findById(1001L)).thenReturn(Optional.of(user(1001L)));
        when(friendMapper.selectCount(any())).thenReturn(1L);
        FriendshipApplicationService service =
                new FriendshipApplicationService(requestMapper, friendMapper, userRepository, idGenClient);

        assertThatThrownBy(() -> service.sendRequest(1001L, 2002L, "hi"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void sendRequest_noExistingRequest_createsNewPendingRequest() {
        FriendRequestMapper requestMapper = mock(FriendRequestMapper.class);
        FriendMapper friendMapper = mock(FriendMapper.class);
        UserRepository userRepository = mock(UserRepository.class);
        IdGenClient idGenClient = mock(IdGenClient.class);
        when(userRepository.findById(2002L)).thenReturn(Optional.of(user(2002L)));
        when(userRepository.findById(1001L)).thenReturn(Optional.of(user(1001L)));
        when(friendMapper.selectCount(any())).thenReturn(0L);
        when(requestMapper.selectOne(any())).thenReturn(null); // 反向 + 同向都没有待处理申请
        when(idGenClient.generateId("friend_request_id")).thenReturn(9999L);

        FriendshipApplicationService service =
                new FriendshipApplicationService(requestMapper, friendMapper, userRepository, idGenClient);
        long requestId = service.sendRequest(1001L, 2002L, "hi");

        assertThat(requestId).isEqualTo(9999L);
        ArgumentCaptor<FriendRequestPO> captor = ArgumentCaptor.forClass(FriendRequestPO.class);
        verify(requestMapper).insert(captor.capture());
        assertThat(captor.getValue().getFromUserId()).isEqualTo(1001L);
        assertThat(captor.getValue().getToUserId()).isEqualTo(2002L);
        assertThat(captor.getValue().getStatus()).isEqualTo(FriendRequestStatus.PENDING.ordinal());
    }

    @Test
    void sendRequest_existingPendingSameDirection_returnsExistingIdWithoutInsertingAgain() {
        FriendRequestMapper requestMapper = mock(FriendRequestMapper.class);
        FriendMapper friendMapper = mock(FriendMapper.class);
        UserRepository userRepository = mock(UserRepository.class);
        IdGenClient idGenClient = mock(IdGenClient.class);
        when(userRepository.findById(2002L)).thenReturn(Optional.of(user(2002L)));
        when(userRepository.findById(1001L)).thenReturn(Optional.of(user(1001L)));
        when(friendMapper.selectCount(any())).thenReturn(0L);

        FriendRequestPO existing = new FriendRequestPO();
        existing.setRequestId(555L);
        existing.setFromUserId(1001L);
        existing.setToUserId(2002L);
        existing.setStatus(FriendRequestStatus.PENDING.ordinal());
        // 第一次 selectOne 是查反方向(没有),第二次是查同方向(有)
        when(requestMapper.selectOne(any())).thenReturn(null, existing);

        FriendshipApplicationService service =
                new FriendshipApplicationService(requestMapper, friendMapper, userRepository, idGenClient);
        long requestId = service.sendRequest(1001L, 2002L, "hi again");

        assertThat(requestId).isEqualTo(555L);
        verify(requestMapper, never()).insert(any(FriendRequestPO.class));
    }

    @Test
    void sendRequest_reversePendingExists_autoAcceptsAndCreatesBothFriendRows() {
        FriendRequestMapper requestMapper = mock(FriendRequestMapper.class);
        FriendMapper friendMapper = mock(FriendMapper.class);
        UserRepository userRepository = mock(UserRepository.class);
        IdGenClient idGenClient = mock(IdGenClient.class);
        when(userRepository.findById(2002L)).thenReturn(Optional.of(user(2002L)));
        when(userRepository.findById(1001L)).thenReturn(Optional.of(user(1001L)));
        when(friendMapper.selectCount(any())).thenReturn(0L);

        FriendRequestPO reversePending = new FriendRequestPO();
        reversePending.setRequestId(777L);
        reversePending.setFromUserId(2002L);
        reversePending.setToUserId(1001L);
        reversePending.setStatus(FriendRequestStatus.PENDING.ordinal());
        when(requestMapper.selectOne(any())).thenReturn(reversePending);
        when(friendMapper.selectOne(any())).thenReturn(null); // 好友表里还没有对应行

        FriendshipApplicationService service =
                new FriendshipApplicationService(requestMapper, friendMapper, userRepository, idGenClient);
        long requestId = service.sendRequest(1001L, 2002L, "hi");

        assertThat(requestId).isEqualTo(777L);
        verify(friendMapper, times(2)).insert(any(FriendPO.class)); // 双向各插一行
        ArgumentCaptor<FriendRequestPO> captor = ArgumentCaptor.forClass(FriendRequestPO.class);
        verify(requestMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(FriendRequestStatus.ACCEPTED.ordinal());
    }

    @Test
    void handleRequest_accept_createsBothFriendRowsAndMarksAccepted() {
        FriendRequestMapper requestMapper = mock(FriendRequestMapper.class);
        FriendMapper friendMapper = mock(FriendMapper.class);
        UserRepository userRepository = mock(UserRepository.class);
        IdGenClient idGenClient = mock(IdGenClient.class);

        FriendRequestPO pending = new FriendRequestPO();
        pending.setRequestId(100L);
        pending.setFromUserId(1001L);
        pending.setToUserId(2002L);
        pending.setStatus(FriendRequestStatus.PENDING.ordinal());
        when(requestMapper.selectById(100L)).thenReturn(pending);
        when(friendMapper.selectOne(any())).thenReturn(null);

        FriendshipApplicationService service =
                new FriendshipApplicationService(requestMapper, friendMapper, userRepository, idGenClient);
        service.handleRequest(100L, 2002L, true);

        verify(friendMapper, times(2)).insert(any(FriendPO.class));
        ArgumentCaptor<FriendRequestPO> captor = ArgumentCaptor.forClass(FriendRequestPO.class);
        verify(requestMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(FriendRequestStatus.ACCEPTED.ordinal());
        assertThat(captor.getValue().getHandledAt()).isNotNull();
    }

    @Test
    void handleRequest_reject_marksRejectedWithoutCreatingFriendRows() {
        FriendRequestMapper requestMapper = mock(FriendRequestMapper.class);
        FriendMapper friendMapper = mock(FriendMapper.class);
        UserRepository userRepository = mock(UserRepository.class);
        IdGenClient idGenClient = mock(IdGenClient.class);

        FriendRequestPO pending = new FriendRequestPO();
        pending.setRequestId(100L);
        pending.setFromUserId(1001L);
        pending.setToUserId(2002L);
        pending.setStatus(FriendRequestStatus.PENDING.ordinal());
        when(requestMapper.selectById(100L)).thenReturn(pending);

        FriendshipApplicationService service =
                new FriendshipApplicationService(requestMapper, friendMapper, userRepository, idGenClient);
        service.handleRequest(100L, 2002L, false);

        verify(friendMapper, never()).insert(any(FriendPO.class));
        ArgumentCaptor<FriendRequestPO> captor = ArgumentCaptor.forClass(FriendRequestPO.class);
        verify(requestMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(FriendRequestStatus.REJECTED.ordinal());
    }

    @Test
    void handleRequest_wrongOperator_rejected() {
        FriendRequestMapper requestMapper = mock(FriendRequestMapper.class);
        FriendMapper friendMapper = mock(FriendMapper.class);
        UserRepository userRepository = mock(UserRepository.class);
        IdGenClient idGenClient = mock(IdGenClient.class);

        FriendRequestPO pending = new FriendRequestPO();
        pending.setRequestId(100L);
        pending.setFromUserId(1001L);
        pending.setToUserId(2002L);
        pending.setStatus(FriendRequestStatus.PENDING.ordinal());
        when(requestMapper.selectById(100L)).thenReturn(pending);

        FriendshipApplicationService service =
                new FriendshipApplicationService(requestMapper, friendMapper, userRepository, idGenClient);

        assertThatThrownBy(() -> service.handleRequest(100L, 9999L, true))
                .isInstanceOf(BizException.class);
    }

    @Test
    void handleRequest_alreadyHandled_rejected() {
        FriendRequestMapper requestMapper = mock(FriendRequestMapper.class);
        FriendMapper friendMapper = mock(FriendMapper.class);
        UserRepository userRepository = mock(UserRepository.class);
        IdGenClient idGenClient = mock(IdGenClient.class);

        FriendRequestPO handled = new FriendRequestPO();
        handled.setRequestId(100L);
        handled.setFromUserId(1001L);
        handled.setToUserId(2002L);
        handled.setStatus(FriendRequestStatus.ACCEPTED.ordinal());
        when(requestMapper.selectById(100L)).thenReturn(handled);

        FriendshipApplicationService service =
                new FriendshipApplicationService(requestMapper, friendMapper, userRepository, idGenClient);

        assertThatThrownBy(() -> service.handleRequest(100L, 2002L, true))
                .isInstanceOf(BizException.class);
    }

    @Test
    void removeFriend_deletesBothDirections() {
        FriendRequestMapper requestMapper = mock(FriendRequestMapper.class);
        FriendMapper friendMapper = mock(FriendMapper.class);
        UserRepository userRepository = mock(UserRepository.class);
        IdGenClient idGenClient = mock(IdGenClient.class);

        FriendshipApplicationService service =
                new FriendshipApplicationService(requestMapper, friendMapper, userRepository, idGenClient);
        service.removeFriend(1001L, 2002L);

        verify(friendMapper, times(2)).delete(any());
    }

    @Test
    void getFriends_mapsToFriendRecordsWithUserData() {
        FriendRequestMapper requestMapper = mock(FriendRequestMapper.class);
        FriendMapper friendMapper = mock(FriendMapper.class);
        UserRepository userRepository = mock(UserRepository.class);
        IdGenClient idGenClient = mock(IdGenClient.class);

        FriendPO row = new FriendPO();
        row.setUserId(1001L);
        row.setFriendId(2002L);
        row.setRemark("老王");
        row.setCreatedAt(123456789L);
        when(friendMapper.selectList(any())).thenReturn(List.of(row));
        when(userRepository.findById(2002L)).thenReturn(Optional.of(user(2002L)));

        FriendshipApplicationService service =
                new FriendshipApplicationService(requestMapper, friendMapper, userRepository, idGenClient);
        List<FriendRecord> friends = service.getFriends(1001L, "", 20);

        assertThat(friends).hasSize(1);
        assertThat(friends.get(0).user().getUserId()).isEqualTo(2002L);
        assertThat(friends.get(0).remark()).isEqualTo("老王");
        assertThat(friends.get(0).friendSince()).isEqualTo(123456789L);
    }

    @Test
    void getFriendRequests_incomingFiltersOnToUserId() {
        FriendRequestMapper requestMapper = mock(FriendRequestMapper.class);
        FriendMapper friendMapper = mock(FriendMapper.class);
        UserRepository userRepository = mock(UserRepository.class);
        IdGenClient idGenClient = mock(IdGenClient.class);

        FriendRequestPO po = new FriendRequestPO();
        po.setRequestId(1L);
        po.setFromUserId(2002L);
        po.setToUserId(1001L);
        po.setStatus(FriendRequestStatus.PENDING.ordinal());
        po.setGreeting("hi");
        po.setCreatedAt(1L);
        when(requestMapper.selectList(any())).thenReturn(List.of(po));

        FriendshipApplicationService service =
                new FriendshipApplicationService(requestMapper, friendMapper, userRepository, idGenClient);
        List<FriendRequestRecord> requests = service.getFriendRequests(1001L, true);

        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).fromUserId()).isEqualTo(2002L);
        assertThat(requests.get(0).status()).isEqualTo(FriendRequestStatus.PENDING);
    }
}
