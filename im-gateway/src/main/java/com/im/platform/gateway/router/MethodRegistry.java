package com.im.platform.gateway.router;

import com.google.protobuf.Message;
import com.google.protobuf.Parser;
import com.im.platform.biz.grpc.AddMemberRequest;
import com.im.platform.biz.grpc.BlockUserRequest;
import com.im.platform.biz.grpc.CreateGroupRequest;
import com.im.platform.biz.grpc.GetContactsRequest;
import com.im.platform.biz.grpc.GetFriendRequestsRequest;
import com.im.platform.biz.grpc.GetGroupInfoRequest;
import com.im.platform.biz.grpc.GetJoinRequestsRequest;
import com.im.platform.biz.grpc.GetUserRequest;
import com.im.platform.biz.grpc.HandleFriendRequestRequest;
import com.im.platform.biz.grpc.HandleJoinRequestRequest;
import com.im.platform.biz.grpc.MuteMemberRequest;
import com.im.platform.biz.grpc.RemoveFriendRequest;
import com.im.platform.biz.grpc.RemoveMemberRequest;
import com.im.platform.biz.grpc.RequestJoinGroupRequest;
import com.im.platform.biz.grpc.SendFriendRequestRequest;
import com.im.platform.biz.grpc.TransferOwnerRequest;
import com.im.platform.biz.grpc.UpdateGroupMuteAllRequest;
import com.im.platform.biz.grpc.UpdateJoinModeRequest;
import com.im.platform.biz.grpc.UpdateMemberRoleRequest;
import com.im.platform.biz.grpc.UpdateProfileRequest;
import com.im.platform.dfs.grpc.CompleteUploadRequest;
import com.im.platform.dfs.grpc.GetDownloadUrlRequest;
import com.im.platform.dfs.grpc.UploadRequest;
import com.im.platform.common.protocol.grpc.AckRequest;
import com.im.platform.common.protocol.grpc.AckResponse;
import com.im.platform.common.protocol.grpc.HeartbeatRequest;
import com.im.platform.common.protocol.grpc.HeartbeatResponse;
import com.im.platform.gateway.client.CoreGrpcClients;
import com.im.platform.msg.grpc.GetConversationSettingsRequest;
import com.im.platform.msg.grpc.GetOrCreateSingleChatRequest;
import com.im.platform.msg.grpc.PullHistoryRequest;
import com.im.platform.msg.grpc.SendMessageRequest;
import com.im.platform.msg.grpc.UpdateConversationSettingRequest;
import com.im.platform.msg.grpc.UpdateReadCursorRequest;
import com.im.platform.push.grpc.RegisterPushTokenRequest;
import com.im.platform.push.grpc.UnregisterPushTokenRequest;
import com.im.platform.session.grpc.AuthRequest;
import com.im.platform.session.grpc.CloseSessionRequest;
import com.im.platform.session.grpc.ValidateSessionRequest;
import com.im.platform.status.grpc.BatchGetStatusRequest;
import com.im.platform.status.grpc.GetStatusRequest;
import com.im.platform.status.grpc.SetOfflineRequest;
import com.im.platform.status.grpc.SetOnlineRequest;
import com.im.platform.sync.grpc.PullUpdatesRequest;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * method_id -&gt; 处理器 的注册表。NegotiateKey 不在这里(它在握手阶段特殊处理,见
 * MethodRouter),这里覆盖握手完成之后的全部业务方法。
 *
 * 新增一个方法只需要加一行 register(...) 调用,不用碰分发逻辑(开闭原则)。
 */
@Component
public class MethodRegistry {

    private final Map<Integer, MethodHandler> handlers = new HashMap<>();

    public MethodRegistry(CoreGrpcClients core) {
        // session
        register(MethodIds.AUTHENTICATE, AuthRequest.parser(), core.session::authenticate);
        register(MethodIds.VALIDATE_SESSION, ValidateSessionRequest.parser(), core.session::validateSession);
        register(MethodIds.CLOSE_SESSION, CloseSessionRequest.parser(), core.session::closeSession);

        // biz.user
        register(MethodIds.GET_USER, GetUserRequest.parser(), core.user::getUser);
        register(MethodIds.UPDATE_PROFILE, UpdateProfileRequest.parser(), core.user::updateProfile);
        register(MethodIds.BLOCK_USER, BlockUserRequest.parser(), core.user::blockUser);
        register(MethodIds.GET_CONTACTS, GetContactsRequest.parser(), core.user::getContacts);
        register(MethodIds.SEND_FRIEND_REQUEST, SendFriendRequestRequest.parser(), core.user::sendFriendRequest);
        register(MethodIds.HANDLE_FRIEND_REQUEST, HandleFriendRequestRequest.parser(), core.user::handleFriendRequest);
        register(MethodIds.GET_FRIEND_REQUESTS, GetFriendRequestsRequest.parser(), core.user::getFriendRequests);
        register(MethodIds.REMOVE_FRIEND, RemoveFriendRequest.parser(), core.user::removeFriend);

        // biz.group
        register(MethodIds.CREATE_GROUP, CreateGroupRequest.parser(), core.group::createGroup);
        register(MethodIds.TRANSFER_OWNER, TransferOwnerRequest.parser(), core.group::transferOwner);
        register(MethodIds.ADD_MEMBER, AddMemberRequest.parser(), core.group::addMember);
        register(MethodIds.REMOVE_MEMBER, RemoveMemberRequest.parser(), core.group::removeMember);
        register(MethodIds.UPDATE_MEMBER_ROLE, UpdateMemberRoleRequest.parser(), core.group::updateMemberRole);
        register(MethodIds.GET_GROUP_INFO, GetGroupInfoRequest.parser(), core.group::getGroupInfo);
        register(MethodIds.REQUEST_JOIN_GROUP, RequestJoinGroupRequest.parser(), core.group::requestJoinGroup);
        register(MethodIds.HANDLE_JOIN_REQUEST, HandleJoinRequestRequest.parser(), core.group::handleJoinRequest);
        register(MethodIds.GET_JOIN_REQUESTS, GetJoinRequestsRequest.parser(), core.group::getJoinRequests);
        register(MethodIds.UPDATE_JOIN_MODE, UpdateJoinModeRequest.parser(), core.group::updateJoinMode);
        register(MethodIds.UPDATE_GROUP_MUTE_ALL, UpdateGroupMuteAllRequest.parser(), core.group::updateGroupMuteAll);
        register(MethodIds.MUTE_MEMBER, MuteMemberRequest.parser(), core.group::muteMember);

        // msg
        register(MethodIds.SEND_MESSAGE, SendMessageRequest.parser(), core.message::sendMessage);
        register(MethodIds.PULL_HISTORY, PullHistoryRequest.parser(), core.message::pullHistory);
        register(MethodIds.UPDATE_READ_CURSOR, UpdateReadCursorRequest.parser(), core.message::updateReadCursor);
        register(MethodIds.GET_OR_CREATE_SINGLE_CHAT, GetOrCreateSingleChatRequest.parser(), core.message::getOrCreateSingleChat);
        register(MethodIds.UPDATE_CONVERSATION_SETTING, UpdateConversationSettingRequest.parser(), core.message::updateConversationSetting);
        register(MethodIds.GET_CONVERSATION_SETTINGS, GetConversationSettingsRequest.parser(), core.message::getConversationSettings);

        // sync
        register(MethodIds.PULL_UPDATES, PullUpdatesRequest.parser(), core.sync::pullUpdates);

        // status
        register(MethodIds.SET_ONLINE, SetOnlineRequest.parser(), core.status::setOnline);
        register(MethodIds.SET_OFFLINE, SetOfflineRequest.parser(), core.status::setOffline);
        register(MethodIds.GET_STATUS, GetStatusRequest.parser(), core.status::getStatus);
        register(MethodIds.BATCH_GET_STATUS, BatchGetStatusRequest.parser(), core.status::batchGetStatus);

        // dfs
        register(MethodIds.REQUEST_UPLOAD, UploadRequest.parser(), core.file::requestUpload);
        register(MethodIds.COMPLETE_UPLOAD, CompleteUploadRequest.parser(), core.file::completeUpload);
        register(MethodIds.GET_DOWNLOAD_URL, GetDownloadUrlRequest.parser(), core.file::getDownloadUrl);

        // push
        register(MethodIds.REGISTER_PUSH_TOKEN, RegisterPushTokenRequest.parser(), core.pushToken::registerPushToken);
        register(MethodIds.UNREGISTER_PUSH_TOKEN, UnregisterPushTokenRequest.parser(), core.pushToken::unregisterPushToken);

        // 网关控制帧:纯本地处理,不转发给 im-core,连 gRPC 通道都不占用
        register(MethodIds.HEARTBEAT, HeartbeatRequest.parser(),
                req -> HeartbeatResponse.newBuilder().setServerTime(System.currentTimeMillis()).build());
        // ACK 立即本地应答,不在这里转发给 im-core——转发时需要这条连接绑定的 userId,
        // 这里的 Function<ReqT,RespT> 拿不到 channel 上下文,真正落库放在 MethodRouter.postProcess
        // (跟 AUTHENTICATE 绑定用户、HEARTBEAT 刷新在线状态是同一个套路)。
        register(MethodIds.ACK, AckRequest.parser(),
                req -> AckResponse.newBuilder().setOk(true).build());
    }

    private <ReqT extends Message, RespT extends Message> void register(
            int methodId, Parser<ReqT> parser, Function<ReqT, RespT> invoker) {
        handlers.put(methodId, payload -> {
            ReqT request = parser.parseFrom(payload);
            RespT response = invoker.apply(request);
            return response.toByteArray();
        });
    }

    /** @throws UnknownMethodException 客户端传了个网关不认识的 method_id。 */
    public byte[] dispatch(int methodId, byte[] payload) throws Exception {
        MethodHandler handler = handlers.get(methodId);
        if (handler == null) {
            throw new UnknownMethodException(methodId);
        }
        return handler.handle(payload);
    }

    public static class UnknownMethodException extends RuntimeException {
        public UnknownMethodException(int methodId) {
            super("unknown method_id: " + methodId);
        }
    }
}
