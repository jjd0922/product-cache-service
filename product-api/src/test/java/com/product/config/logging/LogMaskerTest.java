package com.product.config.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogMaskerTest {

    @Test
    void mask_masksEmailPhoneAuthorizationAndPassword() {
        String actual = LogMasker.mask(
                "email=user@example.com phone=010-1234-5678 Authorization: Basic abc123 password=secret"
        );

        assertThat(actual).doesNotContain("user@example.com");
        assertThat(actual).doesNotContain("010-1234-5678");
        assertThat(actual).doesNotContain("abc123");
        assertThat(actual).doesNotContain("secret");
        assertThat(actual).contains("email=***");
        assertThat(actual).contains("phone=***");
        assertThat(actual).contains("Authorization: Basic ***");
        assertThat(actual).contains("password=***");
    }
}
