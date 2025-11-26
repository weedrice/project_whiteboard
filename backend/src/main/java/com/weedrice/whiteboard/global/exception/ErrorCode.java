package com.weedrice.whiteboard.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "권한이 없습니다"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "리소스를 찾을 수 없습니다"),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "입력값이 올바르지 않습니다"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 오류가 발생했습니다"),

    // User/Auth
    DUPLICATE_LOGIN_ID(HttpStatus.BAD_REQUEST, "DUPLICATE_LOGIN_ID", "이미 존재하는 로그인 ID입니다"),
    DUPLICATE_EMAIL(HttpStatus.BAD_REQUEST, "DUPLICATE_EMAIL", "이미 존재하는 이메일입니다"),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "비밀번호 형식이 올바르지 않습니다"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "로그인 정보가 일치하지 않습니다"),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED", "정지된 계정입니다"),
    ACCOUNT_DELETED(HttpStatus.FORBIDDEN, "ACCOUNT_DELETED", "탈퇴한 계정입니다"),
    INVALID_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "INVALID_CURRENT_PASSWORD", "현재 비밀번호가 일치하지 않습니다"),
    PASSWORD_RECENTLY_USED(HttpStatus.BAD_REQUEST, "PASSWORD_RECENTLY_USED", "최근에 사용한 비밀번호입니다"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "유효하지 않은 리프레시 토큰입니다"),
    EXPIRED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "EXPIRED_REFRESH_TOKEN", "만료된 리프레시 토큰입니다"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다"),
    CANNOT_BLOCK_SELF(HttpStatus.BAD_REQUEST, "CANNOT_BLOCK_SELF", "자기 자신을 차단할 수 없습니다"),
    ALREADY_BLOCKED(HttpStatus.BAD_REQUEST, "ALREADY_BLOCKED", "이미 차단된 사용자입니다"),
    BLOCKED_BY_USER(HttpStatus.FORBIDDEN, "BLOCKED_BY_USER", "상대방에게 차단되었습니다"),

    // Board
    DUPLICATE_BOARD_NAME(HttpStatus.BAD_REQUEST, "DUPLICATE_BOARD_NAME", "이미 존재하는 게시판 이름입니다"),
    BOARD_NOT_FOUND(HttpStatus.NOT_FOUND, "BOARD_NOT_FOUND", "게시판을 찾을 수 없습니다"),
    ALREADY_SUBSCRIBED(HttpStatus.BAD_REQUEST, "ALREADY_SUBSCRIBED", "이미 구독한 게시판입니다"),
    NOT_SUBSCRIBED(HttpStatus.BAD_REQUEST, "NOT_SUBSCRIBED", "구독하지 않은 게시판입니다"),

    // Post
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "게시글을 찾을 수 없습니다"),
    ALREADY_SCRAPED(HttpStatus.BAD_REQUEST, "ALREADY_SCRAPED", "이미 스크랩한 게시글입니다"),
    NOT_SCRAPED(HttpStatus.BAD_REQUEST, "NOT_SCRAPED", "스크랩하지 않은 게시글입니다"),

    // Comment
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND", "댓글을 찾을 수 없습니다"),

    // Report
    ALREADY_REPORTED(HttpStatus.BAD_REQUEST, "ALREADY_REPORTED", "이미 신고한 대상입니다"),
    INVALID_TARGET(HttpStatus.BAD_REQUEST, "INVALID_TARGET", "유효하지 않은 신고 대상입니다"),

    // File
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE", "파일 크기가 너무 큽니다"),
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "INVALID_FILE_TYPE", "허용되지 않은 파일 형식입니다"),

    // Point/Shop
    INSUFFICIENT_POINTS(HttpStatus.BAD_REQUEST, "INSUFFICIENT_POINTS", "포인트가 부족합니다"),
    ITEM_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "ITEM_NOT_AVAILABLE", "판매 중단된 아이템입니다");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
