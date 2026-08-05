package com.im.platform.session.auth;

import com.im.platform.common.core.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CredentialAuthenticatorTest {

    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOperations;
    // 内存代替真实 Redis:key -> value,足够验证 issue 写的东西 authenticate 能读到
    private final Map<String, String> fakeRedis = new HashMap<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        fakeRedis.clear();
        when(valueOperations.get(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> fakeRedis.get(invocation.getArgument(0, String.class)));
        org.mockito.Mockito.doAnswer(invocation -> {
            fakeRedis.put(invocation.getArgument(0, String.class), invocation.getArgument(1, String.class));
            return null;
        }).when(valueOperations).set(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(TimeUnit.class));
    }

    @Test
    void unconfigured_fallsBackToParsingRawUserId() {
        CredentialAuthenticator authenticator = new CredentialAuthenticator("", stringRedisTemplate);

        long userId = authenticator.authenticate("device-1", "1001".getBytes(StandardCharsets.UTF_8), "1.0");

        assertThat(userId).isEqualTo(1001L);
    }

    @Test
    void unconfigured_nonNumericCredential_rejected() {
        CredentialAuthenticator authenticator = new CredentialAuthenticator("", stringRedisTemplate);

        assertThatThrownBy(() -> authenticator.authenticate("device-1", "not-a-number".getBytes(StandardCharsets.UTF_8), "1.0"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void configured_issueThenAuthenticate_roundTrips() {
        CredentialAuthenticator authenticator = new CredentialAuthenticator("app1:secret1", stringRedisTemplate);

        CredentialAuthenticator.IssuedCredential issued = authenticator.issue("app1", "secret1", 2002L, 3600);
        long userId = authenticator.authenticate("device-1", issued.credential(), "1.0");

        assertThat(userId).isEqualTo(2002L);
    }

    @Test
    void configured_wrongAppSecret_rejected() {
        CredentialAuthenticator authenticator = new CredentialAuthenticator("app1:secret1", stringRedisTemplate);

        assertThatThrownBy(() -> authenticator.issue("app1", "wrong-secret", 2002L, 3600))
                .isInstanceOf(BizException.class);
    }

    @Test
    void configured_unknownAppKey_rejected() {
        CredentialAuthenticator authenticator = new CredentialAuthenticator("app1:secret1", stringRedisTemplate);

        assertThatThrownBy(() -> authenticator.issue("unknown-app", "secret1", 2002L, 3600))
                .isInstanceOf(BizException.class);
    }

    @Test
    void configured_unknownCredential_rejected() {
        CredentialAuthenticator authenticator = new CredentialAuthenticator("app1:secret1", stringRedisTemplate);

        assertThatThrownBy(() -> authenticator.authenticate("device-1", "never-issued".getBytes(StandardCharsets.UTF_8), "1.0"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void configured_rawNumericCredential_noLongerAccepted() {
        // 一旦配了 app-credentials,老的"直接解析数字"这条路必须彻底关掉,不能两条路都通——
        // 否则真实鉴权形同虚设,谁都能拿一个数字字符串直接登录任意 user_id。
        CredentialAuthenticator authenticator = new CredentialAuthenticator("app1:secret1", stringRedisTemplate);

        assertThatThrownBy(() -> authenticator.authenticate("device-1", "1001".getBytes(StandardCharsets.UTF_8), "1.0"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void multipleAppCredentials_parsedCorrectly() {
        CredentialAuthenticator authenticator = new CredentialAuthenticator("app1:secret1,app2:secret2", stringRedisTemplate);

        CredentialAuthenticator.IssuedCredential issuedForApp2 = authenticator.issue("app2", "secret2", 3003L, 3600);
        long userId = authenticator.authenticate("device-1", issuedForApp2.credential(), "1.0");

        assertThat(userId).isEqualTo(3003L);
    }
}
