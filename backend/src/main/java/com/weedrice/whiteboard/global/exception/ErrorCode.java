package com.weedrice.whiteboard.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "error.common.invalidInput"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C002", "error.common.methodNotAllowed"),
    HANDLE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "C003", "error.common.accessDenied"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "C004", "error.common.unauthorized"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C005", "error.common.serverError"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "C006", "error.common.notFound"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "C007", "error.common.forbidden"),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "C008", "error.common.validationError"),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "C009", "error.common.duplicateResource"),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "C010", "error.common.rateLimitExceeded"),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "error.user.notFound"),
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "U002", "error.user.duplicateLoginId"),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "U003", "error.user.duplicateEmail"),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "U004", "error.user.invalidPassword"),
    INVALID_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "U005", "error.user.invalidCurrentPassword"),
    PASSWORD_RECENTLY_USED(HttpStatus.BAD_REQUEST, "U006", "error.user.passwordRecentlyUsed"),
    ALREADY_BLOCKED(HttpStatus.CONFLICT, "U007", "error.user.alreadyBlocked"),
    CANNOT_BLOCK_SELF(HttpStatus.BAD_REQUEST, "U008", "error.user.cannotBlockSelf"),
    BLOCKED_BY_USER(HttpStatus.FORBIDDEN, "U009", "error.user.blockedByUser"),
    USER_NOT_ACTIVE(HttpStatus.FORBIDDEN, "U010", "error.user.notActive"),
    EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "U011", "error.user.emailNotVerified"),

    // Board
    BOARD_NOT_FOUND(HttpStatus.NOT_FOUND, "B001", "error.board.notFound"),
    DUPLICATE_BOARD_NAME(HttpStatus.CONFLICT, "B002", "error.board.duplicateName"),
    ALREADY_SUBSCRIBED(HttpStatus.CONFLICT, "B003", "error.board.alreadySubscribed"),
    NOT_SUBSCRIBED(HttpStatus.BAD_REQUEST, "B004", "error.board.notSubscribed"),
    DUPLICATE_BOARD_URL(HttpStatus.CONFLICT, "B005", "error.board.duplicateUrl"),

    // Post
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "P001", "error.post.notFound"),
    ALREADY_SCRAPED(HttpStatus.CONFLICT, "P002", "error.post.alreadyScraped"),
    NOT_SCRAPED(HttpStatus.BAD_REQUEST, "P003", "error.post.notScraped"),

    // Comment
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "C001", "error.comment.notFound"),

    // Point
    INSUFFICIENT_POINTS(HttpStatus.BAD_REQUEST, "PT001", "error.point.insufficientPoints"),
    ITEM_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "I001", "error.point.itemNotAvailable"),

    // Auth
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "A001", "error.auth.invalidRefreshToken"),
    EXPIRED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "A002", "error.auth.expiredRefreshToken"),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "A003", "error.auth.loginFailed"),
    INVALID_PASSWORD_RESET_TOKEN(HttpStatus.BAD_REQUEST, "A004", "error.auth.invalidPasswordResetToken"),
    EXPIRED_PASSWORD_RESET_TOKEN(HttpStatus.BAD_REQUEST, "A005", "error.auth.expiredPasswordResetToken"),
    USED_PASSWORD_RESET_TOKEN(HttpStatus.BAD_REQUEST, "A006", "error.auth.usedPasswordResetToken"),

    // Report
    ALREADY_REPORTED(HttpStatus.BAD_REQUEST, "R001", "error.report.alreadyReported"),
    INVALID_TARGET(HttpStatus.BAD_REQUEST, "R002", "error.report.invalidTarget"),

    // Ad
    AD_NOT_FOUND(HttpStatus.NOT_FOUND, "AD001", "error.ad.notFound"),

    // IP Block
    IP_BLOCKED(HttpStatus.FORBIDDEN, "IP001", "error.ip.blocked"),

    // Like
    ALREADY_LIKED(HttpStatus.BAD_REQUEST, "L001", "error.like.alreadyLiked"),
    NOT_LIKED(HttpStatus.BAD_REQUEST, "L002", "error.like.notLiked"),

    // File
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "F001", "error.file.tooLarge"),
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "F002", "error.file.invalidType"),
    FILE_EMPTY(HttpStatus.BAD_REQUEST, "F003", "error.file.empty"),
    FILE_UPLOAD_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "F004", "error.file.uploadError"),
    FILE_LOAD_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "F005", "error.file.loadError"),

    // Email
    EMAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "E001", "error.email.sendFailed");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
