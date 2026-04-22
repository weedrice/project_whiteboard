package com.weedrice.whiteboard.global.security.oauth;

import com.weedrice.whiteboard.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final OAuthUserResolutionService oauthUserResolutionService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = oauth2UserDelegate();
        OAuth2User oauth2User = delegate.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        String userNameAttributeName = userRequest.getClientRegistration().getProviderDetails()
                .getUserInfoEndpoint().getUserNameAttributeName();

        Map<String, Object> attributes = oauth2User.getAttributes();
        OAuthAttributes extractAttributes = OAuthAttributes.of(registrationId, userNameAttributeName, attributes);

        String providerId = extractAttributes.getProviderId();

        User user = oauthUserResolutionService.resolveUser(registrationId, extractAttributes)
                .orElse(null);
        if (user != null) {
            return new CustomOAuth2User(
                    user,
                    attributes,
                    extractAttributes.getNameAttributeKey(),
                    Collections.singleton(
                            new SimpleGrantedAuthority(
                                    user.getIsSuperAdmin() ? "ROLE_SUPER_ADMIN" : "ROLE_USER")));
        }

        return new UnregisteredOAuth2User(
                attributes,
                extractAttributes.getNameAttributeKey(),
                registrationId,
                providerId,
                extractAttributes.getEmail(),
                extractAttributes.getName(),
                extractAttributes.getPicture());
    }

    OAuth2UserService<OAuth2UserRequest, OAuth2User> oauth2UserDelegate() {
        return new DefaultOAuth2UserService();
    }
}
