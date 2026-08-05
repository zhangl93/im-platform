package com.im.platform.biz.domain.group;

import java.util.Optional;

public interface GroupRepository {

    Optional<Group> findById(long groupId);

    long nextGroupId();

    void save(Group group);
}
