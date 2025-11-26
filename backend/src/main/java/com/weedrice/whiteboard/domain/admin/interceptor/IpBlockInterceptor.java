package com.weedrice.whiteboard.domain.admin.interceptor;

import com.weedrice.whiteboard.domain.admin.service.AdminService;
import com.weedrice.whiteboard.global.common.util.ClientUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class IpBlockInterceptor implements HandlerInterceptor {

    private final AdminService adminService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String clientIp = ClientUtils.getIp(request);
        if (adminService.isIpBlocked(clientIp)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "차단된 IP 주소입니다.");
        }
        return true;
    }
}
