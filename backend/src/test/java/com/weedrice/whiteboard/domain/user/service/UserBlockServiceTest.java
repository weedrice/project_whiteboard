package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserBlock;
import com.weedrice.whiteboard.domain.user.repository.UserBlockRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserBlockServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserBlockRepository userBlockRepository;

    @InjectMocks
    private UserBlockService userBlockService;

    private User user;
    private User targetUser;

    @BeforeEach
    void setUp() {
        user = User.builder().loginId("user").build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        targetUser = User.builder().loginId("target").build();
        ReflectionTestUtils.setField(targetUser, "userId", 2L);
    }

    @Test
    @DisplayName("사용자 차단 성공")
    void blockUser_success() {
        // given
        Long userId = 1L;
        Long targetUserId = 2L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
        when(userBlockRepository.existsByUserAndTarget(user, targetUser)).thenReturn(false);

        // when
        userBlockService.blockUser(userId, targetUserId);

        // then
        verify(userBlockRepository).save(any(UserBlock.class));
    }

    @Test
    @DisplayName("사용자 차단 실패 - 자기 자신 차단")
    void blockUser_fail_blockSelf() {
        // given
        Long userId = 1L;

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> userBlockService.blockUser(userId, userId));
        assertThat(exception.getErrorCode().getCode()).isEqualTo("U008");
    }

    @Test
    @DisplayName("차단 해제 성공")
    void unblockUser_success() {
        // given
        Long userId = 1L;
        Long targetUserId = 2L;
        UserBlock userBlock = UserBlock.builder().user(user).target(targetUser).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
        when(userBlockRepository.findByUserAndTarget(user, targetUser)).thenReturn(Optional.of(userBlock));

        // when
        userBlockService.unblockUser(userId, targetUserId);

        // then
        verify(userBlockRepository).delete(any(UserBlock.class));
    }

    @Test
    @DisplayName("차단 목록 조회 성공")
    void getBlockedUsers_success() {
        // given
        Long userId = 1L;
        Pageable pageable = Pageable.unpaged();
        UserBlock userBlock = UserBlock.builder().user(user).target(targetUser).build();
        Page<UserBlock> page = new PageImpl<>(Collections.singletonList(userBlock));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userBlockRepository.findByUserOrderByCreatedAtDesc(user, pageable)).thenReturn(page);

        // when
        Page<UserBlockService.BlockedUserDto> result = userBlockService.getBlockedUsers(userId, pageable);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getLoginId()).isEqualTo("target");
    }
}
