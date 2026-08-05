package com.im.platform.session.crypto;

import com.im.platform.common.protocol.crypto.Ed25519Signer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

/**
 * im-core 的长期身份密钥(Ed25519),握手时给临时 DH 公钥签名用,客户端拿这个签名 +
 * 自己出厂内置的公钥(pinning)校验,防止中间人在 gateway 和客户端之间替换 server_public_key。
 *
 * 生产环境必须同时配置 {@code im.session.identity-private-key-base64} 和
 * {@code im.session.identity-public-key-base64}(都是 base64 编码的标准 DER:私钥 PKCS8,
 * 公钥 X.509),保证重启后身份不变——客户端出厂内置的是固定公钥,身份密钥一变所有客户端的
 * pinning 全部失效。JDK 的 Ed25519 KeyFactory 不支持从私钥反推公钥,所以两个都要配,
 * 不能只配私钥。没配置时生成一对临时的,只是为了让骨架能跑起来,启动日志会打醒目的警告。
 */
@Component
public class IdentityKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(IdentityKeyProvider.class);

    private final KeyPair keyPair;

    public IdentityKeyProvider(
            @Value("${im.session.identity-private-key-base64:}") String privateKeyBase64,
            @Value("${im.session.identity-public-key-base64:}") String publicKeyBase64) {
        boolean hasPrivate = privateKeyBase64 != null && !privateKeyBase64.isBlank();
        boolean hasPublic = publicKeyBase64 != null && !publicKeyBase64.isBlank();

        if (hasPrivate != hasPublic) {
            throw new IllegalStateException(
                    "im.session.identity-private-key-base64 and identity-public-key-base64 must be configured together");
        }

        if (hasPrivate) {
            PrivateKey privateKey = Ed25519Signer.decodePrivateKey(Base64.getDecoder().decode(privateKeyBase64));
            PublicKey publicKey = Ed25519Signer.decodePublicKey(Base64.getDecoder().decode(publicKeyBase64));
            this.keyPair = new KeyPair(publicKey, privateKey);
            log.info("loaded configured session identity key, public key (base64 X.509 DER) = {}", publicKeyBase64());
        } else {
            this.keyPair = Ed25519Signer.generateKeyPair();
            log.warn("im.session.identity-*-key-base64 not configured, generated an EPHEMERAL identity key "
                    + "-- this changes every restart, client public-key pinning WILL break across restarts, "
                    + "do not use this in production. public key (base64 X.509 DER) = {}", publicKeyBase64());
        }
    }

    public PrivateKey privateKey() {
        return keyPair.getPrivate();
    }

    public String publicKeyBase64() {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }
}
