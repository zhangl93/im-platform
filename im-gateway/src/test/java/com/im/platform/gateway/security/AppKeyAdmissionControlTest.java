package com.im.platform.gateway.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppKeyAdmissionControlTest {

    @Test
    void emptyWhitelist_disabledByDefault_allowsAnything() {
        AppKeyAdmissionControl control = new AppKeyAdmissionControl("");

        assertThat(control.isEnabled()).isFalse();
        assertThat(control.isAllowed(null)).isTrue();
        assertThat(control.isAllowed("")).isTrue();
        assertThat(control.isAllowed("anything")).isTrue();
    }

    @Test
    void nonEmptyWhitelist_onlyMatchingKeysAllowed() {
        AppKeyAdmissionControl control = new AppKeyAdmissionControl("key-a, key-b");

        assertThat(control.isEnabled()).isTrue();
        assertThat(control.isAllowed("key-a")).isTrue();
        assertThat(control.isAllowed("key-b")).isTrue();
        assertThat(control.isAllowed("key-c")).isFalse();
        assertThat(control.isAllowed(null)).isFalse();
        assertThat(control.isAllowed("")).isFalse();
    }
}
