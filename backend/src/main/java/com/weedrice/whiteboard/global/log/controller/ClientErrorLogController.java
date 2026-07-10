package com.weedrice.whiteboard.global.log.controller;

import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.ApiResponses;
import com.weedrice.whiteboard.global.common.util.ClientIpResolver;
import com.weedrice.whiteboard.global.log.dto.ClientErrorLogRequest;
import com.weedrice.whiteboard.global.log.service.ErrorLogService;
import com.weedrice.whiteboard.global.security.AuthenticatedUserResolver;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
public class ClientErrorLogController {

    private final ErrorLogService errorLogService;
    private final ClientIpResolver clientIpResolver;

    @PostMapping(value = "/client", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Void> collectClientError(
            @Valid @RequestBody ClientErrorLogRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest servletRequest) {
        errorLogService.saveClientErrorLog(
                request,
                AuthenticatedUserResolver.optionalUserId(userDetails),
                clientIpResolver.resolve(servletRequest),
                servletRequest.getHeader("User-Agent"));
        return ApiResponses.ok();
    }
}
