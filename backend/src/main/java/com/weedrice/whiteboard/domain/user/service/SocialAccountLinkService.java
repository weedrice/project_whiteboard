package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.user.entity.SocialAccount;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.SocialAccountRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SocialAccountLinkService {

    private final SocialAccountRepository socialAccountRepository;

    @Transactional
    public SocialAccount linkSocialAccount(User user, String provider, String providerId) {
        SocialAccount existingByProvider = socialAccountRepository.findByProviderAndProviderId(provider, providerId)
                .orElse(null);
        if (existingByProvider != null) {
            if (hasSameUser(existingByProvider, user)) {
                return existingByProvider;
            }
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }

        SocialAccount existingByUserProvider = socialAccountRepository.findByUserAndProvider(user, provider)
                .orElse(null);
        if (existingByUserProvider != null) {
            if (Objects.equals(existingByUserProvider.getProviderId(), providerId)) {
                return existingByUserProvider;
            }
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }

        try {
            return socialAccountRepository.saveAndFlush(SocialAccount.builder()
                    .user(user)
                    .provider(provider)
                    .providerId(providerId)
                    .build());
        } catch (DataIntegrityViolationException exception) {
            return resolveDuplicateLink(user, provider, providerId);
        }
    }

    private SocialAccount resolveDuplicateLink(User user, String provider, String providerId) {
        SocialAccount existingByProvider = socialAccountRepository.findByProviderAndProviderId(provider, providerId)
                .orElse(null);
        if (existingByProvider != null) {
            if (hasSameUser(existingByProvider, user)) {
                return existingByProvider;
            }
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }

        SocialAccount existingByUserProvider = socialAccountRepository.findByUserAndProvider(user, provider)
                .orElse(null);
        if (existingByUserProvider != null) {
            if (Objects.equals(existingByUserProvider.getProviderId(), providerId)) {
                return existingByUserProvider;
            }
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }

        throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
    }

    private boolean hasSameUser(SocialAccount socialAccount, User user) {
        return Objects.equals(socialAccount.getUser().getUserId(), user.getUserId());
    }
}
