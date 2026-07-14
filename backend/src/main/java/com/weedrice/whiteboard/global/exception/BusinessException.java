package com.weedrice.whiteboard.global.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String messageKey;
    private final Object[] messageArguments;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.messageKey = null;
        this.messageArguments = new Object[0];
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.messageKey = null;
        this.messageArguments = new Object[0];
    }

    private BusinessException(ErrorCode errorCode, String messageKey, Object[] messageArguments) {
        super(messageKey);
        this.errorCode = errorCode;
        this.messageKey = messageKey;
        this.messageArguments = messageArguments == null ? new Object[0] : messageArguments.clone();
    }

    public static BusinessException withMessageKey(ErrorCode errorCode, String messageKey, Object... messageArguments) {
        return new BusinessException(errorCode, messageKey, messageArguments);
    }

    public Object[] getMessageArguments() {
        return messageArguments.clone();
    }
}
