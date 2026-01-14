package com.weedrice.whiteboard.global.security.oauth;

import com.weedrice.whiteboard.domain.user.entity.SocialAccount;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.SocialAccountRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

        private final UserRepository userRepository;
        private final SocialAccountRepository socialAccountRepository;

        @Override
        @Transactional
        public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
                OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
                OAuth2User oAuth2User = delegate.loadUser(userRequest);

                String registrationId = userRequest.getClientRegistration().getRegistrationId();
                String userNameAttributeName = userRequest.getClientRegistration().getProviderDetails()
                                .getUserInfoEndpoint().getUserNameAttributeName();

                Map<String, Object> attributes = oAuth2User.getAttributes();
                OAuthAttributes extractAttributes = OAuthAttributes.of(registrationId, userNameAttributeName,
                                attributes);

                String providerId = extractAttributes.getProviderId();

                // 1. Check SocialAccount
                Optional<SocialAccount> socialAccountOptional = socialAccountRepository
                                .findByProviderAndProviderId(registrationId, providerId);

                if (socialAccountOptional.isPresent()) {
                        User user = socialAccountOptional.get().getUser();
                        // Update profile if needed
                        // user.updateDisplayName(extractAttributes.getName());
                        // user.updateProfileImage(extractAttributes.getPicture());

                        return new CustomOAuth2User(
                                        user,
                                        attributes,
                                        extractAttributes.getNameAttributeKey(),
                                        Collections.singleton(
                                                        new SimpleGrantedAuthority(
                                                                        user.getIsSuperAdmin() ? "ROLE_SUPER_ADMIN"
                                                                                        : "ROLE_USER")));
                }

                // 2. Check Email (for linking)
                Optional<User> userOptional = userRepository.findByEmail(extractAttributes.getEmail());
                if (userOptional.isPresent()) {
                        User user = userOptional.get();
                        // Create SocialAccount and link
                        SocialAccount socialAccount = SocialAccount.builder()
                                        .user(user)
                                        .provider(registrationId)
                                        .providerId(providerId)
                                        .build();
                        socialAccountRepository.save(socialAccount);

                        // Existing user info is NOT updated to preserve original data

                        return new CustomOAuth2User(
                                        user,
                                        attributes,
                                        extractAttributes.getNameAttributeKey(),
                                        Collections.singleton(
                                                        new SimpleGrantedAuthority(
                                                                        user.getIsSuperAdmin() ? "ROLE_SUPER_ADMIN"
                                                                                        : "ROLE_USER")));
                }

                // 3. Unregistered User
                return new UnregisteredOAuth2User(
                                attributes,
                                extractAttributes.getNameAttributeKey(),
                                registrationId,
                                providerId,
                                extractAttributes.getEmail(),
                                extractAttributes.getName(),
                                extractAttributes.getPicture());
        }
}
