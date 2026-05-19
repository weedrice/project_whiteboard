package com.weedrice.whiteboard.domain.user.web;

import com.weedrice.whiteboard.domain.user.dto.MessageResponse;
import com.weedrice.whiteboard.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserActionResponseFactory {

    private static final String PASSWORD_CHANGED_MESSAGE = "success.user.passwordChanged";
    private static final String ACCOUNT_DELETED_MESSAGE = "success.user.accountDeleted";
    private static final String BLOCKED_MESSAGE = "success.user.blocked";
    private static final String UNBLOCKED_MESSAGE = "success.user.unblocked";

    private final MessageSource messageSource;

    public ResponseEntity<ApiResponse<MessageResponse>> passwordChanged() {
        return ok(PASSWORD_CHANGED_MESSAGE);
    }

    public ResponseEntity<ApiResponse<MessageResponse>> accountDeleted() {
        return ok(ACCOUNT_DELETED_MESSAGE);
    }

    public ResponseEntity<ApiResponse<MessageResponse>> blocked() {
        return status(HttpStatus.CREATED, BLOCKED_MESSAGE);
    }

    public ResponseEntity<ApiResponse<MessageResponse>> unblocked() {
        return ok(UNBLOCKED_MESSAGE);
    }

    private ResponseEntity<ApiResponse<MessageResponse>> ok(String messageCode) {
        return status(HttpStatus.OK, messageCode);
    }

    private ResponseEntity<ApiResponse<MessageResponse>> status(HttpStatus status, String messageCode) {
        String message = messageSource.getMessage(messageCode, null, LocaleContextHolder.getLocale());
        return ResponseEntity.status(status)
                .body(ApiResponse.success(new MessageResponse(message)));
    }
}
