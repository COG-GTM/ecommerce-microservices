package com.ibatulanand.notificationservice.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PiiMaskerTest {

    @Test
    void masksEmail() {
        assertThat(PiiMasker.mask("jordan.lee@example.com")).isEqualTo("j***@e***.com");
    }

    @Test
    void masksPhoneToLastFour() {
        assertThat(PiiMasker.mask("+1 (415) 555-0142")).isEqualTo("***-***-0142");
        assertThat(PiiMasker.mask("4155550142")).isEqualTo("***-***-0142");
    }

    @Test
    void handlesBlankAndShortValues() {
        assertThat(PiiMasker.mask(null)).isEqualTo("<none>");
        assertThat(PiiMasker.mask(" ")).isEqualTo("<none>");
        assertThat(PiiMasker.mask("12")).isEqualTo("***");
    }
}
