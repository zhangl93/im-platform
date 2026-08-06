package com.im.platform.push.service;

import com.im.platform.msg.service.ConversationSettingService;
import com.im.platform.push.channel.OfflinePushDispatcher;
import com.im.platform.push.channel.OfflinePushPayload;
import com.im.platform.push.domain.PushPlatform;
import com.im.platform.push.mapper.PushTokenMapper;
import com.im.platform.status.service.StatusService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 离线推送触发规则:在线跳过、免打扰跳过、没有 token 自然不发,三条规则互相独立可以组合验证。 */
class OfflinePushTriggerServiceTest {

    private PushTokenMapper.PushTokenRow tokenRow(String deviceId, String platform, String token) {
        PushTokenMapper.PushTokenRow row = new PushTokenMapper.PushTokenRow();
        row.setDeviceId(deviceId);
        row.setPlatform(platform);
        row.setPushToken(token);
        return row;
    }

    @Test
    void onlineRecipient_neverDispatched() {
        StatusService statusService = mock(StatusService.class);
        ConversationSettingService conversationSettingService = mock(ConversationSettingService.class);
        PushTokenService pushTokenService = mock(PushTokenService.class);
        OfflinePushDispatcher dispatcher = mock(OfflinePushDispatcher.class);
        when(statusService.isOnline(1001L)).thenReturn(true);

        OfflinePushTriggerService service = new OfflinePushTriggerService(
                statusService, conversationSettingService, pushTokenService, dispatcher);
        service.triggerForOfflineRecipients(9001L, 2001L, 1, List.of(1001L));

        verify(conversationSettingService, never()).isMuted(anyLong(), anyLong());
        verify(dispatcher, never()).dispatch(any(), any(), any());
    }

    @Test
    void offlineButMuted_notDispatched() {
        StatusService statusService = mock(StatusService.class);
        ConversationSettingService conversationSettingService = mock(ConversationSettingService.class);
        PushTokenService pushTokenService = mock(PushTokenService.class);
        OfflinePushDispatcher dispatcher = mock(OfflinePushDispatcher.class);
        when(statusService.isOnline(1001L)).thenReturn(false);
        when(conversationSettingService.isMuted(1001L, 9001L)).thenReturn(true);

        OfflinePushTriggerService service = new OfflinePushTriggerService(
                statusService, conversationSettingService, pushTokenService, dispatcher);
        service.triggerForOfflineRecipients(9001L, 2001L, 1, List.of(1001L));

        verify(pushTokenService, never()).getTokens(anyLong());
        verify(dispatcher, never()).dispatch(any(), any(), any());
    }

    @Test
    void offlineNotMuted_dispatchesToEachRegisteredDevice() {
        StatusService statusService = mock(StatusService.class);
        ConversationSettingService conversationSettingService = mock(ConversationSettingService.class);
        PushTokenService pushTokenService = mock(PushTokenService.class);
        OfflinePushDispatcher dispatcher = mock(OfflinePushDispatcher.class);
        when(statusService.isOnline(1001L)).thenReturn(false);
        when(conversationSettingService.isMuted(1001L, 9001L)).thenReturn(false);
        when(pushTokenService.getTokens(1001L)).thenReturn(List.of(
                tokenRow("device-ios", "IOS", "apns-token"),
                tokenRow("device-android", "ANDROID", "fcm-token")));

        OfflinePushTriggerService service = new OfflinePushTriggerService(
                statusService, conversationSettingService, pushTokenService, dispatcher);
        service.triggerForOfflineRecipients(9001L, 2001L, 3, List.of(1001L));

        OfflinePushPayload expectedPayload = new OfflinePushPayload(9001L, 2001L, 3);
        verify(dispatcher).dispatch(eq(PushPlatform.IOS), eq("apns-token"), eq(expectedPayload));
        verify(dispatcher).dispatch(eq(PushPlatform.ANDROID), eq("fcm-token"), eq(expectedPayload));
    }

    @Test
    void offlineNotMuted_noRegisteredDevices_dispatchNeverCalled() {
        StatusService statusService = mock(StatusService.class);
        ConversationSettingService conversationSettingService = mock(ConversationSettingService.class);
        PushTokenService pushTokenService = mock(PushTokenService.class);
        OfflinePushDispatcher dispatcher = mock(OfflinePushDispatcher.class);
        when(statusService.isOnline(1001L)).thenReturn(false);
        when(conversationSettingService.isMuted(1001L, 9001L)).thenReturn(false);
        when(pushTokenService.getTokens(1001L)).thenReturn(List.of());

        OfflinePushTriggerService service = new OfflinePushTriggerService(
                statusService, conversationSettingService, pushTokenService, dispatcher);
        service.triggerForOfflineRecipients(9001L, 2001L, 1, List.of(1001L));

        verify(dispatcher, never()).dispatch(any(), any(), any());
    }
}
