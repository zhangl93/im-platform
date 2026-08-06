package com.im.platform.push.channel;

import com.im.platform.push.domain.PushPlatform;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfflinePushDispatcherTest {

    @Test
    void dispatch_routesToChannelMatchingPlatform() {
        OfflinePushChannel iosChannel = mock(OfflinePushChannel.class);
        when(iosChannel.platform()).thenReturn(PushPlatform.IOS);
        OfflinePushChannel androidChannel = mock(OfflinePushChannel.class);
        when(androidChannel.platform()).thenReturn(PushPlatform.ANDROID);

        OfflinePushDispatcher dispatcher = new OfflinePushDispatcher(List.of(iosChannel, androidChannel));
        OfflinePushPayload payload = new OfflinePushPayload(9001L, 1001L, 1);
        dispatcher.dispatch(PushPlatform.ANDROID, "fcm-token", payload);

        verify(androidChannel).push(eq("fcm-token"), eq(payload));
        verify(iosChannel, never()).push(any(), any());
    }

    @Test
    void dispatch_noChannelForPlatform_doesNotThrow() {
        OfflinePushChannel iosChannel = mock(OfflinePushChannel.class);
        when(iosChannel.platform()).thenReturn(PushPlatform.IOS);

        OfflinePushDispatcher dispatcher = new OfflinePushDispatcher(List.of(iosChannel));
        // ANDROID 没有注册任何 channel,不应该抛异常,静默跳过
        dispatcher.dispatch(PushPlatform.ANDROID, "token", new OfflinePushPayload(1L, 2L, 1));

        verify(iosChannel, never()).push(any(), any());
    }
}
