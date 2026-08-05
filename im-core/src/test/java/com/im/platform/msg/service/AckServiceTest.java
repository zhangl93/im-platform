package com.im.platform.msg.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AckServiceTest {

    private StringRedisTemplate stringRedisTemplate;
    private SetOperations<String, String> setOperations;
    private AckService ackService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        stringRedisTemplate = mock(StringRedisTemplate.class);
        setOperations = mock(SetOperations.class);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        ackService = new AckService(stringRedisTemplate);
    }

    @Test
    void ack_addsMessageIdToUserSet_andSetsExpiry() {
        ackService.ack(1001L, 555L);

        verify(setOperations).add("im:ack:1001", "555");
        verify(stringRedisTemplate).expire(eq("im:ack:1001"), eq(Duration.ofDays(7)));
    }

    @Test
    void isAcked_whenRedisSetContainsMessageId_returnsTrue() {
        when(setOperations.isMember("im:ack:1001", "555")).thenReturn(true);

        assertThat(ackService.isAcked(1001L, 555L)).isTrue();
    }

    @Test
    void isAcked_whenNotAMember_returnsFalse() {
        when(setOperations.isMember(anyString(), anyString())).thenReturn(false);

        assertThat(ackService.isAcked(1001L, 555L)).isFalse();
    }

    @Test
    void isAcked_whenRedisReturnsNull_treatedAsFalse_notNpe() {
        when(setOperations.isMember(anyString(), anyString())).thenReturn(null);

        assertThat(ackService.isAcked(1001L, 555L)).isFalse();
    }

    @Test
    void differentUsers_haveIndependentAckKeys() {
        ackService.ack(1001L, 1L);
        ackService.ack(2002L, 1L);

        verify(setOperations).add("im:ack:1001", "1");
        verify(setOperations).add("im:ack:2002", "1");
    }
}
