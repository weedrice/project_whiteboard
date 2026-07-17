package com.weedrice.whiteboard.global.security;

import com.weedrice.whiteboard.domain.auth.service.LoginAccountEligibilityService;
import com.weedrice.whiteboard.domain.auth.service.LoginAccountEligibilityService.LoginAccountEligibility;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final LoginAccountEligibilityService loginAccountEligibilityService;

    @Override
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with loginId: " + loginId));

        LoginAccountEligibility eligibility = loginAccountEligibilityService.evaluate(user);

        return new CustomUserDetails(
                user.getUserId(),
                user.getLoginId(),
                user.getPassword(),
                user.getSecurityVersion() == null ? 0L : user.getSecurityVersion(),
                eligibility.enabled(),
                true,
                true,
                eligibility.accountNonLocked(),
                SecurityAuthorities.user(user.isUsableSuperAdmin()));
    }
}
