package com.im.platform.push.service;

import com.im.platform.push.domain.PushPlatform;
import com.im.platform.push.mapper.PushTokenMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PushTokenServiceTest {

    @Test
    void register_upsertsWithPlatformName() {
        PushTokenMapper mapper = mock(PushTokenMapper.class);
        PushTokenService service = new PushTokenService(mapper);

        service.register(1001L, "device-a", PushPlatform.ANDROID, "fcm-token-abc");

        verify(mapper).upsert(eq(1001L), eq("device-a"), eq("ANDROID"), eq("fcm-token-abc"), anyLong());
    }

    @Test
    void unregister_deletesByUserAndDevice() {
        PushTokenMapper mapper = mock(PushTokenMapper.class);
        PushTokenService service = new PushTokenService(mapper);

        service.unregister(1001L, "device-a");

        verify(mapper).delete(1001L, "device-a");
    }

    @Test
    void getTokens_returnsRowsFromMapper() {
        PushTokenMapper mapper = mock(PushTokenMapper.class);
        PushTokenMapper.PushTokenRow row = new PushTokenMapper.PushTokenRow();
        row.setDeviceId("device-a");
        row.setPlatform("IOS");
        row.setPushToken("apns-token");
        when(mapper.selectAllForUser(1001L)).thenReturn(List.of(row));

        PushTokenService service = new PushTokenService(mapper);
        List<PushTokenMapper.PushTokenRow> result = service.getTokens(1001L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPushToken()).isEqualTo("apns-token");
    }
}
