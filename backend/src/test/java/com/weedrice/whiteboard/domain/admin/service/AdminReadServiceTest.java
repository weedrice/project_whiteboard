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
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
        PageRequest safePageable = PageRequest.of(0, 20, Sort.by(Sort.Order.desc("adminId")));
        when(adminRepository.findAll(safePageable))
                .thenReturn(new PageImpl<>(List.of(admin), safePageable, 1));

        Page<com.weedrice.whiteboard.domain.admin.dto.AdminResponse> page = adminReadService.getAllAdmins(pageable);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getAdminId()).isEqualTo(100L);
        assertThat(page.getContent().get(0).getBoard().getBoardId()).isEqualTo(10L);
        verify(adminRepository).findAll(safePageable);
    }

    @Test
    @DisplayName("관리자 목록 조회는 페이지 크기와 정렬 필드를 제한한다")
    void getAllAdmins_normalizesPageable() {
        PageRequest requested = PageRequest.of(2, 250, Sort.by(Sort.Order.asc("createdAt")));
        when(adminRepository.findAll(any(Pageable.class)))
                .thenAnswer(invocation -> Page.empty(invocation.getArgument(0)));

        Page<com.weedrice.whiteboard.domain.admin.dto.AdminResponse> page = adminReadService.getAllAdmins(requested);

        Pageable safePageable = page.getPageable();
        assertThat(safePageable.getPageNumber()).isEqualTo(2);
        assertThat(safePageable.getPageSize()).isEqualTo(100);
        assertThat(safePageable.getSort()).isEqualTo(Sort.by(
                Sort.Order.asc("createdAt"),
                Sort.Order.desc("adminId")));
        verify(adminRepository).findAll(safePageable);
    }

    @Test
    @DisplayName("관리자 목록 조회는 허용되지 않은 정렬 필드를 기본값으로 대체한다")
    void getAllAdmins_usesDefaultSortWhenOnlyUnsupportedSortRequested() {
        PageRequest requested = PageRequest.of(0, 20, Sort.by(Sort.Order.asc("user.loginId")));
        when(adminRepository.findAll(any(Pageable.class)))
                .thenAnswer(invocation -> Page.empty(invocation.getArgument(0)));

        Page<com.weedrice.whiteboard.domain.admin.dto.AdminResponse> page = adminReadService.getAllAdmins(requested);

        Pageable safePageable = page.getPageable();
        assertThat(safePageable.getSort()).isEqualTo(Sort.by(Sort.Order.desc("adminId")));
        verify(adminRepository).findAll(safePageable);
    }
}
