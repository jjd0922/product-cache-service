package com.product.application.common.failure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FailureReasonBuilderTest {

    @Test
    void from_whenThrowableIsNull_thenReturnUnknownError() {
        assertThat(FailureReasonBuilder.from(null)).isEqualTo("Unknown error");
    }

    @Test
    void from_whenMessageExists_thenReturnClassNameAndMessage() {
        RuntimeException exception = new RuntimeException("redis down");

        assertThat(FailureReasonBuilder.from(exception))
                .isEqualTo("RuntimeException: redis down");
    }

    @Test
    void from_whenMessageIsBlank_thenReturnClassNameOnly() {
        RuntimeException exception = new RuntimeException(" ");

        assertThat(FailureReasonBuilder.from(exception))
                .isEqualTo("RuntimeException");
    }

    @Test
    void from_whenReasonIsTooLong_thenTruncate() {
        RuntimeException exception = new RuntimeException("1234567890");

        assertThat(FailureReasonBuilder.from(exception, 15))
                .isEqualTo("RuntimeExceptio");
    }
}
