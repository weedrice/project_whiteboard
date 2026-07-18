package com.weedrice.whiteboard.domain.point.entity;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;

import java.util.Locale;

public enum PointHistoryType {
    EARN,
    SPEND,
    EXPIRE,
    PENALTY,
    REWARD_REVERSAL;

    public static PointHistoryType fromWireValue(String value) {
        try {
            return PointHistoryType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
