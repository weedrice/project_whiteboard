package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.user.dto.BlockedUserResponse;
import com.weedrice.whiteboard.domain.user.dto.BlockedUsersResponse;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserBlock;
import com.weedrice.whiteboard.domain.user.repository.UserBlockRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserBlockServiceTest {

    @InjectMocks
    private UserBlockService userBlockService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserBlockRepository userBlockRepository;

    @Test
    @DisplayName("사용자 차단 성공")
    void blockUser_success() {
        User blocker = User.builder().build();
        ReflectionTestUtils.setField(blocker, "userId", 1L);
        User blocked = User.builder().build();
        ReflectionTestUtils.setField(blocked, "userId", 2L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(blocker));
        when(userRepository.findById(2L)).thenReturn(Optional.of(blocked));
        when(userBlockRepository.existsByUserAndTarget(blocker, blocked)).thenReturn(false);

        userBlockService.blockUser(1L, 2L);

        verify(userBlockRepository).save(any(UserBlock.class));
    }

    @Test
    @DisplayName("사용자 차단 실패 - 자기 자신 차단")
    void blockUser_self() {
        assertThatThrownBy(() -> userBlockService.blockUser(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CANNOT_BLOCK_SELF);
    }

    @Test
    @DisplayName("사용자 차단 실패 - 이미 차단됨")
    void blockUser_alreadyBlocked() {
        User blocker = User.builder().build();
        ReflectionTestUtils.setField(blocker, "userId", 1L);
        User blocked = User.builder().build();
        ReflectionTestUtils.setField(blocked, "userId", 2L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(blocker));
        when(userRepository.findById(2L)).thenReturn(Optional.of(blocked));
        when(userBlockRepository.existsByUserAndTarget(blocker, blocked)).thenReturn(true);

        assertThatThrownBy(() -> userBlockService.blockUser(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ALREADY_BLOCKED);
    }

    @Test
    @DisplayName("사용자 차단 해제 성공")
    void unblockUser_success() {
        User blocker = User.builder().build();
        User blocked = User.builder().build();
        UserBlock userBlock = UserBlock.builder().user(blocker).target(blocked).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(blocker));
        when(userRepository.findById(2L)).thenReturn(Optional.of(blocked));
        when(userBlockRepository.findByUserAndTarget(blocker, blocked)).thenReturn(Optional.of(userBlock));

        userBlockService.unblockUser(1L, 2L);

        verify(userBlockRepository).delete(userBlock);
    }

    @Test
    @DisplayName("차단 목록 조회 성공")
    void getBlockedUsers_success() {
        User blocker = User.builder().build();
        User blocked = User.builder().displayName("Blocked").build();
        UserBlock userBlock = UserBlock.builder().user(blocker).target(blocked).build();
        Page<UserBlock> page = new PageImpl<>(List.of(userBlock));

        when(userRepository.findById(1L)).thenReturn(Optional.of(blocker));
        when(userBlockRepository.findByUserOrderByCreatedAtDesc(any(), any())).thenReturn(page);

        Page<BlockedUserResponse> response = userBlockService.getBlockedUsers(1L, PageRequest.of(0, 10));

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().getFirst().getDisplayName()).isEqualTo("Blocked");
    }

    @Test
    @DisplayName("차단 여부 확인")
    void isBlocked() {
        User blocker = User.builder().build();
        User blocked = User.builder().build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(blocker));
        when(userRepository.findById(2L)).thenReturn(Optional.of(blocked));
        when(userBlockRepository.existsByUserAndTarget(blocker, blocked)).thenReturn(true);

        assertThat(userBlockService.isBlocked(1L, 2L)).isTrue();
    }

    @Test
    @DisplayName("차단된 사용자 ID 목록 조회")
    void getBlockedUserIds() {
        User blocker = User.builder().build();
        User blocked = User.builder().build();
        ReflectionTestUtils.setField(blocked, "userId", 2L);
        UserBlock userBlock = UserBlock.builder().user(blocker).target(blocked).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(blocker));
        when(userBlockRepository.findByUser(blocker)).thenReturn(List.of(userBlock));

        List<Long> ids = userBlockService.getBlockedUserIds(1L);
        assertThat(ids).containsExactly(2L);
    }

    @Test
    @DisplayName("사용자 차단 실패 - 차단자 없음")
    void blockUser_blockerNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userBlockService.blockUser(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("사용자 차단 실패 - 대상자 없음")
    void blockUser_blockedNotFound() {
        // Blocker exists, Blocked does not
        when(userRepository.findById(1L)).thenReturn(Optional.of(com.weedrice.whiteboard.domain.user.entity.User.builder().build()));
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userBlockService.blockUser(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("차단 해제 실패 - 사용자 없음")
    void unblockUser_userNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userBlockService.unblockUser(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("차단 목록 조회 실패 - 사용자 없음")
    void getBlockedUsers_userNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userBlockService.getBlockedUsers(1L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("차단 여부 확인 실패 - 사용자 없음")
    void isBlocked_userNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userBlockService.isBlocked(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("차단된 사용자 ID 목록 조회 실패 - 사용자 없음")
    void getBlockedUserIds_userNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userBlockService.getBlockedUserIds(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}