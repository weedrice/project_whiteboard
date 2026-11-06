package com.weedrice.whiteboard.domain.agent.service;

public final class AgentContentEncodingValidator {
    private AgentContentEncodingValidator() {
    }

    public static boolean isInvalid(String value) {
        if (value == null) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '\uFFFD') {
                return true;
            }
            if (Character.isHighSurrogate(current)) {
                if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(i + 1))) {
                    return true;
                }
                i++;
            } else if (Character.isLowSurrogate(current)) {
                return true;
            }
        }
        return false;
    }
}
