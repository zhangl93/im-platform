package com.im.platform.biz.domain.group;

/**
 * 群组业务规则的策略封装(成员上限等),独立出来是为了后续按群类型(普通群/超大群/频道)
 * 做差异化配置时,不用改 Group 聚合根本身的逻辑。
 */
public class GroupPolicy {

    public static final GroupPolicy DEFAULT = new GroupPolicy(500);

    private final int maxMemberCount;

    public GroupPolicy(int maxMemberCount) {
        this.maxMemberCount = maxMemberCount;
    }

    public int getMaxMemberCount() {
        return maxMemberCount;
    }
}
