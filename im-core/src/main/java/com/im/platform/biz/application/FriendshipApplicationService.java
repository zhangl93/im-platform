package com.im.platform.biz.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.im.platform.biz.domain.friend.FriendRecord;
import com.im.platform.biz.domain.friend.FriendRequestRecord;
import com.im.platform.biz.domain.friend.FriendRequestStatus;
import com.im.platform.biz.domain.user.User;
import com.im.platform.biz.domain.user.UserRepository;
import com.im.platform.biz.infrastructure.persistence.FriendPO;
import com.im.platform.biz.infrastructure.persistence.FriendRequestPO;
import com.im.platform.biz.infrastructure.persistence.mapper.FriendMapper;
import com.im.platform.biz.infrastructure.persistence.mapper.FriendRequestMapper;
import com.im.platform.common.core.constant.BizType;
import com.im.platform.common.core.exception.BizException;
import com.im.platform.common.core.exception.ErrorCode;
import com.im.platform.idgen.IdGenClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 好友关系用例编排。是否要求"必须是好友才能建单聊会话"这类策略,不在这里做——
 * 那是业务系统的事,通过回调钩子接管,核心不写死这个约束(GetOrCreateSingleChat
 * 现在对任意 user_id 放行,保持不变)。
 */
@Service
public class FriendshipApplicationService {

    private final FriendRequestMapper friendRequestMapper;
    private final FriendMapper friendMapper;
    private final UserRepository userRepository;
    private final IdGenClient idGenClient;

    public FriendshipApplicationService(FriendRequestMapper friendRequestMapper,
                                         FriendMapper friendMapper,
                                         UserRepository userRepository,
                                         IdGenClient idGenClient) {
        this.friendRequestMapper = friendRequestMapper;
        this.friendMapper = friendMapper;
        this.userRepository = userRepository;
        this.idGenClient = idGenClient;
    }

    @Transactional
    public long sendRequest(long fromUserId, long toUserId, String greeting) {
        if (fromUserId == toUserId) {
            throw new BizException(ErrorCode.PARAM_INVALID, "cannot send friend request to self");
        }
        User target = userRepository.findById(toUserId)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
        User requester = userRepository.findById(fromUserId)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
        if (target.hasBlocked(fromUserId) || requester.hasBlocked(toUserId)) {
            throw new BizException(ErrorCode.USER_BLOCKED);
        }
        if (isFriend(fromUserId, toUserId)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "already friends");
        }

        // 对方已经先申请过我了(反方向 PENDING)——直接互相同意,不用再走一遍申请流程,
        // 这是大多数 IM 产品的常见交互(双方都表达过加好友意愿,没必要再等一次确认)。
        FriendRequestPO reversePending = friendRequestMapper.selectOne(new LambdaQueryWrapper<FriendRequestPO>()
                .eq(FriendRequestPO::getFromUserId, toUserId)
                .eq(FriendRequestPO::getToUserId, fromUserId)
                .eq(FriendRequestPO::getStatus, FriendRequestStatus.PENDING.ordinal()));
        if (reversePending != null) {
            acceptInternal(reversePending);
            return reversePending.getRequestId();
        }

        FriendRequestPO existingPending = friendRequestMapper.selectOne(new LambdaQueryWrapper<FriendRequestPO>()
                .eq(FriendRequestPO::getFromUserId, fromUserId)
                .eq(FriendRequestPO::getToUserId, toUserId)
                .eq(FriendRequestPO::getStatus, FriendRequestStatus.PENDING.ordinal()));
        if (existingPending != null) {
            return existingPending.getRequestId(); // 幂等:已经有一条待处理申请,不重复创建
        }

        long requestId = idGenClient.generateId(BizType.FRIEND_REQUEST_ID);
        FriendRequestPO po = new FriendRequestPO();
        po.setRequestId(requestId);
        po.setFromUserId(fromUserId);
        po.setToUserId(toUserId);
        po.setStatus(FriendRequestStatus.PENDING.ordinal());
        po.setGreeting(greeting);
        po.setCreatedAt(System.currentTimeMillis());
        friendRequestMapper.insert(po);
        return requestId;
    }

    @Transactional
    public void handleRequest(long requestId, long operatorUserId, boolean accept) {
        FriendRequestPO request = friendRequestMapper.selectById(requestId);
        if (request == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "friend request not found: " + requestId);
        }
        if (request.getToUserId() != operatorUserId) {
            throw new BizException(ErrorCode.PARAM_INVALID, "only the recipient can handle this request");
        }
        if (request.getStatus() != FriendRequestStatus.PENDING.ordinal()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "request already handled");
        }

        if (accept) {
            acceptInternal(request);
        } else {
            request.setStatus(FriendRequestStatus.REJECTED.ordinal());
            request.setHandledAt(System.currentTimeMillis());
            friendRequestMapper.updateById(request);
        }
    }

    private void acceptInternal(FriendRequestPO request) {
        long now = System.currentTimeMillis();
        insertFriendRowIfAbsent(request.getFromUserId(), request.getToUserId(), now);
        insertFriendRowIfAbsent(request.getToUserId(), request.getFromUserId(), now);
        request.setStatus(FriendRequestStatus.ACCEPTED.ordinal());
        request.setHandledAt(now);
        friendRequestMapper.updateById(request);
    }

    private void insertFriendRowIfAbsent(long userId, long friendId, long now) {
        FriendPO exists = friendMapper.selectOne(new LambdaQueryWrapper<FriendPO>()
                .eq(FriendPO::getUserId, userId).eq(FriendPO::getFriendId, friendId));
        if (exists != null) {
            return;
        }
        FriendPO po = new FriendPO();
        po.setUserId(userId);
        po.setFriendId(friendId);
        po.setCreatedAt(now);
        friendMapper.insert(po);
    }

    @Transactional
    public void removeFriend(long userId, long friendId) {
        friendMapper.delete(new LambdaQueryWrapper<FriendPO>()
                .eq(FriendPO::getUserId, userId).eq(FriendPO::getFriendId, friendId));
        friendMapper.delete(new LambdaQueryWrapper<FriendPO>()
                .eq(FriendPO::getUserId, friendId).eq(FriendPO::getFriendId, userId));
    }

    public boolean isFriend(long userId, long targetId) {
        return friendMapper.selectCount(new LambdaQueryWrapper<FriendPO>()
                .eq(FriendPO::getUserId, userId).eq(FriendPO::getFriendId, targetId)) > 0;
    }

    public List<FriendRequestRecord> getFriendRequests(long userId, boolean incoming) {
        LambdaQueryWrapper<FriendRequestPO> query = new LambdaQueryWrapper<FriendRequestPO>()
                .orderByDesc(FriendRequestPO::getCreatedAt);
        query = incoming ? query.eq(FriendRequestPO::getToUserId, userId)
                          : query.eq(FriendRequestPO::getFromUserId, userId);
        return friendRequestMapper.selectList(query).stream()
                .map(po -> new FriendRequestRecord(po.getRequestId(), po.getFromUserId(), po.getToUserId(),
                        po.getGreeting(), FriendRequestStatus.values()[po.getStatus()],
                        po.getCreatedAt(), po.getHandledAt()))
                .collect(Collectors.toList());
    }

    public List<FriendRecord> getFriends(long userId, String pageToken, int pageSize) {
        long afterFriendId = 0L;
        if (pageToken != null && !pageToken.isEmpty()) {
            try {
                afterFriendId = Long.parseLong(pageToken);
            } catch (NumberFormatException ignored) {
                // 游标解析失败当成"从头开始",不因为一个坏 token 直接报错
            }
        }
        int limit = pageSize > 0 ? pageSize : 20;

        List<FriendPO> rows = friendMapper.selectList(new LambdaQueryWrapper<FriendPO>()
                .eq(FriendPO::getUserId, userId)
                .gt(FriendPO::getFriendId, afterFriendId)
                .orderByAsc(FriendPO::getFriendId)
                .last("limit " + limit));

        return rows.stream()
                .map(row -> userRepository.findById(row.getFriendId())
                        .map(u -> new FriendRecord(u, row.getRemark(), row.getCreatedAt()))
                        .orElse(null))
                .filter(record -> record != null) // 好友用户被注销之类的边缘情况,跳过而不是抛异常
                .collect(Collectors.toList());
    }
}
