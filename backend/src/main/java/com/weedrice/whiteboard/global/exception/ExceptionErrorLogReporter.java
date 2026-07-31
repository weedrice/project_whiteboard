package com.weedrice.whiteboard.global.exception;

import com.weedrice.whiteboard.global.common.util.ClientIpResolver;
import com.weedrice.whiteboard.global.log.service.ErrorLogService;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.PrintWriter;
import java.io.StringWriter;

@Slf4j
@RequiredArgsConstructor
public class ExceptionErrorLogReporter {

    private final ObjectProvider<ClientIpResolver> clientIpResolverProvider;
    private final ObjectProvider<ErrorLogService> errorLogServiceProvider;

    public void report(
            String errorCode,
            String errorType,
            int httpStatus,
            String message,
            HttpServletRequest request,
            String stackTrace) {
        try {
            ErrorLogService errorLogService = errorLogServiceProvider.getIfAvailable();
            if (errorLogService == null) {
                return;
            }
            ClientIpResolver clientIpResolver = clientIpResolverProvider.getIfAvailable();
            String ipAddress = clientIpResolver != null
                    ? clientIpResolver.resolve(request)
                    : request.getRemoteAddr();
            errorLogService.saveErrorLog(
                    errorCode,
                    errorType,
                    httpStatus,
                    message,
                    request.getRequestURI(),
                    request.getMethod(),
                    currentUserId(),
                    ipAddress,
                    request.getHeader("User-Agent"),
                    stackTrace);
        } catch (Exception exception) {
            log.error("Failed to save error log to DB", exception);
        }
    }

    public String stackTrace(Exception exception) {
        StringWriter writer = new StringWriter();
        exception.printStackTrace(new PrintWriter(writer));
        String trace = writer.toString();
        return trace.length() > 4000 ? trace.substring(0, 4000) : trace;
    }

    private Long currentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null
                    && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
                return userDetails.getUserId();
            }
        } catch (Exception ignored) {
            // Authentication is optional for error reporting.
        }
        return null;
    }
}
