package com.weedrice.whiteboard.domain.point.service;

import com.weedrice.whiteboard.domain.point.dto.PointHistoryResponse;
import com.weedrice.whiteboard.domain.point.dto.UserPointResponse;
import com.weedrice.whiteboard.domain.point.entity.PointHistory;
import com.weedrice.whiteboard.domain.point.entity.PointHistoryType;
import com.weedrice.whiteboard.domain.point.entity.UserPoint;
import com.weedrice.whiteboard.domain.point.repository.PointHistoryRepository;
import com.weedrice.whiteboard.domain.point.repository.UserPointRepository;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserReadableResolver;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.common.util.TextInputNormalizer;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointService {

    private static final int HISTORY_TYPE_MAX_LENGTH = 50;
    private static final int HISTORY_DESCRIPTION_MAX_LENGTH = 255;
    private static final int HISTORY_RELATED_TYPE_MAX_LENGTH = 50;
    private static final int DEFAULT_HISTORY_PAGE_SIZE = 20;
    private final UserPointRepository userPointRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final UserRepository userRepository;
    private final SanctionService sanctionService;
    private final UserReadableResolver userReadableResolver;

    public UserPointResponse getUserPoint(@NonNull Long userId) {
        userReadableResolver.resolveActive(userId);
        return userPointRepository.findByUserId(userId)
                .map(UserPointResponse::from)
                .orElseGet(() -> UserPointResponse.builder()
                        .currentPoint(0)
                        .build());
    }

    public PointHistoryResponse getPointHistories(@NonNull Long userId, String type, Pageable pageable) {
        userReadableResolver.resolveActive(userId);
        PointHistoryType normalizedType = normalizeHistoryType(type);
        Pageable safePageable = PageRequestUtils.of(pageable, DEFAULT_HISTORY_PAGE_SIZE);
        Page<PointHistory> historyPage;
        if (normalizedType != null) {
            historyPage = pointHistoryRepository.findByUser_UserIdAndTypeOrderByCreatedAtDescHistoryIdDesc(
                    userId,
                    normalizedType.name(),
                    safePageable);
        } else {
            historyPage = pointHistoryRepository.findByUser_UserIdOrderByCreatedAtDescHistoryIdDesc(userId,
                    safePageable);
        }
        return PointHistoryResponse.from(historyPage);
    }

    @Transactional
    public void addPoint(@NonNull Long userId, int amount, String description, Long relatedId, String relatedType) {
        validatePositiveAmount(amount);
        changePoint(userId, amount, PointHistoryType.EARN, description, relatedId, relatedType, true, false);
    }

    @Transactional
    public int addPointIfAbsent(@NonNull Long userId, int amount, String description, Long relatedId,
            String relatedType) {
        validatePositiveAmount(amount);
        boolean added = changePoint(
                userId,
                amount,
                PointHistoryType.EARN,
                description,
                relatedId,
                relatedType,
                true,
                false,
                true);
        return added ? amount : 0;
    }

    @Transactional
    public void forceSubtractPoint(@NonNull Long userId, int amount, String description, Long relatedId,
            String relatedType) {
        validatePositiveAmount(amount);
        changePoint(userId, -amount, PointHistoryType.PENALTY, description, relatedId, relatedType, true, false);
    }

    @Transactional
    public void reverseRewardPoint(@NonNull Long userId, int amount, String description, Long relatedId,
            String relatedType) {
        validatePositiveAmount(amount);
        changePoint(userId, -amount, PointHistoryType.REWARD_REVERSAL, description, relatedId, relatedType, true,
                false);
    }

    @Transactional
    public void spendPoint(@NonNull Long userId, int amount, String description, Long relatedId,
            String relatedType) {
        validatePositiveAmount(amount);
        changePoint(userId, -amount, PointHistoryType.SPEND, description, relatedId, relatedType, false, true);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void spendPointForPrevalidatedUser(@NonNull User user, int amount, String description, Long relatedId,
            String relatedType) {
        validatePositiveAmount(amount);
        changePoint(user, -amount, PointHistoryType.SPEND, description, relatedId, relatedType, false, true, false,
                false);
    }

    public int getCurrentBalance(@NonNull Long userId) {
        userReadableResolver.resolveActive(userId);
        return userPointRepository.findByUserId(userId)
                .map(UserPoint::getCurrentPoint)
                .orElse(0);
    }

    private boolean changePoint(@NonNull Long userId, int delta, PointHistoryType historyType, String description,
            Long relatedId,
            String relatedType, boolean createIfMissing, boolean validateSufficientBalance) {
        return changePoint(userId, delta, historyType, description, relatedId, relatedType, createIfMissing,
                validateSufficientBalance, false);
    }

    private boolean changePoint(@NonNull Long userId, int delta, PointHistoryType historyType, String description,
            Long relatedId,
            String relatedType, boolean createIfMissing, boolean validateSufficientBalance,
            boolean skipExistingHistory) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return changePoint(user, delta, historyType, description, relatedId, relatedType, createIfMissing,
                validateSufficientBalance, skipExistingHistory, true);
    }

    private boolean changePoint(@NonNull User user, int delta, PointHistoryType historyType, String description,
            Long relatedId,
            String relatedType, boolean createIfMissing, boolean validateSufficientBalance,
            boolean skipExistingHistory, boolean validateSpendSanction) {
        Long userId = user.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (validateSpendSanction && historyType == PointHistoryType.SPEND) {
            sanctionService.validateNotBanned(user);
        }
        String normalizedDescription = normalizeHistoryDescription(description);
        String normalizedRelatedType = normalizeRelatedType(relatedType);
        if (skipExistingHistory && pointHistoryRepository.existsByUser_UserIdAndTypeAndRelatedTypeAndRelatedId(
                userId,
                historyType.name(),
                normalizedRelatedType,
                relatedId)) {
            return false;
        }
        UserPoint userPoint = getOrCreateUserPoint(user, createIfMissing);

        if (validateSufficientBalance && userPoint.getCurrentPoint() < Math.abs(delta)) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_POINTS);
        }

        validateBalanceRange(userPoint, delta);

        if (delta >= 0) {
            userPoint.addPoint(delta);
        } else {
            userPoint.subtractPoint(Math.abs(delta));
        }

        userPointRepository.save(userPoint);
        pointHistoryRepository.save(PointHistory.builder()
                .user(user)
                .type(historyType.name())
                .amount(delta)
                .balanceAfter(userPoint.getCurrentPoint())
                .description(normalizedDescription)
                .relatedId(relatedId)
                .relatedType(normalizedRelatedType)
                .build());
        return true;
    }

    private UserPoint getOrCreateUserPoint(User user, boolean createIfMissing) {
        return userPointRepository.findByUserId(user.getUserId())
                .orElseGet(() -> {
                    if (!createIfMissing) {
                        throw new BusinessException(ErrorCode.INSUFFICIENT_POINTS);
                    }
                    return UserPoint.builder()
                            .user(user)
                            .build();
                });
    }

    private void validatePositiveAmount(int amount) {
        if (amount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateBalanceRange(UserPoint userPoint, int delta) {
        long balanceAfter = (long) userPoint.getCurrentPoint() + delta;
        if (balanceAfter > Integer.MAX_VALUE || balanceAfter < Integer.MIN_VALUE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private PointHistoryType normalizeHistoryType(String type) {
        String normalizedType = TextInputNormalizer.normalizeNullable(type);
        if (normalizedType == null || normalizedType.isBlank()) {
            return null;
        }
        if (normalizedType.length() > HISTORY_TYPE_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        return PointHistoryType.fromWireValue(normalizedType);
    }

    private String normalizeHistoryDescription(String description) {
        String normalizedDescription = TextInputNormalizer.normalizeNullable(description);
        if (normalizedDescription != null && normalizedDescription.length() > HISTORY_DESCRIPTION_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalizedDescription;
    }

    private String normalizeRelatedType(String relatedType) {
        String normalizedRelatedType = TextInputNormalizer.normalizeNullable(relatedType);
        if (normalizedRelatedType == null) {
            return null;
        }
        if (normalizedRelatedType.length() > HISTORY_RELATED_TYPE_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalizedRelatedType.toUpperCase(Locale.ROOT);
    }
}
