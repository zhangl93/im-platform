package com.im.platform.biz.domain.group;

import java.util.List;
import java.util.Optional;

public interface GroupRepository {

    Optional<Group> findById(long groupId);

    /** 这个用户当前是成员的全部 group_id,不含已退出/被移除的群。 */
    List<Long> findGroupIdsByUserId(long userId);

    long nextGroupId();

    void save(Group group);
}
