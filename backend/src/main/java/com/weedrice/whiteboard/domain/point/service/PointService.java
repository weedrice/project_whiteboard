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

        private final UserPointRepository userPointRepository;
        private final PointHistoryRepository pointHistoryRepository;
        private final UserRepository userRepository;

        public UserPointResponse getUserPoint(@NonNull Long userId) {
                UserPoint userPoint = userPointRepository.findByUserId(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                return UserPointResponse.from(userPoint);
        }

        public PointHistoryResponse getPointHistories(@NonNull Long userId, String type, @NonNull Pageable pageable) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                Page<PointHistory> historyPage;
                if (type != null && !type.isEmpty()) {
                        historyPage = pointHistoryRepository.findByUserAndTypeOrderByCreatedAtDesc(user, type,
                                        pageable);
                } else {
                        historyPage = pointHistoryRepository.findByUserOrderByCreatedAtDesc(user, pageable);
                }
                return PointHistoryResponse.from(historyPage);
        }

        @Transactional
        public void addPoint(@NonNull Long userId, int amount, String description, Long relatedId, String relatedType) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                UserPoint userPoint = userPointRepository.findByUserId(userId)
                                .orElseGet(() -> userPointRepository.save(UserPoint.builder().user(user).build())); // UserPoint가
                                                                                                                    // 없으면
                                                                                                                    // 생성

                userPoint.addPoint(amount);
                userPointRepository.save(userPoint);

                PointHistory history = PointHistory.builder()
                                .user(user)
                                .type("EARN")
                                .amount(amount)
                                .balanceAfter(userPoint.getCurrentPoint())
                                .description(description)
                                .relatedId(relatedId)
                                .relatedType(relatedType)
                                .build();
                pointHistoryRepository.save(history);
        }

        @Transactional
        public void forceSubtractPoint(@NonNull Long userId, int amount, String description, Long relatedId,
                        String relatedType) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                UserPoint userPoint = userPointRepository.findByUserId(userId)
                                .orElseGet(() -> userPointRepository.save(UserPoint.builder().user(user).build()));

                userPoint.subtractPoint(amount); // Assuming subtractPoint in entity handles calculation. Does it
                                                 // prevent negative? Check entity.
                userPointRepository.save(userPoint);

                PointHistory history = PointHistory.builder()
                                .user(user)
                                .type("PENALTY") // Or DEDUCT
                                .amount(-amount)
                                .balanceAfter(userPoint.getCurrentPoint())
                                .description(description)
                                .relatedId(relatedId)
                                .relatedType(relatedType)
                                .build();
                pointHistoryRepository.save(history);
        }
}
