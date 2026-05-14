package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.global.common.util.ClientUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class LoginClientMetadataResolver {

    public LoginClientMetadata resolve(HttpServletRequest httpServletRequest) {
        if (httpServletRequest == null) {
            return new LoginClientMetadata(null, null);
        }
        return new LoginClientMetadata(
                ClientUtils.getIp(httpServletRequest),
                httpServletRequest.getHeader("User-Agent"));
    }
}
