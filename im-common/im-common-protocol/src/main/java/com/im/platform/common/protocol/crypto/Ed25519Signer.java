package com.im.platform.common.protocol.crypto;

import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * Ed25519 签名,用来在握手时证明"这个临时 DH 公钥确实是持有身份私钥的服务端签发的"
 * (防中间人替换 server_public_key)。JDK 15+ 原生支持,不需要 Bouncy Castle。
 *
 * 公私钥的网络/配置传输统一用标准 DER 编码(公钥 X.509 SubjectPublicKeyInfo,
 * 私钥 PKCS8),不用像 X25519 那样手动处理原始字节的大小端转换——Ed25519 的原始编码
 * 还要处理符号位,手搓更容易出错,DER 编解码是 JDK 内置的,更可靠。
 */
public final class Ed25519Signer {

    private static final String ALGORITHM = "Ed25519";

    private Ed25519Signer() {
    }

    public static KeyPair generateKeyPair() {
        try {
            return KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Ed25519 not supported by this JDK", e);
        }
    }

    public static byte[] sign(PrivateKey privateKey, byte[] message) {
        try {
            java.security.Signature signature = java.security.Signature.getInstance(ALGORITHM);
            signature.initSign(privateKey);
            signature.update(message);
            return signature.sign();
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new IllegalStateException("Ed25519 sign failed", e);
        }
    }

    public static boolean verify(PublicKey publicKey, byte[] message, byte[] signatureBytes) {
        try {
            java.security.Signature signature = java.security.Signature.getInstance(ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(message);
            return signature.verify(signatureBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            return false; // 格式不对/密钥不对都算校验不通过,不区分"出错"和"校验失败"
        }
    }

    public static PublicKey decodePublicKey(byte[] x509Der) {
        try {
            return KeyFactory.getInstance(ALGORITHM).generatePublic(new X509EncodedKeySpec(x509Der));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalArgumentException("invalid Ed25519 public key encoding", e);
        }
    }

    public static PrivateKey decodePrivateKey(byte[] pkcs8Der) {
        try {
            return KeyFactory.getInstance(ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(pkcs8Der));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalArgumentException("invalid Ed25519 private key encoding", e);
        }
    }
}
