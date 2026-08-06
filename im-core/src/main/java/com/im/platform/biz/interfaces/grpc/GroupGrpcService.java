package com.im.platform.biz.interfaces.grpc;

import com.im.platform.biz.application.GroupApplicationService;
import com.im.platform.biz.domain.group.Group;
import com.im.platform.biz.domain.group.GroupJoinRequestRecord;
import com.im.platform.biz.domain.group.GroupMember;
import com.im.platform.biz.domain.group.GroupRole;
import com.im.platform.biz.domain.group.JoinGroupResult;
import com.im.platform.biz.grpc.AddMemberRequest;
import com.im.platform.biz.grpc.CreateGroupRequest;
import com.im.platform.biz.grpc.GetGroupInfoRequest;
import com.im.platform.biz.grpc.GetGroupMembersRequest;
import com.im.platform.biz.grpc.GetJoinRequestsRequest;
import com.im.platform.biz.grpc.GetMyGroupsRequest;
import com.im.platform.biz.grpc.GroupInfo;
import com.im.platform.biz.grpc.GroupInfoList;
import com.im.platform.biz.grpc.GroupJoinRequestInfo;
import com.im.platform.biz.grpc.GroupJoinRequestList;
import com.im.platform.biz.grpc.GroupJoinRequestStatus;
import com.im.platform.biz.grpc.GroupMemberInfo;
import com.im.platform.biz.grpc.GroupMemberList;
import com.im.platform.biz.grpc.GroupServiceGrpc;
import com.im.platform.biz.grpc.HandleJoinRequestRequest;
import com.im.platform.biz.grpc.LeaveGroupRequest;
import com.im.platform.biz.grpc.MuteMemberRequest;
import com.im.platform.biz.grpc.RemoveMemberRequest;
import com.im.platform.biz.grpc.RequestJoinGroupRequest;
import com.im.platform.biz.grpc.RequestJoinGroupResponse;
import com.im.platform.biz.grpc.TransferOwnerRequest;
import com.im.platform.biz.grpc.UpdateGroupMuteAllRequest;
import com.im.platform.biz.grpc.UpdateJoinModeRequest;
import com.im.platform.biz.grpc.UpdateMemberRoleRequest;
import com.im.platform.common.protocol.grpc.Empty;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * gRPC 适配层,只做 protobuf DTO &lt;-&gt; 领域对象的转换,不包含业务判断。
 * 抛出的 BizException 由 BizExceptionInterceptor 统一转 Status。
 */
@Component
public class GroupGrpcService extends GroupServiceGrpc.GroupServiceImplBase {

    private final GroupApplicationService groupApplicationService;

    public GroupGrpcService(GroupApplicationService groupApplicationService) {
        this.groupApplicationService = groupApplicationService;
    }

    @Override
    public void createGroup(CreateGroupRequest request, StreamObserver<GroupInfo> responseObserver) {
        Group group = groupApplicationService.createGroup(request.getOwnerId(), request.getGroupName(), request.getEx());
        responseObserver.onNext(toProto(group));
        responseObserver.onCompleted();
    }

    @Override
    public void transferOwner(TransferOwnerRequest request, StreamObserver<Empty> responseObserver) {
        groupApplicationService.transferOwner(request.getGroupId(), request.getOperatorId(), request.getNewOwnerId());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void addMember(AddMemberRequest request, StreamObserver<Empty> responseObserver) {
        groupApplicationService.addMember(request.getGroupId(), request.getOperatorId(), request.getTargetUserId());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void removeMember(RemoveMemberRequest request, StreamObserver<Empty> responseObserver) {
        groupApplicationService.removeMember(request.getGroupId(), request.getOperatorId(), request.getTargetUserId());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void leaveGroup(LeaveGroupRequest request, StreamObserver<Empty> responseObserver) {
        groupApplicationService.leaveGroup(request.getGroupId(), request.getUserId());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void getMyGroups(GetMyGroupsRequest request, StreamObserver<GroupInfoList> responseObserver) {
        List<Group> groups = groupApplicationService.getMyGroups(request.getUserId());
        GroupInfoList.Builder builder = GroupInfoList.newBuilder();
        for (Group group : groups) {
            builder.addGroups(toProto(group));
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void getGroupMembers(GetGroupMembersRequest request, StreamObserver<GroupMemberList> responseObserver) {
        List<GroupMember> members = groupApplicationService.getGroupMembers(request.getGroupId(), request.getOperatorId());
        GroupMemberList.Builder builder = GroupMemberList.newBuilder();
        for (GroupMember member : members) {
            builder.addMembers(GroupMemberInfo.newBuilder()
                    .setUserId(member.getUserId())
                    .setRole(member.getRole().ordinal())
                    .setJoinedAt(member.getJoinedAt())
                    .setMutedUntil(member.getMutedUntil())
                    .setEx(member.getEx() == null ? "" : member.getEx())
                    .build());
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void updateMemberRole(UpdateMemberRoleRequest request, StreamObserver<Empty> responseObserver) {
        groupApplicationService.updateMemberRole(request.getGroupId(), request.getOperatorId(),
                request.getTargetUserId(), GroupRole.values()[request.getRole()]);
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void getGroupInfo(GetGroupInfoRequest request, StreamObserver<GroupInfo> responseObserver) {
        Group group = groupApplicationService.getGroup(request.getGroupId());
        responseObserver.onNext(toProto(group));
        responseObserver.onCompleted();
    }

    @Override
    public void requestJoinGroup(RequestJoinGroupRequest request, StreamObserver<RequestJoinGroupResponse> responseObserver) {
        JoinGroupResult result = groupApplicationService.requestJoinGroup(
                request.getGroupId(), request.getUserId(), request.getGreeting());
        responseObserver.onNext(RequestJoinGroupResponse.newBuilder()
                .setJoinedImmediately(result.joinedImmediately())
                .setRequestId(result.requestId())
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void handleJoinRequest(HandleJoinRequestRequest request, StreamObserver<Empty> responseObserver) {
        groupApplicationService.handleJoinRequest(
                request.getRequestId(), request.getOperatorId(), request.getAccept());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void getJoinRequests(GetJoinRequestsRequest request, StreamObserver<GroupJoinRequestList> responseObserver) {
        List<GroupJoinRequestRecord> records = groupApplicationService.getJoinRequests(request.getGroupId());

        GroupJoinRequestList.Builder builder = GroupJoinRequestList.newBuilder();
        for (GroupJoinRequestRecord record : records) {
            builder.addRequests(GroupJoinRequestInfo.newBuilder()
                    .setRequestId(record.requestId())
                    .setGroupId(record.groupId())
                    .setUserId(record.userId())
                    .setGreeting(record.greeting() == null ? "" : record.greeting())
                    .setStatus(GroupJoinRequestStatus.forNumber(record.status().ordinal()))
                    .setCreatedAt(record.createdAt())
                    .setHandledAt(record.handledAt() == null ? 0 : record.handledAt())
                    .build());
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void updateJoinMode(UpdateJoinModeRequest request, StreamObserver<Empty> responseObserver) {
        groupApplicationService.updateJoinMode(request.getGroupId(), request.getOperatorId(),
                com.im.platform.biz.domain.group.GroupJoinMode.values()[request.getJoinMode().getNumber()]);
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void updateGroupMuteAll(UpdateGroupMuteAllRequest request, StreamObserver<Empty> responseObserver) {
        groupApplicationService.updateGroupMuteAll(request.getGroupId(), request.getOperatorId(), request.getMuted());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void muteMember(MuteMemberRequest request, StreamObserver<Empty> responseObserver) {
        groupApplicationService.muteMember(request.getGroupId(), request.getOperatorId(),
                request.getTargetUserId(), request.getMutedUntil());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    private GroupInfo toProto(Group group) {
        return GroupInfo.newBuilder()
                .setGroupId(group.getGroupId())
                .setGroupName(group.getGroupName() == null ? "" : group.getGroupName())
                .setOwnerId(group.getOwnerId().orElse(0L))
                .setMemberCount(group.getMemberCount())
                .setEx(group.getEx() == null ? "" : group.getEx())
                .setJoinMode(com.im.platform.biz.grpc.GroupJoinMode.forNumber(group.getJoinMode().ordinal()))
                .setGroupMuted(group.isGroupMuted())
                .build();
    }
}
