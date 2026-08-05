package com.im.platform.common.protocol.crypto;

import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 从 DH 共享密钥派生出 AES-256 密钥。
 *
 * 简化实现:SHA-256(sharedSecret) 直接当 AES key。真正的 MTProto 用的是更复杂的 KDF
 * (掺入 msg_key/server_salt 防重放),这里先用最简单的版本把链路跑通,
 * 生产化的时候换成 HKDF(带 salt/info)即可,调用方不需要变。
 */
public final class KeyDerivation {

    private static final String AES = "AES";

    private KeyDerivation() {
    }

    public static SecretKeySpec deriveAesKey(byte[] sharedSecret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(sharedSecret);
            return new SecretKeySpec(digest, AES); // SHA-256 输出正好 32 字节 = AES-256
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
