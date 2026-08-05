package com.im.platform.session.interfaces.grpc;

import com.google.protobuf.ByteString;
import com.im.platform.common.protocol.crypto.Ed25519Signer;
import com.im.platform.common.protocol.crypto.KeyDerivation;
import com.im.platform.common.protocol.crypto.X25519KeyExchange;
import com.im.platform.common.protocol.grpc.Empty;
import com.im.platform.session.auth.CredentialAuthenticator;
import com.im.platform.session.crypto.IdentityKeyProvider;
import com.im.platform.session.grpc.AuthRequest;
import com.im.platform.session.grpc.AuthResponse;
import com.im.platform.session.grpc.CloseSessionRequest;
import com.im.platform.session.grpc.IssueUserCredentialRequest;
import com.im.platform.session.grpc.IssueUserCredentialResponse;
import com.im.platform.session.grpc.NegotiateKeyRequest;
import com.im.platform.session.grpc.NegotiateKeyResponse;
import com.im.platform.session.grpc.SessionServiceGrpc;
import com.im.platform.session.grpc.ValidateSessionRequest;
import com.im.platform.session.grpc.ValidateSessionResponse;
import com.im.platform.session.manager.SessionRecord;
import com.im.platform.session.service.SessionApplicationService;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;

/**
 * gRPC 适配层,只做 protobuf DTO &lt;-&gt; SessionRecord 的转换。
 * 抛出的 BizException 由 GrpcServerLifecycle 挂的 BizExceptionInterceptor 统一转 Status。
 */
@Component
public class SessionGrpcService extends SessionServiceGrpc.SessionServiceImplBase {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SessionApplicationService applicationService;
    private final IdentityKeyProvider identityKeyProvider;

    public SessionGrpcService(SessionApplicationService applicationService, IdentityKeyProvider identityKeyProvider) {
        this.applicationService = applicationService;
        this.identityKeyProvider = identityKeyProvider;
    }

    @Override
    public void negotiateKey(NegotiateKeyRequest request, StreamObserver<NegotiateKeyResponse> responseObserver) {
        // 真正的 X25519 DH 交换:生成一次性的服务端密钥对,用客户端公钥算出共享密钥,
        // 派生出 AES key 一起返回给 gateway(gateway 转发给客户端前必须把 derived_key 字段剥掉)。
        X25519KeyExchange.KeyPairBytes serverKeyPair = X25519KeyExchange.generateKeyPair();
        byte[] clientPublicKey = request.getClientPublicKey().toByteArray();
        byte[] sharedSecret = X25519KeyExchange.computeSharedSecret(serverKeyPair.privateKey(), clientPublicKey);
        SecretKeySpec aesKey = KeyDerivation.deriveAesKey(sharedSecret);

        // 用长期身份私钥对 (client_public_key || server_public_key) 签名,客户端拿出厂内置的
        // 身份公钥校验这个签名——校验通过才能确认 server_public_key 没有被中间人替换。
        byte[] signedMessage = concat(clientPublicKey, serverKeyPair.publicKey());
        byte[] signature = Ed25519Signer.sign(identityKeyProvider.privateKey(), signedMessage);

        long authKeyId = SECURE_RANDOM.nextLong();
        NegotiateKeyResponse response = NegotiateKeyResponse.newBuilder()
                .setAuthKeyId(authKeyId)
                .setServerPublicKey(ByteString.copyFrom(serverKeyPair.publicKey()))
                .setDerivedKey(ByteString.copyFrom(aesKey.getEncoded()))
                .setSignature(ByteString.copyFrom(signature))
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private static byte[] concat(byte[] a, byte[] b) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(a.length + b.length);
        out.writeBytes(a);
        out.writeBytes(b);
        return out.toByteArray();
    }

    @Override
    public void authenticate(AuthRequest request, StreamObserver<AuthResponse> responseObserver) {
        SessionRecord record = applicationService.authenticate(
                request.getDeviceId(),
                request.getEncryptedCredential().toByteArray(),
                request.getClientVersion(),
                request.getAuthKeyId());

        responseObserver.onNext(AuthResponse.newBuilder()
                .setUserId(record.getUserId())
                .setSessionToken(record.getSessionToken())
                .setExpireAt(record.getExpireAt())
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void validateSession(ValidateSessionRequest request, StreamObserver<ValidateSessionResponse> responseObserver) {
        SessionRecord record = applicationService.validate(request.getSessionToken());
        ValidateSessionResponse response = record == null
                ? ValidateSessionResponse.newBuilder().setValid(false).build()
                : ValidateSessionResponse.newBuilder().setValid(true).setUserId(record.getUserId()).build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void closeSession(CloseSessionRequest request, StreamObserver<Empty> responseObserver) {
        applicationService.close(request.getSessionToken());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void issueUserCredential(IssueUserCredentialRequest request, StreamObserver<IssueUserCredentialResponse> responseObserver) {
        CredentialAuthenticator.IssuedCredential issued = applicationService.issueUserCredential(
                request.getAppKey(), request.getAppSecret(), request.getUserId(), request.getExpireSeconds());
        responseObserver.onNext(IssueUserCredentialResponse.newBuilder()
                .setCredential(ByteString.copyFrom(issued.credential()))
                .setExpireAt(issued.expireAt())
                .build());
        responseObserver.onCompleted();
    }
}
