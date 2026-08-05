package com.im.platform.common.protocol.crypto;

import javax.crypto.AEADBadTagException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * AES-GCM 加解密(带认证,篡改会直接解密失败,不需要额外的完整性校验)。
 * JDK 自带 GCM 实现,不需要 Bouncy Castle。
 */
public final class AesGcmCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BITS = 128;
    public static final int IV_LENGTH_BYTES = 12; // GCM 推荐的 96 位 IV,各语言/库支持最好,别为了对齐字段名改成别的长度

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private AesGcmCipher() {
    }

    public static byte[] randomIv() {
        byte[] iv = new byte[IV_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(iv);
        return iv;
    }

    public static byte[] encrypt(SecretKeySpec key, byte[] iv, byte[] plaintext) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return cipher.doFinal(plaintext);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
                 | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            throw new IllegalStateException("AES-GCM encrypt failed", e);
        }
    }

    /**
     * @throws DecryptionException 密文被篡改、密钥不对、或 IV 不对时抛出——调用方(网关)应该把这个
     *                              当成"这个连接的密钥不可信"处理,不能当普通业务异常吞掉。
     */
    public static byte[] decrypt(SecretKeySpec key, byte[] iv, byte[] ciphertext) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (AEADBadTagException e) {
            throw new DecryptionException("GCM tag mismatch: ciphertext tampered or wrong key", e);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
                 | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            throw new DecryptionException("AES-GCM decrypt failed", e);
        }
    }

    public static class DecryptionException extends RuntimeException {
        public DecryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
