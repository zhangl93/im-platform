package com.im.platform.biz.application;

import com.im.platform.biz.domain.user.User;
import com.im.platform.biz.domain.user.UserRepository;
import com.im.platform.biz.domain.user.UserStatus;
import com.im.platform.core.callback.CallbackInvoker;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ex 扩展字段接入 API 之后的 round-trip 验证:数据库层面的存取早在 task #31 就用真实 MySQL
 * 验证过了,这里补的是新加的这一层——UpdateProfileRequest.ex -&gt; 应用服务 -&gt; User.setEx() -&gt;
 * repository.save() 有没有正确传递,不用真的起 DB,mock repository 拦截 save() 时的入参即可。
 */
class UserApplicationServiceTest {

    @Test
    void updateProfile_setsExOnUser_andPersists() {
        UserRepository userRepository = mock(UserRepository.class);
        CallbackInvoker callbackInvoker = mock(CallbackInvoker.class);
        User existing = new User(1001L, "old-nick", "old-avatar", UserStatus.NORMAL, new HashSet<>(), null);
        when(userRepository.findById(1001L)).thenReturn(Optional.of(existing));

        UserApplicationService service = new UserApplicationService(userRepository, callbackInvoker);
        service.updateProfile(1001L, "new-nick", "new-avatar", "{\"level\":5}");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEx()).isEqualTo("{\"level\":5}");
        assertThat(saved.getNickname()).isEqualTo("new-nick");
        assertThat(saved.getAvatar()).isEqualTo("new-avatar");
    }

    @Test
    void updateProfile_exOverwrittenWithNull_whenCallerPassesNull() {
        UserRepository userRepository = mock(UserRepository.class);
        CallbackInvoker callbackInvoker = mock(CallbackInvoker.class);
        User existing = new User(1001L, "old-nick", "old-avatar", UserStatus.NORMAL, new HashSet<>(), "existing-ex");
        when(userRepository.findById(1001L)).thenReturn(Optional.of(existing));

        UserApplicationService service = new UserApplicationService(userRepository, callbackInvoker);
        service.updateProfile(1001L, "new-nick", "new-avatar", null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        // 跟 nickname/avatar 一样是全量覆盖语义,不是"只在非空时更新"——调用方要自己决定
        // 要不要先读一遍旧值再回填,这里保持跟已有字段一致的行为,不单独给 ex 搞特殊逻辑。
        assertThat(captor.getValue().getEx()).isNull();
    }
}
