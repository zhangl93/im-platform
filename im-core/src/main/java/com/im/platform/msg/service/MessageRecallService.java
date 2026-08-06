package com.im.platform.msg.service;

import com.im.platform.common.core.exception.BizException;
import com.im.platform.common.core.exception.ErrorCode;
import com.im.platform.conversation.RecipientResolver;
import com.im.platform.msg.entity.MessageEntity;
import com.im.platform.msg.store.MessageStore;
import com.im.platform.sync.service.SyncEventTypes;
import com.im.platform.sync.service.UpdateLogService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 消息撤回:只有发送者本人能撤回,且必须在发送后 {@link #RECALL_WINDOW} 之内——参考主流 IM
 * 的"2 分钟撤回窗口"惯例,不做成可配置项(没有真实场景需要按业务方定制这个数字)。
 * 不做物理删除,只打 recalled 标记(见 MessageStore.markRecalled),pullHistory 侧按这个
 * 标记决定是否清空 content、返回占位。撤回事件走跟已读回执同一套 update_log 广播路径
 * (SyncEventTypes),不单独做实时 PUSH——理由跟 ReadCursorService 一致:这是"消息状态变更",
 * 不是"新消息",客户端下次 PullUpdates 就能拿到,不需要额外一条实时推送链路。
 */
@Service
public class MessageRecallService {

    private static final Duration RECALL_WINDOW = Duration.ofMinutes(2);

    private final MessageStore messageStore;
    private final RecipientResolver recipientResolver;
    private final UpdateLogService updateLogService;

    public MessageRecallService(MessageStore messageStore, RecipientResolver recipientResolver,
                                 UpdateLogService updateLogService) {
        this.messageStore = messageStore;
        this.recipientResolver = recipientResolver;
        this.updateLogService = updateLogService;
    }

    public void recall(long chatId, long userId, long messageId) {
        MessageEntity message = messageStore.findById(chatId, messageId);
        // chatId 是调用方自己声称的,必须跟消息实际所在的会话一致——否则拿一条自己在别处发过的
        // 消息、配一个不相关的 chatId,就能让撤回通知广播给那个不相关会话的参与者(见 code review)。
        // 找不到消息、或者 chatId 对不上,统一当成"这个会话里没有这条消息",跟 findById 的
        // not-found 语义一致,不额外区分。
        if (message == null || message.getChatId() == null || message.getChatId() != chatId) {
            throw new BizException(ErrorCode.MESSAGE_NOT_FOUND);
        }
        // 权限校验必须排在幂等短路之前——不然"已经撤回过"这个状态会让任何人(不只是发送者)
        // 调用都返回成功,等于绕过了"只有发送者能撤回"这条规则,也让非发送者能靠返回值探测
        // 一条消息是否已经被撤回。
        if (message.getSenderId() == null || message.getSenderId() != userId) {
            throw new BizException(ErrorCode.MESSAGE_RECALL_NOT_OWNER);
        }
        if (Boolean.TRUE.equals(message.getRecalled())) {
            return; // 发送者本人重复撤回,幂等直接返回,跟本项目其它写操作的幂等约定一致
        }
        if (System.currentTimeMillis() - message.getServerTime() > RECALL_WINDOW.toMillis()) {
            throw new BizException(ErrorCode.MESSAGE_RECALL_WINDOW_EXPIRED);
        }

        messageStore.markRecalled(chatId, messageId);

        byte[] payload = (chatId + ":" + messageId).getBytes(StandardCharsets.UTF_8);
        updateLogService.appendForUser(userId, SyncEventTypes.MESSAGE_RECALLED, payload);
        for (Long recipientId : recipientResolver.resolveRecipients(chatId, userId)) {
            updateLogService.appendForUser(recipientId, SyncEventTypes.MESSAGE_RECALLED, payload);
        }
    }
}
