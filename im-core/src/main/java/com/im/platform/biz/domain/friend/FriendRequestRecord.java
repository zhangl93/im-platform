package com.im.platform.biz.domain.friend;

public record FriendRequestRecord(long requestId, long fromUserId, long toUserId,
                                   String greeting, FriendRequestStatus status,
                                   long createdAt, Long handledAt) {
}
