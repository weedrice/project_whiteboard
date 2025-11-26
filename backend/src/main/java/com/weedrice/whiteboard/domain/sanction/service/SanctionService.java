package com.weedrice.whiteboard.domain.sanction.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.sanction.entity.Sanction;
import com.weedrice.whiteboard.domain.sanction.repository.SanctionRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SanctionService {

    private final SanctionRepository sanctionRepository;
    private final UserRepository userRepository;
    private final AdminRepository adminRepository;

    @Transactional
    public Sanction createSanction(Long adminUserId, Long targetUserId, String type, String remark, LocalDateTime endDate) {
        Admin admin = adminRepository.findByUserAndIsActive(userRepository.findById(adminUserId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)), "Y")
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "관리자 권한이 없습니다."));

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if ("BAN".equals(type)) {
            targetUser.suspend();
        }

        Sanction sanction = Sanction.builder()
                .targetUser(targetUser)
                .admin(admin)
                .type(type)
                .remark(remark)
                .startDate(LocalDateTime.now())
                .endDate(endDate)
                .build();
        return sanctionRepository.save(sanction);
    }

    public Page<Sanction> getSanctions(Long targetUserId, Pageable pageable) {
        if (targetUserId != null) {
            User targetUser = userRepository.findById(targetUserId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            return sanctionRepository.findByTargetUser(targetUser, pageable);
        }
        return sanctionRepository.findAll(pageable);
    }

    public boolean isUserBanned(User user) {
        return sanctionRepository.findFirstByTargetUserAndTypeAndEndDateAfterOrderByEndDateDesc(user, "BAN", LocalDateTime.now()).isPresent();
    }
}
