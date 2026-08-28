package com.im.platform.msg.service;

import com.im.platform.biz.domain.group.Group;
import com.im.platform.biz.domain.group.GroupRepository;
import com.im.platform.common.core.constant.BizType;
import com.im.platform.common.core.exception.BizException;
import com.im.platform.common.core.exception.ErrorCode;
import com.im.platform.conversation.RecipientResolver;
import com.im.platform.core.callback.AfterSendMessagePayload;
import com.im.platform.core.callback.BeforeSendMessagePayload;
import com.im.platform.core.callback.CallbackInvoker;
import com.im.platform.core.callback.CallbackResult;
import com.im.platform.core.push.PushPublisher;
import com.im.platform.idgen.IdGenClient;
import com.im.platform.msg.entity.MessageEntity;
import com.im.platform.msg.service.moderation.SensitiveWordMatcher;
import com.im.platform.msg.store.MessageStore;
import com.im.platform.push.service.OfflinePushTriggerService;
import com.im.platform.sync.service.MessageSyncNotifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 消息写入主链路:幂等去重 -&gt; 敏感词本地匹配(内置的、不可关闭的最后一道防线)
 * -&gt; beforeSendMessage 回调(业务自己的审核策略,可选) -&gt; 取号 -&gt; 落库 -&gt; afterSendMessage 回调(通知类)。
 *
 * 原来 im-moderation/im-translation/im-ai-analysis 三个独立服务承担的"内容处理管道"职责,
 * 现在通过 beforeSendMessage/afterSendMessage 两个回调钩子交给业务自己的系统,
 * IM 核心不再内置这些策略,只保留最基础的本地敏感词匹配作为兜底。
 */
@Service
public class MessageWriteService {

    private static final String IDEMPOTENT_KEY_PREFIX = "im:msg:idempotent:";
    private static final Duration IDEMPOTENT_TTL = Duration.ofMinutes(10);

    private final MessageStore messageStore;
    private final IdGenClient idGenClient;
    private final SensitiveWordMatcher sensitiveWordMatcher;
    private final CallbackInvoker callbackInvoker;
    private final MessageSyncNotifier syncNotifier;
    private final StringRedisTemplate stringRedisTemplate;
    private final RecipientResolver recipientResolver;
    private final PushPublisher pushPublisher;
    private final MessageMetrics messageMetrics;
    private final GroupMuteGuard groupMuteGuard;
    private final SingleChatBlockGuard singleChatBlockGuard;
    private final OfflinePushTriggerService offlinePushTriggerService;
    private final GroupRepository groupRepository;

    public MessageWriteService(MessageStore messageStore,
                                IdGenClient idGenClient,
                                SensitiveWordMatcher sensitiveWordMatcher,
                                CallbackInvoker callbackInvoker,
                                MessageSyncNotifier syncNotifier,
                                StringRedisTemplate stringRedisTemplate,
                                RecipientResolver recipientResolver,
                                PushPublisher pushPublisher,
                                MessageMetrics messageMetrics,
                                GroupMuteGuard groupMuteGuard,
                                SingleChatBlockGuard singleChatBlockGuard,
                                OfflinePushTriggerService offlinePushTriggerService,
                                GroupRepository groupRepository) {
        this.messageStore = messageStore;
        this.idGenClient = idGenClient;
        this.sensitiveWordMatcher = sensitiveWordMatcher;
        this.callbackInvoker = callbackInvoker;
        this.syncNotifier = syncNotifier;
        this.stringRedisTemplate = stringRedisTemplate;
        this.recipientResolver = recipientResolver;
        this.pushPublisher = pushPublisher;
        this.messageMetrics = messageMetrics;
        this.groupMuteGuard = groupMuteGuard;
        this.singleChatBlockGuard = singleChatBlockGuard;
        this.offlinePushTriggerService = offlinePushTriggerService;
        this.groupRepository = groupRepository;
    }

    public MessageEntity send(long chatId, long senderId, String clientMsgId, byte[] content, int msgType,
                               List<Long> atUserIds) {
        String idempotentKey = IDEMPOTENT_KEY_PREFIX + clientMsgId;

        String existingMessageId = stringRedisTemplate.opsForValue().get(idempotentKey);
        if (existingMessageId != null) {
            MessageEntity existing = messageStore.findById(chatId, Long.parseLong(existingMessageId));
            if (existing != null) {
                return existing;
            }
        }

        // 群禁言检查、拉黑检查、收件人解析都要判断"这个 chat_id 是不是群聊",这里只查一次
        // GroupRepository,后面三处都复用这个结果,不重复查数据库(三个独立查询会各自再
        // 拉一遍群的完整成员列表,量级大的群上是可观的浪费)。
        Optional<Group> group = groupRepository.findById(chatId);
        groupMuteGuard.checkNotMuted(group, senderId);
        singleChatBlockGuard.checkNotBlocked(chatId, senderId, group);

        String textContent = new String(content, StandardCharsets.UTF_8);
        if (sensitiveWordMatcher.containsSensitiveWord(textContent)) {
            throw new BizException(ErrorCode.MESSAGE_SEND_FAILED, "content rejected by built-in moderation");
        }

        CallbackResult beforeResult = callbackInvoker.invoke(
                new BeforeSendMessagePayload(chatId, senderId, textContent, msgType));
        if (!beforeResult.pass()) {
            throw new BizException(ErrorCode.MESSAGE_SEND_FAILED, beforeResult.rejectReason());
        }

        long messageId = idGenClient.generateId(BizType.MESSAGE_ID, chatId);

        MessageEntity entity = new MessageEntity();
        entity.setMessageId(messageId);
        entity.setChatId(chatId);
        entity.setSenderId(senderId);
        entity.setContent(content);
        entity.setMsgType(msgType);
        entity.setServerTime(System.currentTimeMillis());
        entity.setClientMsgId(clientMsgId);
        entity.setAtUserIds(atUserIds);
        messageStore.insert(entity);
        messageMetrics.recordMessageSent();

        // 先落库拿 ACK,幂等 key 写入放在事务外也可接受:极端情况下重复请求由 afterSendMessage 回调的幂等处理兜底
        stringRedisTemplate.opsForValue().set(idempotentKey, String.valueOf(messageId),
                IDEMPOTENT_TTL.toMillis(), TimeUnit.MILLISECONDS);

        List<Long> recipients = recipientResolver.resolveRecipients(chatId, senderId, group);
        syncNotifier.onMessageSent(messageId, chatId, senderId, msgType, entity.getServerTime(), recipients);
        callbackInvoker.invoke(new AfterSendMessagePayload(
                messageId, chatId, senderId, msgType, entity.getServerTime()));

        for (Long recipientId : recipients) {
            pushPublisher.publish(recipientId, messageId, chatId, senderId, content, msgType, entity.getServerTime(), atUserIds);
        }
        // 在线推送(上面那个循环)只有真的在线才会投出去;不在线的那部分人在这里单独判断,
        // 触发离线推送(APNs/FCM 等厂商通道),两条路径互不依赖,见 OfflinePushTriggerService 说明。
        offlinePushTriggerService.triggerForOfflineRecipients(chatId, senderId, msgType, recipients);
        return entity;
    }
}
