package com.im.platform.msg.service;

import com.im.platform.biz.domain.group.Group;
import com.im.platform.common.core.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 群禁言检查——是不是群聊由调用方解析好传进来,这里只判断禁言状态。 */
class GroupMuteGuardTest {

    private static final long OWNER = 1L;
    private static final long MEMBER = 2L;

    @Test
    void singleChat_noGroup_passes() {
        GroupMuteGuard guard = new GroupMuteGuard();
        assertThatCode(() -> guard.checkNotMuted(Optional.empty(), MEMBER)).doesNotThrowAnyException();
    }

    @Test
    void groupChat_memberMuted_rejected() {
        long now = System.currentTimeMillis();
        Group group = Group.create(100L, "g", OWNER, now, "");
        group.addMember(OWNER, MEMBER, now);
        group.setGroupMuted(OWNER, true);

        GroupMuteGuard guard = new GroupMuteGuard();
        assertThatThrownBy(() -> guard.checkNotMuted(Optional.of(group), MEMBER)).isInstanceOf(BizException.class);
    }

    @Test
    void groupChat_notMuted_passes() {
        long now = System.currentTimeMillis();
        Group group = Group.create(101L, "g", OWNER, now, "");
        group.addMember(OWNER, MEMBER, now);

        GroupMuteGuard guard = new GroupMuteGuard();
        assertThatCode(() -> guard.checkNotMuted(Optional.of(group), MEMBER)).doesNotThrowAnyException();
    }
}
