package com.im.platform.sdk;

import com.im.platform.biz.grpc.AddMemberRequest;
import com.im.platform.biz.grpc.CreateGroupRequest;
import com.im.platform.biz.grpc.GetUserRequest;
import com.im.platform.biz.grpc.GroupInfo;
import com.im.platform.biz.grpc.GroupServiceGrpc;
import com.im.platform.biz.grpc.UpdateProfileRequest;
import com.im.platform.biz.grpc.UserInfo;
import com.im.platform.biz.grpc.UserServiceGrpc;
import com.im.platform.dfs.grpc.FileServiceGrpc;
import com.im.platform.dfs.grpc.UploadRequest;
import com.im.platform.dfs.grpc.UploadTicket;
import com.im.platform.msg.grpc.MessageServiceGrpc;
import com.im.platform.msg.grpc.SendMessageResponse;
import com.im.platform.session.grpc.IssueUserCredentialRequest;
import com.im.platform.session.grpc.IssueUserCredentialResponse;
import com.im.platform.session.grpc.SessionServiceGrpc;
import com.im.platform.status.grpc.GetStatusRequest;
import com.im.platform.status.grpc.StatusServiceGrpc;
import com.im.platform.status.grpc.UserStatusInfo;
import com.im.platform.sync.grpc.PullUpdatesRequest;
import com.im.platform.sync.grpc.SyncServiceGrpc;
import com.im.platform.sync.grpc.UpdatesResponse;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.concurrent.TimeUnit;

/**
 * 业务后端接入 im-core 的瘦客户端。封装了最常用的管理操作(发消息/建群/查用户/查在线状态/
 * 拿文件上传凭证),不常用的可以直接拿 rawXxxStub() 自己调,不需要 SDK 把每个 RPC 都包一遍。
 *
 * 用法:
 * <pre>{@code
 * try (ImCoreClient client = ImCoreClient.connect("127.0.0.1", 9080)) {
 *     UserInfo user = client.getUser(1001L);
 * }
 * }</pre>
 */
public final class ImCoreClient implements AutoCloseable {

    private final ManagedChannel channel;

    private final UserServiceGrpc.UserServiceBlockingStub userStub;
    private final GroupServiceGrpc.GroupServiceBlockingStub groupStub;
    private final MessageServiceGrpc.MessageServiceBlockingStub messageStub;
    private final SyncServiceGrpc.SyncServiceBlockingStub syncStub;
    private final StatusServiceGrpc.StatusServiceBlockingStub statusStub;
    private final FileServiceGrpc.FileServiceBlockingStub fileStub;
    private final SessionServiceGrpc.SessionServiceBlockingStub sessionStub;

    private ImCoreClient(ManagedChannel channel) {
        this.channel = channel;
        this.userStub = UserServiceGrpc.newBlockingStub(channel);
        this.groupStub = GroupServiceGrpc.newBlockingStub(channel);
        this.messageStub = MessageServiceGrpc.newBlockingStub(channel);
        this.syncStub = SyncServiceGrpc.newBlockingStub(channel);
        this.statusStub = StatusServiceGrpc.newBlockingStub(channel);
        this.fileStub = FileServiceGrpc.newBlockingStub(channel);
        this.sessionStub = SessionServiceGrpc.newBlockingStub(channel);
    }

    public static ImCoreClient connect(String host, int port) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext() // 内网服务间调用,先不做 mTLS
                .build();
        return new ImCoreClient(channel);
    }

    // ------------------------------------------------------------------
    // 常用操作的门面方法
    // ------------------------------------------------------------------

    public UserInfo getUser(long userId) {
        return userStub.getUser(GetUserRequest.newBuilder().setUserId(userId).build());
    }

    /** ex 是业务自定义扩展字段(通常是 JSON 字符串),IM 核心原样存取、不解析内容。 */
    public void updateProfile(long userId, String nickname, String avatar, String ex) {
        userStub.updateProfile(UpdateProfileRequest.newBuilder()
                .setUserId(userId).setNickname(nickname).setAvatar(avatar).setEx(ex).build());
    }

    public GroupInfo createGroup(long ownerId, String groupName) {
        return createGroup(ownerId, groupName, "");
    }

    public GroupInfo createGroup(long ownerId, String groupName, String ex) {
        return groupStub.createGroup(CreateGroupRequest.newBuilder()
                .setOwnerId(ownerId).setGroupName(groupName).setEx(ex).build());
    }

    public void addGroupMember(long groupId, long operatorId, long targetUserId) {
        groupStub.addMember(AddMemberRequest.newBuilder()
                .setGroupId(groupId).setOperatorId(operatorId).setTargetUserId(targetUserId).build());
    }

    /** 以系统身份代发一条消息(比如系统通知)。 */
    public SendMessageResponse sendMessage(long chatId, long senderId, String clientMsgId, byte[] content, int msgType) {
        return messageStub.sendMessage(com.im.platform.msg.grpc.SendMessageRequest.newBuilder()
                .setChatId(chatId)
                .setSenderId(senderId)
                .setClientMsgId(clientMsgId)
                .setContent(ByteString.copyFrom(content))
                .setMsgType(msgType)
                .build());
    }

    public UpdatesResponse pullUpdates(long userId, long lastSeq) {
        return syncStub.pullUpdates(PullUpdatesRequest.newBuilder()
                .setUserId(userId).setLastSeq(lastSeq).build());
    }

    public UserStatusInfo getStatus(long userId) {
        return statusStub.getStatus(GetStatusRequest.newBuilder().setUserId(userId).build());
    }

    public UploadTicket requestUpload(long ownerId, String fileName, long fileSize, String contentType) {
        return fileStub.requestUpload(UploadRequest.newBuilder()
                .setOwnerId(ownerId).setFileName(fileName).setFileSize(fileSize).setContentType(contentType).build());
    }

    /**
     * 用 app_key/app_secret 换一个登录凭证,下发给自己的客户端后,客户端拿它填
     * AuthRequest.encrypted_credential 发起登录——IM 核心不管业务是怎么认证这个用户的
     * (密码/短信/OAuth 都行),只负责"业务后端说这是谁,就给谁签一个能登录的凭证"。
     * expireSeconds &lt;= 0 时用服务端默认值(7 天)。
     */
    public IssueUserCredentialResponse issueUserCredential(String appKey, String appSecret, long userId, long expireSeconds) {
        return sessionStub.issueUserCredential(IssueUserCredentialRequest.newBuilder()
                .setAppKey(appKey).setAppSecret(appSecret).setUserId(userId).setExpireSeconds(expireSeconds).build());
    }

    // ------------------------------------------------------------------
    // 逃生舱:没封装的 RPC 直接拿 stub 自己调
    // ------------------------------------------------------------------

    public UserServiceGrpc.UserServiceBlockingStub rawUserStub() {
        return userStub;
    }

    public GroupServiceGrpc.GroupServiceBlockingStub rawGroupStub() {
        return groupStub;
    }

    public MessageServiceGrpc.MessageServiceBlockingStub rawMessageStub() {
        return messageStub;
    }

    public SyncServiceGrpc.SyncServiceBlockingStub rawSyncStub() {
        return syncStub;
    }

    public StatusServiceGrpc.StatusServiceBlockingStub rawStatusStub() {
        return statusStub;
    }

    public FileServiceGrpc.FileServiceBlockingStub rawFileStub() {
        return fileStub;
    }

    public SessionServiceGrpc.SessionServiceBlockingStub rawSessionStub() {
        return sessionStub;
    }

    @Override
    public void close() {
        channel.shutdown();
        try {
            channel.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
