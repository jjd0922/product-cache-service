package com.product.application.common.failure;

public final class FailureReasonBuilder {

    private static final int DEFAULT_MAX_LENGTH = 500;

    private FailureReasonBuilder() {
    }

    public static String from(Throwable throwable) {
        return from(throwable, DEFAULT_MAX_LENGTH);
    }

    public static String from(Throwable throwable, int maxLength) {
        if (throwable == null) {
            return "Unknown error";
        }

        String message = throwable.getMessage();
        String reason = (message == null || message.isBlank())
                ? throwable.getClass().getSimpleName()
                : throwable.getClass().getSimpleName() + ": " + message.strip();

        return truncate(reason, maxLength);
    }

    private static String truncate(String reason, int maxLength) {
        if (maxLength < 1 || reason.length() <= maxLength) {
            return reason;
        }

        return reason.substring(0, maxLength);
    }
}
