package com.weedrice.whiteboard.domain.notification.constant;

import com.weedrice.whiteboard.global.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationTypeTest {

    @Test
    void normalizeTrimsAndIgnoresCase() {
        assertThat(NotificationType.normalize(" comment "))
                .isEqualTo(NotificationType.COMMENT);
    }

    @Test
    void normalizeRejectsInvalidInputAsBusinessException() {
        assertThatThrownBy(() -> NotificationType.normalize("unknown"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void fromDatabaseValueReturnsNullForNullAndRejectsUnsupportedValues() {
        assertThat(NotificationType.fromDatabaseValue(null)).isNull();

        assertThatThrownBy(() -> NotificationType.fromDatabaseValue("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported notification type in database");
    }

    @Test
    void isSupportedUsesSameParsingRulesWithoutThrowing() {
        assertThat(NotificationType.isSupported(" reply ")).isTrue();
        assertThat(NotificationType.isSupported("unknown")).isFalse();
        assertThat(NotificationType.isSupported(null)).isFalse();
    }

    @Test
    void supportedTypeCountStaysAlignedWithEnumValues() {
        assertThat(NotificationType.SUPPORTED_TYPE_COUNT).isEqualTo(NotificationType.values().length);
    }
}
