package com.weedrice.whiteboard.domain.point.service;

import com.weedrice.whiteboard.domain.point.dto.PointHistoryResponse;
import com.weedrice.whiteboard.domain.point.dto.UserPointResponse;
import com.weedrice.whiteboard.domain.point.entity.PointHistory;
import com.weedrice.whiteboard.domain.point.entity.UserPoint;
import com.weedrice.whiteboard.domain.point.repository.PointHistoryRepository;
import com.weedrice.whiteboard.domain.point.repository.UserPointRepository;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings({ "null", "unchecked" })
public class PointService {

        private static final String HISTORY_TYPE_EARN = "EARN";
        private static final String HISTORY_TYPE_PENALTY = "PENALTY";
        private static final String HISTORY_TYPE_REWARD_REVERSAL = "REWARD_REVERSAL";
        private static final String HISTORY_TYPE_SPEND = "SPEND";
        private static final Set<String> HISTORY_TYPES = Set.of(
                        HISTORY_TYPE_EARN,
                        HISTORY_TYPE_PENALTY,
                        HISTORY_TYPE_REWARD_REVERSAL,
                        HISTORY_TYPE_SPEND);

        private final UserPointRepository userPointRepository;
        private final PointHistoryRepository pointHistoryRepository;
        private final UserRepository userRepository;
        private final SanctionService sanctionService;

        public UserPointResponse getUserPoint(@NonNull Long userId) {
                ensureUserExists(userId);
                return userPointRepository.findByUserId(userId)
                                .map(UserPointResponse::from)
                                .orElseGet(() -> UserPointResponse.builder()
                                                .currentPoint(0)
                                                .build());
        }

        public PointHistoryResponse getPointHistories(@NonNull Long userId, String type, @NonNull Pageable pageable) {
                String normalizedType = normalizeHistoryType(type);
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                Page<PointHistory> historyPage;
                if (normalizedType != null) {
                        historyPage = pointHistoryRepository.findByUserAndTypeOrderByCreatedAtDescHistoryIdDesc(
                                        user,
                                        normalizedType,
                                        pageable);
                } else {
                        historyPage = pointHistoryRepository.findByUserOrderByCreatedAtDescHistoryIdDesc(user, pageable);
                }
                return PointHistoryResponse.from(historyPage);
        }

        @Transactional
        public void addPoint(@NonNull Long userId, int amount, String description, Long relatedId, String relatedType) {
                validatePositiveAmount(amount);
                changePoint(userId, amount, HISTORY_TYPE_EARN, description, relatedId, relatedType, true, false);
        }

        @Transactional
        public void addPointIfAbsent(@NonNull Long userId, int amount, String description, Long relatedId,
                        String relatedType) {
                validatePositiveAmount(amount);
                changePoint(userId, amount, HISTORY_TYPE_EARN, description, relatedId, relatedType, true, false, true);
        }

        @Transactional
        public void forceSubtractPoint(@NonNull Long userId, int amount, String description, Long relatedId,
                        String relatedType) {
                validatePositiveAmount(amount);
                changePoint(userId, -amount, HISTORY_TYPE_PENALTY, description, relatedId, relatedType, true, false);
        }

        @Transactional
        public void reverseRewardPoint(@NonNull Long userId, int amount, String description, Long relatedId,
                        String relatedType) {
                validatePositiveAmount(amount);
                changePoint(userId, -amount, HISTORY_TYPE_REWARD_REVERSAL, description, relatedId, relatedType, true,
                                false);
        }

        @Transactional
        public void spendPoint(@NonNull Long userId, int amount, String description, Long relatedId,
                        String relatedType) {
                validatePositiveAmount(amount);
                changePoint(userId, -amount, HISTORY_TYPE_SPEND, description, relatedId, relatedType, false, true);
        }

        public int getCurrentBalance(@NonNull Long userId) {
                ensureUserExists(userId);
                return userPointRepository.findByUserId(userId)
                                .map(UserPoint::getCurrentPoint)
                                .orElse(0);
        }

        private void changePoint(@NonNull Long userId, int delta, String historyType, String description, Long relatedId,
                        String relatedType, boolean createIfMissing, boolean validateSufficientBalance) {
                changePoint(userId, delta, historyType, description, relatedId, relatedType, createIfMissing,
                                validateSufficientBalance, false);
        }

        private void changePoint(@NonNull Long userId, int delta, String historyType, String description, Long relatedId,
                        String relatedType, boolean createIfMissing, boolean validateSufficientBalance,
                        boolean skipExistingHistory) {
                User user = userRepository.findByIdForUpdate(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                if (HISTORY_TYPE_SPEND.equals(historyType)) {
                        sanctionService.validateNotBanned(user);
                }
                if (skipExistingHistory && pointHistoryRepository.existsByUser_UserIdAndTypeAndRelatedTypeAndRelatedId(
                                userId,
                                historyType,
                                relatedType,
                                relatedId)) {
                        return;
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
                                .type(historyType)
                                .amount(delta)
                                .balanceAfter(userPoint.getCurrentPoint())
                                .description(description)
                                .relatedId(relatedId)
                                .relatedType(relatedType)
                                .build());
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

        private String normalizeHistoryType(String type) {
                if (type == null || type.isBlank()) {
                        return null;
                }

                String normalizedType = type.trim().toUpperCase(Locale.ROOT);
                if (!HISTORY_TYPES.contains(normalizedType)) {
                        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
                }
                return normalizedType;
        }

        private void ensureUserExists(Long userId) {
                if (!userRepository.existsById(userId)) {
                        throw new BusinessException(ErrorCode.USER_NOT_FOUND);
                }
        }
}
