package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.global.common.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoginClientMetadataResolver {

    private final ClientIpResolver clientIpResolver;

    public LoginClientMetadata resolve(HttpServletRequest httpServletRequest) {
        if (httpServletRequest == null) {
            return LoginClientMetadata.empty();
        }
        return new LoginClientMetadata(
                clientIpResolver.resolve(httpServletRequest),
                httpServletRequest.getHeader("User-Agent"));
    }
}
