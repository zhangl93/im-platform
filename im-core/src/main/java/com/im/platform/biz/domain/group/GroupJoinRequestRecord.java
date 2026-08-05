package com.im.platform.biz.domain.group;

/** 入群申请的只读快照,供 application 层组装成 proto 返回,不是聚合根(生命周期独立于 Group 本身)。 */
public record GroupJoinRequestRecord(
        long requestId,
        long groupId,
        long userId,
        String greeting,
        GroupJoinRequestStatus status,
        long createdAt,
        Long handledAt) {
}
