package com.im.platform.msg;

import com.im.platform.common.protocol.grpc.Empty;
import com.im.platform.conversation.ConversationService;
import com.im.platform.msg.entity.MessageEntity;
import com.im.platform.msg.grpc.AckMessageRequest;
import com.im.platform.msg.grpc.ChatInfo;
import com.im.platform.msg.grpc.ConversationSetting;
import com.im.platform.msg.grpc.ConversationSettingList;
import com.im.platform.msg.grpc.GetConversationSettingsRequest;
import com.im.platform.msg.grpc.GetOrCreateSingleChatRequest;
import com.im.platform.msg.grpc.GetUnreadCountRequest;
import com.im.platform.msg.grpc.MessageItem;
import com.im.platform.msg.grpc.MessageList;
import com.im.platform.msg.grpc.MessageServiceGrpc;
import com.im.platform.msg.grpc.PullHistoryRequest;
import com.im.platform.msg.grpc.SendMessageRequest;
import com.im.platform.msg.grpc.SendMessageResponse;
import com.im.platform.msg.grpc.UnreadCountResponse;
import com.im.platform.msg.grpc.UpdateConversationSettingRequest;
import com.im.platform.msg.grpc.UpdateReadCursorRequest;
import com.im.platform.msg.mapper.ConversationSettingMapper;
import com.im.platform.msg.service.AckService;
import com.im.platform.msg.service.ConversationSettingService;
import com.im.platform.msg.service.MessageQueryService;
import com.im.platform.msg.service.MessageWriteService;
import com.im.platform.msg.service.ReadCursorService;
import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * gRPC 适配层。im-msg 走简单三层,不做 DDD 分层,接口层直接调用 service 包下的两个服务。
 */
@Component
public class MessageGrpcService extends MessageServiceGrpc.MessageServiceImplBase {

    private final MessageWriteService messageWriteService;
    private final MessageQueryService messageQueryService;
    private final ConversationService conversationService;
    private final AckService ackService;
    private final ReadCursorService readCursorService;
    private final ConversationSettingService conversationSettingService;

    public MessageGrpcService(MessageWriteService messageWriteService,
                               MessageQueryService messageQueryService,
                               ConversationService conversationService,
                               AckService ackService,
                               ReadCursorService readCursorService,
                               ConversationSettingService conversationSettingService) {
        this.messageWriteService = messageWriteService;
        this.messageQueryService = messageQueryService;
        this.conversationService = conversationService;
        this.ackService = ackService;
        this.readCursorService = readCursorService;
        this.conversationSettingService = conversationSettingService;
    }

    @Override
    public void ack(AckMessageRequest request, StreamObserver<Empty> responseObserver) {
        ackService.ack(request.getUserId(), request.getMessageId());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void getOrCreateSingleChat(GetOrCreateSingleChatRequest request, StreamObserver<ChatInfo> responseObserver) {
        long chatId = conversationService.getOrCreateSingleChat(request.getUserA(), request.getUserB());
        responseObserver.onNext(ChatInfo.newBuilder().setChatId(chatId).build());
        responseObserver.onCompleted();
    }

    @Override
    public void sendMessage(SendMessageRequest request, StreamObserver<SendMessageResponse> responseObserver) {
        MessageEntity entity = messageWriteService.send(
                request.getChatId(), request.getSenderId(), request.getClientMsgId(),
                request.getContent().toByteArray(), request.getMsgType());

        responseObserver.onNext(SendMessageResponse.newBuilder()
                .setMessageId(entity.getMessageId())
                .setServerTime(entity.getServerTime())
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void pullHistory(PullHistoryRequest request, StreamObserver<MessageList> responseObserver) {
        int limit = request.getLimit() > 0 ? request.getLimit() : 20;
        List<MessageEntity> messages = messageQueryService.pullHistory(
                request.getChatId(), request.getBeforeMessageId(), limit);

        MessageList.Builder listBuilder = MessageList.newBuilder()
                .setHasMore(messages.size() >= limit);
        for (MessageEntity entity : messages) {
            listBuilder.addMessages(MessageItem.newBuilder()
                    .setMessageId(entity.getMessageId())
                    .setChatId(entity.getChatId())
                    .setSenderId(entity.getSenderId())
                    .setContent(ByteString.copyFrom(entity.getContent()))
                    .setMsgType(entity.getMsgType())
                    .setServerTime(entity.getServerTime())
                    .build());
        }
        responseObserver.onNext(listBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void updateReadCursor(UpdateReadCursorRequest request, StreamObserver<Empty> responseObserver) {
        readCursorService.updateReadCursor(request.getChatId(), request.getUserId(), request.getReadToMessageId());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void updateConversationSetting(UpdateConversationSettingRequest request, StreamObserver<Empty> responseObserver) {
        conversationSettingService.updateSetting(
                request.getUserId(), request.getChatId(), request.getMuted(), request.getPinned());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void getConversationSettings(GetConversationSettingsRequest request, StreamObserver<ConversationSettingList> responseObserver) {
        List<ConversationSettingMapper.ConversationSettingRow> rows =
                conversationSettingService.getSettings(request.getUserId());

        ConversationSettingList.Builder listBuilder = ConversationSettingList.newBuilder();
        for (ConversationSettingMapper.ConversationSettingRow row : rows) {
            listBuilder.addSettings(ConversationSetting.newBuilder()
                    .setChatId(row.getChatId())
                    .setMuted(Boolean.TRUE.equals(row.getIsMuted()))
                    .setPinned(Boolean.TRUE.equals(row.getIsPinned()))
                    .setUpdatedAt(row.getUpdatedAt())
                    .build());
        }
        responseObserver.onNext(listBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void getUnreadCount(GetUnreadCountRequest request, StreamObserver<UnreadCountResponse> responseObserver) {
        long unreadCount = readCursorService.getUnreadCount(request.getChatId(), request.getUserId());
        responseObserver.onNext(UnreadCountResponse.newBuilder().setUnreadCount(unreadCount).build());
        responseObserver.onCompleted();
    }
}
