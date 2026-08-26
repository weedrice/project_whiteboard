package com.weedrice.whiteboard.domain.inquiry.integration;

import com.weedrice.whiteboard.domain.inquiry.port.InquiryUserPort;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
class InquiryUserAdapter implements InquiryUserPort {
    private final UserRepository userRepository;

    @Override
    public Long lockActiveUserId(Long userId) {
        return userRepository.findActiveByIdForUpdate(userId)
                .map(User::getUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    public Long lockUserId(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .map(User::getUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    public Map<Long, String> findDisplayNames(Collection<Long> userIds) {
        Map<Long, String> names = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) return names;
        userRepository.findAllById(userIds.stream().filter(Objects::nonNull).distinct().toList())
                .forEach(user -> names.put(user.getUserId(), user.getDisplayName()));
        return names;
    }

    @Override
    public boolean isUsableSuperAdmin(Long userId) {
        return userId != null && userRepository.findById(userId).map(User::isUsableSuperAdmin).orElse(false);
    }

}
