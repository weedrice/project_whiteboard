package com.weedrice.whiteboard.domain.point.service;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointService {

    private final UserPointRepository userPointRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final UserRepository userRepository;

    public UserPoint getUserPoint(Long userId) {
        return userPointRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)); // UserPoint는 User 생성 시 함께 생성되므로, User가 없으면 UserPoint도 없음
    }

    public Page<PointHistory> getPointHistories(Long userId, String type, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (type != null && !type.isEmpty()) {
            return pointHistoryRepository.findByUserAndTypeOrderByCreatedAtDesc(user, type, pageable);
        }
        return pointHistoryRepository.findByUserOrderByCreatedAtDesc(user, pageable);
    }

    @Transactional
    public void addPoint(Long userId, int amount, String description, Long relatedId, String relatedType) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        UserPoint userPoint = userPointRepository.findByUserId(userId)
                .orElseGet(() -> userPointRepository.save(UserPoint.builder().user(user).build())); // UserPoint가 없으면 생성

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
    public void forceSubtractPoint(Long userId, int amount, String description, Long relatedId, String relatedType) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        UserPoint userPoint = userPointRepository.findByUserId(userId)
                .orElseGet(() -> userPointRepository.save(UserPoint.builder().user(user).build()));

        userPoint.subtractPoint(amount); // Assuming subtractPoint in entity handles calculation. Does it prevent negative? Check entity.
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
