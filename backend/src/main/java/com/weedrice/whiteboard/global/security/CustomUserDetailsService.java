package com.weedrice.whiteboard.global.security;

import com.weedrice.whiteboard.domain.auth.service.LoginAccountEligibilityService;
import com.weedrice.whiteboard.domain.auth.service.LoginAccountEligibilityService.LoginAccountEligibility;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final LoginAccountEligibilityService loginAccountEligibilityService;

    @Override
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with loginId: " + loginId));

        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(Role.ROLE_USER));

        if (user.isUsableSuperAdmin()) {
            authorities.add(new SimpleGrantedAuthority(Role.ROLE_SUPER_ADMIN));
        }

        LoginAccountEligibility eligibility = loginAccountEligibilityService.evaluate(user);

        return new CustomUserDetails(
                user.getUserId(),
                user.getLoginId(),
                user.getPassword(),
                eligibility.enabled(),
                true,
                true,
                eligibility.accountNonLocked(),
                authorities);
    }
}
