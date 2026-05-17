package com.product.config.logging;

import java.util.List;
import java.util.regex.Pattern;

public final class LogMasker {

    private static final String MASK = "***";
    private static final List<MaskRule> RULES = List.of(
            new MaskRule(Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), MASK),
            new MaskRule(Pattern.compile("\\b01[016789]-?\\d{3,4}-?\\d{4}\\b"), MASK),
            new MaskRule(Pattern.compile("(?i)(authorization\\s*[:=]\\s*)(basic|bearer)\\s+[^\\s,}]+"), "$1$2 " + MASK),
            new MaskRule(Pattern.compile("(?i)(password\\s*[:=]\\s*)[^\\s,}]+"), "$1" + MASK)
    );

    private LogMasker() {
    }

    public static String mask(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        String masked = value;
        for (MaskRule rule : RULES) {
            masked = rule.pattern().matcher(masked).replaceAll(rule.replacement());
        }
        return masked;
    }

    private record MaskRule(Pattern pattern, String replacement) {
    }
}
