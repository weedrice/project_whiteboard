package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.dto.KeywordSubscriptionResponse;
import com.weedrice.whiteboard.domain.notification.entity.UserKeywordSubscription;
import com.weedrice.whiteboard.domain.notification.repository.UserKeywordSubscriptionRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KeywordSubscriptionService {

    private static final int MAX_KEYWORDS_PER_USER = 10;

    private final UserKeywordSubscriptionRepository keywordSubscriptionRepository;
    private final UserWritableResolver userWritableResolver;

    public List<KeywordSubscriptionResponse> getSubscriptions(Long userId) {
        userWritableResolver.resolve(userId);
        return keywordSubscriptionRepository.findByUser_UserIdOrderByCreatedAtDescSubscriptionIdDesc(userId).stream()
                .map(KeywordSubscriptionResponse::from)
                .toList();
    }

    @Transactional
    public KeywordSubscriptionResponse subscribe(Long userId, String keyword) {
        String normalizedKeyword = normalizeKeyword(keyword);
        User user = userWritableResolver.resolveForUpdate(userId);
        return keywordSubscriptionRepository.findByUser_UserIdAndKeyword(userId, normalizedKeyword)
                .map(KeywordSubscriptionResponse::from)
                .orElseGet(() -> createSubscription(user, normalizedKeyword));
    }

    @Transactional
    public void unsubscribe(Long userId, String keyword) {
        String normalizedKeyword = normalizeKeyword(keyword);
        userWritableResolver.resolve(userId);
        keywordSubscriptionRepository.findByUser_UserIdAndKeyword(userId, normalizedKeyword)
                .ifPresent(keywordSubscriptionRepository::delete);
    }

    private KeywordSubscriptionResponse createSubscription(User user, String keyword) {
        if (keywordSubscriptionRepository.countByUser_UserId(user.getUserId()) >= MAX_KEYWORDS_PER_USER) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        keywordSubscriptionRepository.insertIgnore(user.getUserId(), keyword);
        return keywordSubscriptionRepository.findByUser_UserIdAndKeyword(user.getUserId(), keyword)
                .map(KeywordSubscriptionResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        String normalizedKeyword = keyword.strip().toLowerCase(Locale.ROOT);
        if (normalizedKeyword.isBlank() || normalizedKeyword.length() > 50) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalizedKeyword;
    }
}
