package com.im.platform.sync.interfaces.grpc;

import com.google.protobuf.ByteString;
import com.im.platform.msg.service.AckService;
import com.im.platform.sync.entity.UpdateLogEntity;
import com.im.platform.sync.grpc.PullUpdatesRequest;
import com.im.platform.sync.grpc.SyncServiceGrpc;
import com.im.platform.sync.grpc.UpdateEvent;
import com.im.platform.sync.grpc.UpdatesResponse;
import com.im.platform.sync.service.SyncEventTypes;
import com.im.platform.sync.service.UpdateLogService;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class SyncGrpcService extends SyncServiceGrpc.SyncServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(SyncGrpcService.class);

    private final UpdateLogService updateLogService;
    private final AckService ackService;

    public SyncGrpcService(UpdateLogService updateLogService, AckService ackService) {
        this.updateLogService = updateLogService;
        this.ackService = ackService;
    }

    @Override
    public void pullUpdates(PullUpdatesRequest request, StreamObserver<UpdatesResponse> responseObserver) {
        List<UpdateLogEntity> entries = updateLogService.pullSince(request.getUserId(), request.getLastSeq());

        UpdatesResponse.Builder builder = UpdatesResponse.newBuilder();
        long newSeq = request.getLastSeq();
        for (UpdateLogEntity entry : entries) {
            newSeq = Math.max(newSeq, entry.getSeq());
            // NEW_MESSAGE 类型的事件如果这条消息已经通过实时推送确认过(ACK 记录命中),
            // 说明客户端在离线补偿窗口之外已经收到过了,这里跳过、不重复下发,但 seq 游标照样推进
            // ——不能因为过滤掉一条就让客户端下次从同一个 seq 重新拉,那样会死循环拉到同一条。
            if (entry.getEventType() == SyncEventTypes.NEW_MESSAGE
                    && ackService.isAcked(request.getUserId(), parseMessageId(entry.getPayload()))) {
                continue;
            }
            builder.addUpdates(UpdateEvent.newBuilder()
                    .setSeq(entry.getSeq())
                    .setEventType(entry.getEventType())
                    .setPayload(ByteString.copyFrom(entry.getPayload()))
                    .build());
        }
        builder.setNewSeq(newSeq);

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    /** payload 格式见 SyncMessageNotifier:"messageId:chatId:msgType"。解析失败就当没 ack 过,照常下发。 */
    private long parseMessageId(byte[] payload) {
        try {
            String text = new String(payload, StandardCharsets.UTF_8);
            return Long.parseLong(text.substring(0, text.indexOf(':')));
        } catch (Exception e) {
            log.warn("failed to parse message_id from update-log payload, treating as not-acked", e);
            return -1L;
        }
    }
}
