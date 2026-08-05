package com.im.platform.session.auth;

import com.im.platform.common.core.exception.BizException;
import com.im.platform.common.core.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 校验 AuthRequest.encrypted_credential,通过后返回 user_id。
 *
 * 凭证从哪来:业务后端持有 app_key/app_secret,通过 im-sdk 直连 im-core 调
 * {@link #issue}(对应 SessionService.IssueUserCredential),拿到一个不透明的登录凭证,
 * 下发给自己的客户端。凭证本身是随机串 + Redis 存的 {user_id, 过期时间},不是自包含签名的
 * token(不用 JWT)——校验就是查一下这个进程/集群共享的 Redis,足够了,不需要额外的签名库,
 * 吊销也只是删一个 Redis key。参考 OpenIM 的 GetUserToken:业务后端换 token 这一步必须
 * 走服务端(而不是业务后端自己拿密钥离线签),这样服务端才能追踪每个凭证、随时吊销。
 *
 * im.session.app-credentials 没配置(本地开发/现有测试工具)时退回最初的行为:直接把
 * encrypted_credential 当十进制 user_id 解析,不做真实校验——跟 gateway 那边 app_key
 * 白名单"留空 = 不启用"是同一个约定,不能因为加了这层校验就让本地开发/已有脚本全部失效。
 */
@Component
public class CredentialAuthenticator {

    private static final String CREDENTIAL_KEY_PREFIX = "im:usercred:";
    private static final long DEFAULT_EXPIRE_SECONDS = TimeUnit.DAYS.toSeconds(7);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final Map<String, String> appSecrets;
    private final StringRedisTemplate stringRedisTemplate;

    public CredentialAuthenticator(@Value("${im.session.app-credentials:}") String appCredentialsCsv,
                                    StringRedisTemplate stringRedisTemplate) {
        this.appSecrets = Arrays.stream(appCredentialsCsv.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .map(entry -> entry.split(":", 2))
                .filter(parts -> parts.length == 2)
                .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1]));
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long authenticate(String deviceId, byte[] encryptedCredential, String clientVersion) {
        if (appSecrets.isEmpty()) {
            return parseRawUserId(encryptedCredential);
        }

        String credential = new String(encryptedCredential, StandardCharsets.UTF_8);
        String userIdValue = stringRedisTemplate.opsForValue().get(CREDENTIAL_KEY_PREFIX + credential);
        if (userIdValue == null) {
            throw new BizException(ErrorCode.AUTH_FAILED, "invalid or expired credential");
        }
        return Long.parseLong(userIdValue);
    }

    /** 业务后端用 app_key/app_secret 换一个登录凭证。appSecret 对不上直接拒绝,不透出"是哪里错了"。 */
    public IssuedCredential issue(String appKey, String appSecret, long userId, long expireSeconds) {
        String expected = appSecrets.get(appKey);
        if (expected == null || !expected.equals(appSecret)) {
            throw new BizException(ErrorCode.AUTH_FAILED, "invalid app credentials");
        }

        long ttlSeconds = expireSeconds > 0 ? expireSeconds : DEFAULT_EXPIRE_SECONDS;
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        String credential = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        stringRedisTemplate.opsForValue().set(
                CREDENTIAL_KEY_PREFIX + credential, String.valueOf(userId), ttlSeconds, TimeUnit.SECONDS);

        long expireAt = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(ttlSeconds);
        return new IssuedCredential(credential.getBytes(StandardCharsets.UTF_8), expireAt);
    }

    private long parseRawUserId(byte[] encryptedCredential) {
        try {
            return Long.parseLong(new String(encryptedCredential, StandardCharsets.UTF_8).trim());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.AUTH_FAILED, "invalid credential");
        }
    }

    public record IssuedCredential(byte[] credential, long expireAt) {
    }
}
