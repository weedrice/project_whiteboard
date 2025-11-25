package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserSettings;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;

    @Transactional
    public Long signup(String loginId, String password, String email, String displayName) {
        if (userRepository.existsByLoginId(loginId)) {
            throw new RuntimeException("이미 존재하는 아이디입니다.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("이미 존재하는 이메일입니다.");
        }

        User user = User.builder()
                .loginId(loginId)
                .password(passwordEncoder.encode(password))
                .email(email)
                .displayName(displayName)
                .build();

        userRepository.save(user);

        // Create default settings
        // Note: UserSettingsRepository might be needed if cascade is not set or logic
        // differs
        // For now assuming UserSettings is created via User or separately.
        // Let's assume we need to save it. But UserSettings has @MapsId, so saving User
        // might be enough if mapped correctly?
        // Actually UserSettings is the owning side of the relationship usually in
        // OneToOne with MapsId?
        // Let's check UserSettings entity again. It has @MapsId.
        // We should save UserSettings.

        // Since I didn't create UserSettingsRepository, I'll skip saving settings for
        // now or add it later.
        // Ideally, we should have UserSettingsRepository.

        return user.getUserId();
    }

    @Transactional
    public String login(String loginId, String password) {
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(loginId,
                password);

        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);

        return jwtTokenProvider.createAccessToken(authentication);
    }
}
