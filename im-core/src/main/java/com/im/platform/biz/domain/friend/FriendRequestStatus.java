package com.im.platform.biz.domain.friend;

/** 顺序必须和 biz.proto 里的 FriendRequestStatus 枚举保持一致,转换时按 ordinal 映射。 */
public enum FriendRequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED
}
