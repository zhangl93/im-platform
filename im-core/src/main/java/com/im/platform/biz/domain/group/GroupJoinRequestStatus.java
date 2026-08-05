package com.im.platform.biz.domain.group;

/** 枚举顺序必须与 biz.proto 里的 GroupJoinRequestStatus 保持一致(ordinal 直接映射)。 */
public enum GroupJoinRequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED
}
