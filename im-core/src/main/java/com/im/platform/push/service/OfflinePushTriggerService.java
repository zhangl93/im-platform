package com.im.platform.push.service;

import com.im.platform.msg.service.ConversationSettingService;
import com.im.platform.push.channel.OfflinePushDispatcher;
import com.im.platform.push.channel.OfflinePushPayload;
import com.im.platform.push.domain.PushPlatform;
import com.im.platform.push.mapper.PushTokenMapper;
import com.im.platform.status.service.StatusService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 消息发送成功后,给"接收者不在线"的那部分人触发离线推送(APNs/FCM 等厂商通道)——
 * 跟 PushPublisher 走的在线推送(Redis Pub/Sub,由各 gateway 实例的 ChannelRegistry 决定
 * 要不要真推)是两条独立路径,不互相依赖:一个用户可能在线又同时有别的离线设备
 * (比如手机端在线、平板端不在线),这里只按"这台设备有没有 token、这个人在不在线"
 * 独立判断,不去猜"是不是所有端都在线"这种更复杂的语义。
 *
 * 免打扰(conversation setting 的 muted)在这里拦截——用户主动设置了这个会话免打扰,
 * 说明不想被系统通知打扰,离线推送也不该发;在线推送(PushPublisher)不做这个判断,
 * 因为那条路径只有真的在线才会投递,是用户自己app内的事,跟系统通知栏无关。
 */
@Service
public class OfflinePushTriggerService {

    private final StatusService statusService;
    private final ConversationSettingService conversationSettingService;
    private final PushTokenService pushTokenService;
    private final OfflinePushDispatcher dispatcher;

    public OfflinePushTriggerService(StatusService statusService,
                                      ConversationSettingService conversationSettingService,
                                      PushTokenService pushTokenService,
                                      OfflinePushDispatcher dispatcher) {
        this.statusService = statusService;
        this.conversationSettingService = conversationSettingService;
        this.pushTokenService = pushTokenService;
        this.dispatcher = dispatcher;
    }

    public void triggerForOfflineRecipients(long chatId, long senderId, int msgType, List<Long> recipientIds) {
        for (Long recipientId : recipientIds) {
            if (statusService.isOnline(recipientId)) {
                continue; // 在线交给 PushPublisher 那条实时路径,不重复触发系统通知
            }
            if (conversationSettingService.isMuted(recipientId, chatId)) {
                continue; // 用户自己设置了这个会话免打扰
            }
            List<PushTokenMapper.PushTokenRow> tokens = pushTokenService.getTokens(recipientId);
            OfflinePushPayload payload = new OfflinePushPayload(chatId, senderId, msgType);
            for (PushTokenMapper.PushTokenRow token : tokens) {
                dispatcher.dispatch(PushPlatform.valueOf(token.getPlatform()), token.getPushToken(), payload);
            }
        }
    }
}
