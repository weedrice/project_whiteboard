package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminReadServiceTest {

    @Mock
    private AdminRepository adminRepository;

    @InjectMocks
    private AdminReadService adminReadService;

    private Admin admin;

    @BeforeEach
    void setUp() {
        User user = User.builder().loginId("testUser").displayName("tester").build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        Board board = Board.builder()
                .boardName("free")
                .boardUrl("free")
                .creator(user)
                .build();
        ReflectionTestUtils.setField(board, "boardId", 10L);

        admin = Admin.builder().user(user).board(board).role(Role.BOARD_ADMIN).build();
        ReflectionTestUtils.setField(admin, "adminId", 100L);
    }

    @Test
    @DisplayName("관리자 목록을 페이지 응답으로 조회한다")
    void getAllAdmins_returnsPagedResponses() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(adminRepository.findAllByOrderByAdminIdDesc(pageable))
                .thenReturn(new PageImpl<>(List.of(admin), pageable, 1));

        Page<com.weedrice.whiteboard.domain.admin.dto.AdminResponse> page = adminReadService.getAllAdmins(pageable);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getAdminId()).isEqualTo(100L);
        assertThat(page.getContent().get(0).getBoard().getBoardId()).isEqualTo(10L);
        verify(adminRepository).findAllByOrderByAdminIdDesc(pageable);
    }
}
