package com.weedrice.whiteboard.domain.search.controller;

import com.weedrice.whiteboard.domain.search.semantic.SemanticSearchBackfillService;
import com.weedrice.whiteboard.domain.search.semantic.SemanticSearchReindexRedriveResponse;
import com.weedrice.whiteboard.domain.search.semantic.SemanticSearchReindexService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AdminSemanticSearchControllerTest {

    private final SemanticSearchBackfillService backfillService =
            mock(SemanticSearchBackfillService.class);
    private final SemanticSearchReindexService reindexService =
            mock(SemanticSearchReindexService.class);
    private final AdminSemanticSearchController controller =
            new AdminSemanticSearchController(backfillService, reindexService);

    @Test
    void redriveDelegatesAndReturnsPendingState() {
        ApiResponse<SemanticSearchReindexRedriveResponse> response =
                controller.redriveReindexJob(17L);

        verify(reindexService).redrive(17L);
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getJobId()).isEqualTo(17L);
        assertThat(response.getData().getStatus()).isEqualTo("PENDING");
    }

    @Test
    void controllerRequiresSuperAdminRole() {
        PreAuthorize annotation = AdminSemanticSearchController.class
                .getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasRole('SUPER_ADMIN')");
    }
}

