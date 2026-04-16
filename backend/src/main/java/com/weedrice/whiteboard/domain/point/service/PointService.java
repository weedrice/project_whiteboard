package com.weedrice.whiteboard.domain.point.service;

import com.weedrice.whiteboard.domain.point.dto.PointHistoryResponse;
import com.weedrice.whiteboard.domain.point.dto.UserPointResponse;
import com.weedrice.whiteboard.domain.point.entity.PointHistory;
import com.weedrice.whiteboard.domain.point.entity.UserPoint;
import com.weedrice.whiteboard.domain.point.repository.PointHistoryRepository;
import com.weedrice.whiteboard.domain.point.repository.UserPointRepository;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings({ "null", "unchecked" })
public class PointService {

        private static final String HISTORY_TYPE_EARN = "EARN";
        private static final String HISTORY_TYPE_PENALTY = "PENALTY";
        private static final String HISTORY_TYPE_SPEND = "SPEND";

        private final UserPointRepository userPointRepository;
        private final PointHistoryRepository pointHistoryRepository;
        private final UserRepository userRepository;

        public UserPointResponse getUserPoint(@NonNull Long userId) {
                ensureUserExists(userId);
                return userPointRepository.findByUserId(userId)
                                .map(UserPointResponse::from)
                                .orElseGet(() -> UserPointResponse.builder()
                                                .currentPoint(0)
                                                .build());
        }

        public PointHistoryResponse getPointHistories(@NonNull Long userId, String type, @NonNull Pageable pageable) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                Page<PointHistory> historyPage;
                if (type != null && !type.isEmpty()) {
                        historyPage = pointHistoryRepository.findByUserAndTypeOrderByCreatedAtDesc(user, type, pageable);
                } else {
                        historyPage = pointHistoryRepository.findByUserOrderByCreatedAtDesc(user, pageable);
                }
                return PointHistoryResponse.from(historyPage);
        }

        @Transactional
        public void addPoint(@NonNull Long userId, int amount, String description, Long relatedId, String relatedType) {
                changePoint(userId, amount, HISTORY_TYPE_EARN, description, relatedId, relatedType, true, false);
        }

        @Transactional
        public void forceSubtractPoint(@NonNull Long userId, int amount, String description, Long relatedId,
                        String relatedType) {
                changePoint(userId, -amount, HISTORY_TYPE_PENALTY, description, relatedId, relatedType, true, false);
        }

        @Transactional
        public void spendPoint(@NonNull Long userId, int amount, String description, Long relatedId,
                        String relatedType) {
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
                User user = userRepository.findByIdForUpdate(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                UserPoint userPoint = getOrCreateUserPoint(user, createIfMissing);

                if (validateSufficientBalance && userPoint.getCurrentPoint() < Math.abs(delta)) {
                        throw new BusinessException(ErrorCode.INSUFFICIENT_POINTS);
                }

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

        private void ensureUserExists(Long userId) {
                if (!userRepository.existsById(userId)) {
                        throw new BusinessException(ErrorCode.USER_NOT_FOUND);
                }
        }
}
