package com.weedrice.whiteboard.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "Invalid Input Value"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C002", "Method Not Allowed"),
    HANDLE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "C003", "Access Denied"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "C004", "Unauthorized"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C005", "Server Error"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "C006", "Not Found"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "C007", "Forbidden"),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "C008", "Validation Error"),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "C009", "Duplicate Resource"),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "User Not Found"),
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "U002", "Duplicate Login ID"),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "U003", "Duplicate Email"),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "U004", "Invalid Password"),
    INVALID_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "U005", "Invalid Current Password"),
    PASSWORD_RECENTLY_USED(HttpStatus.BAD_REQUEST, "U006", "Password Recently Used"),
    ALREADY_BLOCKED(HttpStatus.CONFLICT, "U007", "Already Blocked"),
    CANNOT_BLOCK_SELF(HttpStatus.BAD_REQUEST, "U008", "Cannot Block Self"),
    BLOCKED_BY_USER(HttpStatus.FORBIDDEN, "U009", "Blocked by User"),

    // Board
    BOARD_NOT_FOUND(HttpStatus.NOT_FOUND, "B001", "Board Not Found"),
    DUPLICATE_BOARD_NAME(HttpStatus.CONFLICT, "B002", "Duplicate Board Name"),
    ALREADY_SUBSCRIBED(HttpStatus.CONFLICT, "B003", "Already Subscribed"),
    NOT_SUBSCRIBED(HttpStatus.BAD_REQUEST, "B004", "Not Subscribed"),

    // Post
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "P001", "Post Not Found"),
    ALREADY_SCRAPED(HttpStatus.CONFLICT, "P002", "Already Scraped"),
    NOT_SCRAPED(HttpStatus.BAD_REQUEST, "P003", "Not Scraped"),

    // Comment
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "C001", "Comment Not Found"),

    // Point
    INSUFFICIENT_POINTS(HttpStatus.BAD_REQUEST, "PT001", "Insufficient Points"),
    ITEM_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "I001", "판매 중단된 아이템입니다"),

    // Auth
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "A001", "Invalid Refresh Token"),
    EXPIRED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "A002", "Expired Refresh Token"),

    // Report
    ALREADY_REPORTED(HttpStatus.BAD_REQUEST, "R001", "이미 신고한 대상입니다"),
    INVALID_TARGET(HttpStatus.BAD_REQUEST, "R002", "유효하지 않은 신고 대상입니다"),

    // File
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "F001", "파일 크기가 너무 큽니다"),
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "F002", "허용되지 않은 파일 형식입니다");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
