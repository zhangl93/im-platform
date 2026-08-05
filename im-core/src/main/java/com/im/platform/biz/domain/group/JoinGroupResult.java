package com.im.platform.biz.domain.group;

/**
 * requestJoinGroup 的结果:OPEN 模式(或已经是成员)直接进群,joinedImmediately=true,
 * requestId 无意义(固定 0);APPROVAL 模式下 joinedImmediately=false,requestId 是新建
 * 或已存在的待处理申请 ID(幂等)。
 */
public record JoinGroupResult(boolean joinedImmediately, long requestId) {
}
