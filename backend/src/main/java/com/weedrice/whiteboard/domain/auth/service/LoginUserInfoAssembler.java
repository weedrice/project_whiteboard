package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.dto.LoginResponse;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.CurrentUserSummaryAssembler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoginUserInfoAssembler {

    private final CurrentUserSummaryAssembler currentUserSummaryAssembler;

    public LoginResponse.UserInfo assemble(User user) {
        CurrentUserSummaryAssembler.CurrentUserSummary userSummary = currentUserSummaryAssembler.assemble(user);
        return LoginResponse.UserInfo.builder()
                .userId(userSummary.userId())
                .loginId(userSummary.loginId())
                .displayName(userSummary.displayName())
                .profileImageUrl(userSummary.profileImageUrl())
                .isEmailVerified(Boolean.TRUE.equals(userSummary.isEmailVerified()))
                .role(userSummary.role())
                .theme(userSummary.theme())
                .points(userSummary.points())
                .build();
    }
}
