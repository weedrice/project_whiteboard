package com.weedrice.whiteboard.domain.notification.constant;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class NotificationTypeConverter implements AttributeConverter<NotificationType, String> {

    @Override
    public String convertToDatabaseColumn(NotificationType attribute) {
        return attribute != null ? attribute.name() : null;
    }

    @Override
    public NotificationType convertToEntityAttribute(String dbData) {
        return NotificationType.fromDatabaseValue(dbData);
    }
}
