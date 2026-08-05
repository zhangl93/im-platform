package com.im.platform.common.protocol.crypto;

import javax.crypto.KeyAgreement;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.XECPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.NamedParameterSpec;
import java.security.spec.XECPublicKeySpec;

/**
 * X25519 密钥交换,对应架构文档"类 MTProto 的 DH 交换"。JDK 11+ 原生支持 X25519
 * (KeyPairGenerator/KeyAgreement 的标准算法名),不需要额外引入 Bouncy Castle。
 *
 * 公钥在网络上按 RFC 7748 的标准编码传输:32 字节、小端序的 u 坐标。
 * JDK 的 {@link XECPublicKey#getU()} 返回的是大端序无符号整数形式的 BigInteger,
 * 这里负责在两种表示之间转换——这是最容易出编码 bug 的地方,所以单独抽出来测试。
 */
public final class X25519KeyExchange {

    private static final int KEY_LEN = 32;

    private X25519KeyExchange() {
    }

    public record KeyPairBytes(byte[] publicKey, PrivateKey privateKey) {
    }

    public static KeyPairBytes generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
            KeyPair keyPair = generator.generateKeyPair();
            byte[] publicKeyBytes = encodePublicKey((XECPublicKey) keyPair.getPublic());
            return new KeyPairBytes(publicKeyBytes, keyPair.getPrivate());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("X25519 not supported by this JDK", e);
        }
    }

    /** 用己方私钥 + 对端公钥字节,算出共享密钥(原始字节,还需要过 KDF 才能当 AES key 用)。 */
    public static byte[] computeSharedSecret(PrivateKey ownPrivateKey, byte[] peerPublicKeyBytes) {
        try {
            PublicKey peerPublicKey = decodePublicKey(peerPublicKeyBytes);
            KeyAgreement keyAgreement = KeyAgreement.getInstance("X25519");
            keyAgreement.init(ownPrivateKey);
            keyAgreement.doPhase(peerPublicKey, true);
            return keyAgreement.generateSecret();
        } catch (NoSuchAlgorithmException | InvalidKeyException | InvalidKeySpecException e) {
            throw new IllegalArgumentException("invalid peer public key for X25519", e);
        }
    }

    private static byte[] encodePublicKey(XECPublicKey publicKey) {
        return uToLittleEndian(publicKey.getU());
    }

    private static PublicKey decodePublicKey(byte[] rawLittleEndian) throws NoSuchAlgorithmException, InvalidKeySpecException {
        if (rawLittleEndian.length != KEY_LEN) {
            throw new IllegalArgumentException("X25519 public key must be " + KEY_LEN + " bytes, got " + rawLittleEndian.length);
        }
        BigInteger u = littleEndianToU(rawLittleEndian);
        KeyFactory keyFactory = KeyFactory.getInstance("X25519");
        return keyFactory.generatePublic(new XECPublicKeySpec(NamedParameterSpec.X25519, u));
    }

    private static byte[] uToLittleEndian(BigInteger u) {
        byte[] bigEndian = u.toByteArray(); // 可能带符号位前导 0,也可能比 32 短
        int offset = (bigEndian.length == KEY_LEN + 1 && bigEndian[0] == 0) ? 1 : 0;
        int len = bigEndian.length - offset;

        byte[] littleEndian = new byte[KEY_LEN];
        for (int i = 0; i < len && i < KEY_LEN; i++) {
            littleEndian[i] = bigEndian[offset + len - 1 - i];
        }
        return littleEndian;
    }

    private static BigInteger littleEndianToU(byte[] littleEndian) {
        byte[] bigEndian = new byte[littleEndian.length];
        for (int i = 0; i < littleEndian.length; i++) {
            bigEndian[i] = littleEndian[littleEndian.length - 1 - i];
        }
        return new BigInteger(1, bigEndian); // 无符号
    }
}
