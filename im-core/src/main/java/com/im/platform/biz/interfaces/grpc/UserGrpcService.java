package com.im.platform.biz.interfaces.grpc;

import com.im.platform.biz.application.FriendshipApplicationService;
import com.im.platform.biz.application.UserApplicationService;
import com.im.platform.biz.domain.friend.FriendRecord;
import com.im.platform.biz.domain.friend.FriendRequestRecord;
import com.im.platform.biz.domain.user.User;
import com.im.platform.biz.grpc.BlockUserRequest;
import com.im.platform.biz.grpc.ContactInfo;
import com.im.platform.biz.grpc.ContactList;
import com.im.platform.biz.grpc.FriendRequestInfo;
import com.im.platform.biz.grpc.FriendRequestList;
import com.im.platform.biz.grpc.FriendRequestStatus;
import com.im.platform.biz.grpc.GetContactsRequest;
import com.im.platform.biz.grpc.GetFriendRequestsRequest;
import com.im.platform.biz.grpc.GetUserRequest;
import com.im.platform.biz.grpc.HandleFriendRequestRequest;
import com.im.platform.biz.grpc.RemoveFriendRequest;
import com.im.platform.biz.grpc.SendFriendRequestRequest;
import com.im.platform.biz.grpc.SendFriendRequestResponse;
import com.im.platform.biz.grpc.UpdateProfileRequest;
import com.im.platform.biz.grpc.UserInfo;
import com.im.platform.biz.grpc.UserServiceGrpc;
import com.im.platform.common.protocol.grpc.Empty;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

/**
 * gRPC 适配层,只做 protobuf DTO &lt;-&gt; 领域对象的转换,不包含业务判断。
 * 抛出的 BizException 由 GrpcServerLifecycle 挂的 BizExceptionInterceptor 统一转 Status,
 * 这里不用写 try/catch。
 */
@Component
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {

    private final UserApplicationService userApplicationService;
    private final FriendshipApplicationService friendshipApplicationService;

    public UserGrpcService(UserApplicationService userApplicationService,
                            FriendshipApplicationService friendshipApplicationService) {
        this.userApplicationService = userApplicationService;
        this.friendshipApplicationService = friendshipApplicationService;
    }

    @Override
    public void getUser(GetUserRequest request, StreamObserver<UserInfo> responseObserver) {
        User user = userApplicationService.getUser(request.getUserId());
        responseObserver.onNext(toProto(user));
        responseObserver.onCompleted();
    }

    @Override
    public void updateProfile(UpdateProfileRequest request, StreamObserver<Empty> responseObserver) {
        userApplicationService.updateProfile(request.getUserId(), request.getNickname(),
                request.getAvatar(), request.getEx());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void blockUser(BlockUserRequest request, StreamObserver<Empty> responseObserver) {
        userApplicationService.blockUser(request.getUserId(), request.getTargetUserId());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void getContacts(GetContactsRequest request, StreamObserver<ContactList> responseObserver) {
        String pageToken = request.hasPage() ? request.getPage().getPageToken() : "";
        int pageSize = request.hasPage() ? request.getPage().getPageSize() : 20;
        java.util.List<FriendRecord> friends = friendshipApplicationService.getFriends(
                request.getUserId(), pageToken, pageSize);

        ContactList.Builder builder = ContactList.newBuilder();
        String nextPageToken = "";
        for (FriendRecord friend : friends) {
            builder.addContacts(ContactInfo.newBuilder()
                    .setUser(toProto(friend.user()))
                    .setRemark(friend.remark() == null ? "" : friend.remark())
                    .setFriendSince(friend.friendSince())
                    .build());
            nextPageToken = String.valueOf(friend.user().getUserId());
        }
        builder.setNextPageToken(nextPageToken);
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void sendFriendRequest(SendFriendRequestRequest request, StreamObserver<SendFriendRequestResponse> responseObserver) {
        long requestId = friendshipApplicationService.sendRequest(
                request.getFromUserId(), request.getToUserId(), request.getGreeting());
        responseObserver.onNext(SendFriendRequestResponse.newBuilder().setRequestId(requestId).build());
        responseObserver.onCompleted();
    }

    @Override
    public void handleFriendRequest(HandleFriendRequestRequest request, StreamObserver<Empty> responseObserver) {
        friendshipApplicationService.handleRequest(
                request.getRequestId(), request.getOperatorUserId(), request.getAccept());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void getFriendRequests(GetFriendRequestsRequest request, StreamObserver<FriendRequestList> responseObserver) {
        java.util.List<FriendRequestRecord> records = friendshipApplicationService.getFriendRequests(
                request.getUserId(), request.getIncoming());

        FriendRequestList.Builder builder = FriendRequestList.newBuilder();
        for (FriendRequestRecord record : records) {
            builder.addRequests(FriendRequestInfo.newBuilder()
                    .setRequestId(record.requestId())
                    .setFromUserId(record.fromUserId())
                    .setToUserId(record.toUserId())
                    .setGreeting(record.greeting() == null ? "" : record.greeting())
                    .setStatus(FriendRequestStatus.forNumber(record.status().ordinal()))
                    .setCreatedAt(record.createdAt())
                    .setHandledAt(record.handledAt() == null ? 0 : record.handledAt())
                    .build());
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void removeFriend(RemoveFriendRequest request, StreamObserver<Empty> responseObserver) {
        friendshipApplicationService.removeFriend(request.getUserId(), request.getFriendId());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    private UserInfo toProto(User user) {
        return UserInfo.newBuilder()
                .setUserId(user.getUserId())
                .setNickname(user.getNickname() == null ? "" : user.getNickname())
                .setAvatar(user.getAvatar() == null ? "" : user.getAvatar())
                // 领域层 UserStatus 和 proto 里的 UserStatus 顺序保持一致,直接按 ordinal 映射
                .setStatus(com.im.platform.biz.grpc.UserStatus.forNumber(user.getStatus().ordinal()))
                .setEx(user.getEx() == null ? "" : user.getEx())
                .build();
    }
}
