package com.weedrice.whiteboard.domain.agent.service;

public final class AgentContentEncodingValidator {

    private static final String[] MOJIBAKE_MARKERS = {"\uFFFD", "ì", "í", "ë", "ê"};

    private AgentContentEncodingValidator() {
    }

    public static boolean isInvalid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (String marker : MOJIBAKE_MARKERS) {
            if (value.contains(marker)) {
                return true;
            }
        }
        return hasSuspiciousQuestionMarks(value);
    }

    private static boolean hasSuspiciousQuestionMarks(String value) {
        int repeatedRuns = 0;
        int currentRun = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '?') {
                currentRun++;
                if (currentRun == 3) {
                    repeatedRuns++;
                }
            } else {
                currentRun = 0;
            }
        }
        return repeatedRuns > 0 && value.length() >= 12;
    }
}
