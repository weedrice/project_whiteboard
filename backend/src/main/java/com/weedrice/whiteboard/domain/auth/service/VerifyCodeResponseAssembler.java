package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.dto.VerifyCodeResponse;
import com.weedrice.whiteboard.domain.auth.entity.VerificationPurpose;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VerifyCodeResponseAssembler {

    private final UserRepository userRepository;

    public VerifyCodeResponse assemble(String email, VerificationPurpose purpose, String verificationTicket) {
        VerifyCodeResponse.VerifyCodeResponseBuilder builder = VerifyCodeResponse.builder()
                .verified(true)
                .verificationTicket(verificationTicket)
                .isReregister(false);

        if (purpose == VerificationPurpose.SIGNUP) {
            userRepository.findByEmail(email)
                    .filter(user -> "DELETED".equals(user.getStatus()))
                    .ifPresent(user -> builder
                            .loginId(user.getLoginId())
                            .isReregister(true));
        }

        return builder.build();
    }
}
