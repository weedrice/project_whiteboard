package com.weedrice.whiteboard.global.log.controller;

import tools.jackson.databind.ObjectMapper;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.util.ClientIpResolver;
import com.weedrice.whiteboard.global.log.dto.ClientErrorLogRequest;
import com.weedrice.whiteboard.global.log.service.ErrorLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientErrorLogControllerTest {

    @Mock
    private ErrorLogService errorLogService;

    @Mock
    private ClientIpResolver clientIpResolver;

    @Mock
    private HttpServletRequest servletRequest;

    @InjectMocks
    private ClientErrorLogController controller;

    @Test
    void collectClientError_usesServerRequestMetadataForAnonymousCaller() throws Exception {
        ClientErrorLogRequest request = new ObjectMapper().readValue("""
                {
                  "source": "WINDOW_ERROR",
                  "errorType": "TypeError",
                  "message": "render failed",
                  "pageUrl": "/boards/test",
                  "commitHash": "abc123"
                }
                """, ClientErrorLogRequest.class);
        when(clientIpResolver.resolve(servletRequest)).thenReturn("203.0.113.10");
        when(servletRequest.getHeader("User-Agent")).thenReturn("Test Browser");

        ApiResponse<Void> response = controller.collectClientError(request, null, servletRequest);

        assertThat(response).isNotNull();
        verify(errorLogService).saveClientErrorLog(
                request,
                null,
                "203.0.113.10",
                "Test Browser");
    }
}
