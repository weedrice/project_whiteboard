package com.weedrice.whiteboard.domain.inquiry.legacy;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InquiryLegacyWritePolicyTest {

    private final GlobalConfigService globalConfigService = mock(GlobalConfigService.class);
    private final InquiryLegacyWritePolicy policy = new InquiryLegacyWritePolicy(globalConfigService);

    @Test
    void readsCutoverFlagWithoutUsingLocalCache() {
        when(globalConfigService.getConfigFresh(
                GlobalConfigService.INQUIRY_LEGACY_WRITE_ENABLED_CONFIG_KEY)).thenReturn("N");

        assertThat(policy.areLegacyWritesEnabled()).isFalse();

        verify(globalConfigService).getConfigFresh(
                GlobalConfigService.INQUIRY_LEGACY_WRITE_ENABLED_CONFIG_KEY);
        verify(globalConfigService, never()).getConfig(
                GlobalConfigService.INQUIRY_LEGACY_WRITE_ENABLED_CONFIG_KEY);
    }

    @Test
    void hidesArchivedInquiryBoardFromNonSuperAdmin() {
        when(globalConfigService.getConfigFresh(
                GlobalConfigService.INQUIRY_LEGACY_WRITE_ENABLED_CONFIG_KEY)).thenReturn("N");
        Board inquiryBoard = Board.builder().boardUrl("inquiry").build();
        User viewer = mock(User.class);

        assertThatThrownBy(() -> policy.requireBoardReadable(inquiryBoard, viewer))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BOARD_NOT_FOUND);
    }

    @Test
    void allowsSuperAdminToReadArchivedInquiryBoard() {
        when(globalConfigService.getConfigFresh(
                GlobalConfigService.INQUIRY_LEGACY_WRITE_ENABLED_CONFIG_KEY)).thenReturn("N");
        Board inquiryBoard = Board.builder().boardUrl("inquiry").build();
        User viewer = mock(User.class);
        when(viewer.isUsableSuperAdmin()).thenReturn(true);

        assertThatCode(() -> policy.requireBoardReadable(inquiryBoard, viewer)).doesNotThrowAnyException();
    }
}
