package com.weedrice.whiteboard.domain.feed.controller;

import com.weedrice.whiteboard.domain.feed.dto.FeedGenerationRedriveResponse;
import com.weedrice.whiteboard.domain.feed.service.FeedGenerationJobService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AdminFeedGenerationControllerTest {

    @Test
    void redriveDelegatesAndReturnsPendingCanonicalState() {
        FeedGenerationJobService service = mock(FeedGenerationJobService.class);
        AdminFeedGenerationController controller = new AdminFeedGenerationController(service);

        ApiResponse<FeedGenerationRedriveResponse> response = controller.redrive(7L);

        verify(service).redrive(7L);
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getJobId()).isEqualTo(7L);
        assertThat(response.getData().getStatus()).isEqualTo("PENDING");
    }

    @Test
    void controllerRequiresSuperAdminRole() {
        PreAuthorize preAuthorize = AdminFeedGenerationController.class.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("hasRole('SUPER_ADMIN')");
    }
}
