package com.weedrice.whiteboard.domain.inquiry.legacy;

import com.weedrice.whiteboard.domain.board.constant.BoardPolicyConstants;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InquiryLegacyWritePolicy {
    private final GlobalConfigService globalConfigService;

    public void requireBoardWritable(Board board) {
        if (board != null && isInquiryBoard(board.getBoardUrl())) {
            requireLegacyWritesEnabled();
        }
    }

    public void requireLegacyWritesEnabled() {
        if (!areLegacyWritesEnabled()) {
            throw new BusinessException(ErrorCode.LEGACY_INQUIRY_READ_ONLY);
        }
    }

    public boolean areLegacyWritesEnabled() {
        String value = globalConfigService.getConfigFresh(
                GlobalConfigService.INQUIRY_LEGACY_WRITE_ENABLED_CONFIG_KEY);
        // Missing configuration preserves the pre-migration behavior for rollback safety.
        return value == null || "Y".equalsIgnoreCase(value.trim());
    }

    public void requireBoardReadable(Board board, User viewer) {
        if (isInquiryBoard(board)
                && !areLegacyWritesEnabled()
                && (viewer == null || !viewer.isUsableSuperAdmin())) {
            throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
        }
    }

    public boolean isInquiryBoard(Board board) {
        return board != null && isInquiryBoard(board.getBoardUrl());
    }

    private boolean isInquiryBoard(String boardUrl) {
        return boardUrl != null && BoardPolicyConstants.INQUIRY_BOARD_URL.equalsIgnoreCase(boardUrl.trim());
    }
}
