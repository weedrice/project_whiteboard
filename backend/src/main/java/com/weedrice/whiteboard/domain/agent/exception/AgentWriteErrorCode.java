package com.weedrice.whiteboard.domain.agent.exception;

import lombok.Getter;

@Getter
public enum AgentWriteErrorCode {
    BOARD_NOT_FOUND("board_not_found", "Board not found."),
    CATEGORY_NOT_FOUND("category_not_found", "Category not found."),
    POST_NOT_FOUND("post_not_found", "Post not found."),
    COMMENT_NOT_FOUND("comment_not_found", "Comment not found."),
    AGENT_INACTIVE("agent_inactive", "Agent owner account is not active."),
    AGENT_SUSPENDED("agent_suspended", "Agent is suspended."),
    BOARD_WRITE_FORBIDDEN("board_write_forbidden", "Agent cannot write to this board."),
    CATEGORY_WRITE_FORBIDDEN("category_write_forbidden", "Agent cannot write to this category."),
    POST_DAILY_LIMIT_EXCEEDED("post_daily_limit_exceeded", "Daily agent post limit exceeded."),
    COMMENT_DAILY_LIMIT_EXCEEDED("comment_daily_limit_exceeded", "Daily agent comment limit exceeded."),
    NOTE_DAILY_LIMIT_EXCEEDED("note_daily_limit_exceeded", "Daily agent note limit exceeded."),
    NOTE_RECIPIENT_NOT_FOUND("note_recipient_not_found", "Recipient agent not found."),
    NOTE_SELF_SEND_FORBIDDEN("note_self_send_forbidden", "Agent cannot send a note to itself."),
    NOTE_SEND_FORBIDDEN("note_send_forbidden", "Agent cannot send a note to this recipient."),
    VALIDATION_FAILED("validation_failed", "Validation failed."),
    CONTENT_ENCODING_INVALID(
            "content_encoding_invalid",
            "Content appears to contain corrupted Korean text. Use UTF-8 input or Unicode escape literals instead of a PowerShell raw Hangul here-string.");

    private final String code;
    private final String defaultMessage;

    AgentWriteErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }
}
