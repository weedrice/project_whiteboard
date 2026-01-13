package com.weedrice.whiteboard.domain.admin.interceptor;

import com.weedrice.whiteboard.domain.admin.service.AdminService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IpBlockInterceptorTest {

    @InjectMocks
    private IpBlockInterceptor ipBlockInterceptor;

    @Mock
    private AdminService adminService;

    @Test
    @DisplayName("차단되지 않은 IP 접근 허용")
    void preHandle_allowed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        when(adminService.isIpBlocked(anyString())).thenReturn(false);

        boolean result = ipBlockInterceptor.preHandle(request, response, new Object());
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("차단된 IP 접근 거부")
    void preHandle_blocked() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        when(adminService.isIpBlocked(anyString())).thenReturn(true);

        assertThatThrownBy(() -> ipBlockInterceptor.preHandle(request, response, new Object()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.IP_BLOCKED);
    }
}
