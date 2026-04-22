package com.weedrice.whiteboard.global.security.oauth;

import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.SocialAccount;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.SocialAccountRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.SocialAccountLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OAuthUserResolutionService {

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final SocialAccountLinkService socialAccountLinkService;
    private final SanctionService sanctionService;

    @Transactional
    public Optional<User> resolveUser(String registrationId, OAuthAttributes extractAttributes) {
        String providerId = extractAttributes.getProviderId();
        Optional<SocialAccount> linkedSocialAccount = socialAccountRepository
                .findByProviderAndProviderId(registrationId, providerId);
        if (linkedSocialAccount.isPresent()) {
            User linkedUser = linkedSocialAccount.get().getUser();
            sanctionService.validateNotBanned(linkedUser);
            return Optional.of(linkedUser);
        }

        String email = extractAttributes.getEmail();
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }

        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty()) {
            return Optional.empty();
        }

        User user = userOptional.get();
        sanctionService.validateNotBanned(user);
        socialAccountLinkService.linkSocialAccount(user, registrationId, providerId);
        return Optional.of(user);
    }
}
