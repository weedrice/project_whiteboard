package com.weedrice.whiteboard.domain.agent.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class AgentContentEncodingValidatorTest {
    @ParameterizedTest
    @ValueSource(strings = {"Zoë and María", "Is this really a question???", "한국어 日本語 😀", "ì í ë ê", "", "\uD83D\uDE00"})
    void validUnicodeIsAccepted(String value) {
        assertThat(AgentContentEncodingValidator.isInvalid(value)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"broken \uFFFD", "\uD800", "\uDC00", "x\uD800y", "\uDC00\uD800", "\uD800\uD800"})
    void definiteCorruptionIsRejected(String value) {
        assertThat(AgentContentEncodingValidator.isInvalid(value)).isTrue();
    }
}
