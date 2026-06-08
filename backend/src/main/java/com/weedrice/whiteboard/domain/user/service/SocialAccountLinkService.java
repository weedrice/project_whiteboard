package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.user.entity.SocialAccount;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.SocialAccountRepository;
import com.weedrice.whiteboard.global.common.util.TextInputNormalizer;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SocialAccountLinkService {

    private static final int MAX_PROVIDER_LENGTH = 255;
    private static final int MAX_PROVIDER_ID_LENGTH = 255;

    private final SocialAccountRepository socialAccountRepository;

    @Transactional
    public SocialAccount linkSocialAccount(User user, String provider, String providerId) {
        String normalizedProvider = normalizeProvider(provider);
        String normalizedProviderId = normalizeProviderId(providerId);

        SocialAccount existingLink = resolveOptionalLinkConflict(user, normalizedProvider, normalizedProviderId);
        if (existingLink != null) {
            return existingLink;
        }

        int inserted = socialAccountRepository.insertSocialAccountIfAbsent(
                user.getUserId(),
                normalizedProvider,
                normalizedProviderId);
        if (inserted == 0) {
            return resolveRequiredLinkConflict(user, normalizedProvider, normalizedProviderId);
        }

        return socialAccountRepository.findByProviderAndProviderId(normalizedProvider, normalizedProviderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DUPLICATE_RESOURCE));
    }

    private SocialAccount resolveOptionalLinkConflict(User user, String provider, String providerId) {
        return resolveLinkConflict(user, provider, providerId, false);
    }

    private SocialAccount resolveRequiredLinkConflict(User user, String provider, String providerId) {
        return resolveLinkConflict(user, provider, providerId, true);
    }

    private SocialAccount resolveLinkConflict(User user, String provider, String providerId, boolean required) {
        SocialAccount existingByProvider = resolveExistingByProviderId(user, provider, providerId);
        if (existingByProvider != null) {
            return existingByProvider;
        }

        SocialAccount existingByUserProvider = resolveExistingByUserProvider(user, provider, providerId);
        if (existingByUserProvider != null) {
            return existingByUserProvider;
        }

        if (required) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
        return null;
    }

    private boolean hasSameUser(SocialAccount socialAccount, User user) {
        return Objects.equals(socialAccount.getUser().getUserId(), user.getUserId());
    }

    private SocialAccount resolveExistingByProviderId(User user, String provider, String providerId) {
        List<SocialAccount> existingAccounts = socialAccountRepository.findAllByNormalizedProviderAndProviderId(
                provider,
                providerId);
        for (SocialAccount existingAccount : existingAccounts) {
            if (hasSameUser(existingAccount, user)) {
                return existingAccount;
            }
        }
        if (!existingAccounts.isEmpty()) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
        return null;
    }

    private SocialAccount resolveExistingByUserProvider(User user, String provider, String providerId) {
        List<SocialAccount> existingAccounts = socialAccountRepository.findAllByUserAndNormalizedProvider(
                user,
                provider);
        for (SocialAccount existingAccount : existingAccounts) {
            if (Objects.equals(existingAccount.getProviderId().trim(), providerId)) {
                return existingAccount;
            }
        }
        if (!existingAccounts.isEmpty()) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
        return null;
    }

    private String normalizeProvider(String provider) {
        String normalizedProvider = TextInputNormalizer.normalizeRequired(provider, MAX_PROVIDER_LENGTH);
        return normalizedProvider.toLowerCase(Locale.ROOT);
    }

    private String normalizeProviderId(String providerId) {
        return TextInputNormalizer.normalizeRequired(providerId, MAX_PROVIDER_ID_LENGTH);
    }
}
