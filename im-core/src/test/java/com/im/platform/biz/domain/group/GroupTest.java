package com.im.platform.biz.domain.group;

import com.im.platform.common.core.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Group 聚合根新加的入群模式 + 禁言不变量,纯领域逻辑,不需要 mock。 */
class GroupTest {

    private static final long OWNER = 1L;
    private static final long ADMIN = 2L;
    private static final long MEMBER = 3L;

    private Group newGroupWithAdminAndMember(long now) {
        Group group = Group.create(100L, "g", OWNER, now, "");
        group.addMember(OWNER, ADMIN, now);
        group.updateMemberRole(OWNER, ADMIN, GroupRole.ADMIN);
        group.addMember(OWNER, MEMBER, now);
        return group;
    }

    @Test
    void selfJoin_openMode_succeeds() {
        long now = System.currentTimeMillis();
        Group group = Group.create(200L, "g", OWNER, now, "");
        group.selfJoin(999L, now);
        assertThat(group.getMembers()).anyMatch(m -> m.getUserId() == 999L);
    }

    @Test
    void selfJoin_approvalMode_rejected() {
        long now = System.currentTimeMillis();
        Group group = Group.create(201L, "g", OWNER, now, "");
        group.updateJoinMode(OWNER, GroupJoinMode.APPROVAL);

        assertThatThrownBy(() -> group.selfJoin(999L, now)).isInstanceOf(BizException.class);
    }

    @Test
    void updateJoinMode_byNonOwner_rejected() {
        long now = System.currentTimeMillis();
        Group group = newGroupWithAdminAndMember(now);
        assertThatThrownBy(() -> group.updateJoinMode(ADMIN, GroupJoinMode.APPROVAL))
                .isInstanceOf(BizException.class);
    }

    @Test
    void groupMuted_blocksMemberButNotOwnerOrAdmin() {
        long now = System.currentTimeMillis();
        Group group = newGroupWithAdminAndMember(now);
        group.setGroupMuted(OWNER, true);

        assertThat(group.isMuted(MEMBER, now)).isTrue();
        assertThat(group.isMuted(ADMIN, now)).isFalse();
        assertThat(group.isMuted(OWNER, now)).isFalse();
    }

    @Test
    void muteMember_expiresAfterMutedUntil() {
        long now = System.currentTimeMillis();
        Group group = newGroupWithAdminAndMember(now);
        group.muteMember(ADMIN, MEMBER, now + 1000);

        assertThat(group.isMuted(MEMBER, now + 500)).isTrue();
        assertThat(group.isMuted(MEMBER, now + 1001)).isFalse();
    }

    @Test
    void muteMember_cannotMuteOwner() {
        long now = System.currentTimeMillis();
        Group group = newGroupWithAdminAndMember(now);
        assertThatThrownBy(() -> group.muteMember(ADMIN, OWNER, now + 1000))
                .isInstanceOf(BizException.class);
    }

    @Test
    void muteMember_byPlainMember_rejected() {
        long now = System.currentTimeMillis();
        Group group = newGroupWithAdminAndMember(now);
        assertThatThrownBy(() -> group.muteMember(MEMBER, ADMIN, now + 1000))
                .isInstanceOf(BizException.class);
    }

    @Test
    void isMuted_untouchedMember_false() {
        long now = System.currentTimeMillis();
        Group group = newGroupWithAdminAndMember(now);
        assertThat(group.isMuted(MEMBER, now)).isFalse();
    }

    @Test
    void isMuted_nonMember_false() {
        long now = System.currentTimeMillis();
        Group group = newGroupWithAdminAndMember(now);
        assertThat(group.isMuted(9999L, now)).isFalse();
    }

    @Test
    void isManager_ownerAndAdminTrue_memberFalse() {
        long now = System.currentTimeMillis();
        Group group = newGroupWithAdminAndMember(now);
        assertThat(group.isManager(OWNER)).isTrue();
        assertThat(group.isManager(ADMIN)).isTrue();
        assertThat(group.isManager(MEMBER)).isFalse();
        assertThat(group.isManager(9999L)).isFalse();
    }
}
