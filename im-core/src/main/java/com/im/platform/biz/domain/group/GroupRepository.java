package com.im.platform.biz.domain.group;

import java.util.List;
import java.util.Optional;

public interface GroupRepository {

    Optional<Group> findById(long groupId);

    /** 这个用户当前是成员的全部 group_id,不含已退出/被移除的群。 */
    List<Long> findGroupIdsByUserId(long userId);

    /** 批量加载多个群的完整数据(含各自成员列表),固定 2 次查询(群 PO + 全部成员 PO 各一次
     * IN 查询),不随 groupIds 的数量线性增长——供 GetMyGroups 这类"先拿到一批 group_id,
     * 再要每个群完整数据"的场景用,避免对每个 id 各调一次 findById 造成 N+1。
     * groupIds 为空时直接返回空列表,不发起查询。返回顺序不保证跟入参一致。 */
    List<Group> findAllByGroupIds(List<Long> groupIds);

    long nextGroupId();

    void save(Group group);
}
