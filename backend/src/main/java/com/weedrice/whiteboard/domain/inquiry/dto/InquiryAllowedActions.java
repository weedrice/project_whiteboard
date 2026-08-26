package com.weedrice.whiteboard.domain.inquiry.dto;

public record InquiryAllowedActions(
        boolean canAddMessage,
        boolean canWithdraw,
        boolean canClose) {
}
