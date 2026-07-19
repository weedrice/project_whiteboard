package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.admin.dto.IpBlockResponse;
import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.entity.IpBlock;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.admin.repository.IpBlockRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IpBlockServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 7, 3, 0);

    @Mock
    private IpBlockRepository ipBlockRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AdminRepository adminRepository;
    @Mock
    private ModerationActorResolver moderationActorResolver;

    private IpBlockService ipBlockService;

    private User adminUser;
    private Admin admin;

    @BeforeEach
    void setUp() {
        adminUser = User.builder()
                .loginId("admin")
                .email("admin@test.com")
                .password("password")
                .displayName("Admin")
                .build();
        ReflectionTestUtils.setField(adminUser, "userId", 1L);

        User boardOwner = User.builder()
                .loginId("owner")
                .email("owner@test.com")
                .password("password")
                .displayName("Owner")
                .build();
        Board board = Board.builder()
                .boardName("test")
                .boardUrl("test")
                .creator(boardOwner)
                .build();
        ReflectionTestUtils.setField(board, "boardId", 10L);

        admin = Admin.builder()
                .user(adminUser)
                .board(board)
                .role(Role.BOARD_ADMIN)
                .build();
        ReflectionTestUtils.setField(admin, "adminId", 11L);
        Clock clock = Clock.fixed(Instant.parse("2026-07-07T03:00:00Z"), ZoneOffset.UTC);
        ipBlockService = new IpBlockService(ipBlockRepository, moderationActorResolver, clock);

        lenient().when(moderationActorResolver.resolveModerationActor(1L))
                .thenReturn(new ModerationActorResolver.ModerationActor(adminUser, admin));
    }

    @Test
    @DisplayName("활성 관리자면 IP 차단을 저장한다")
    void blockIp_success() {
        String ipAddress = " 127.0.0.1 ";
        String normalizedIpAddress = "127.0.0.1";

        when(moderationActorResolver.resolveModerationActor(1L))
                .thenReturn(new ModerationActorResolver.ModerationActor(adminUser, admin));
        when(ipBlockRepository.findByIdForUpdate(normalizedIpAddress)).thenReturn(Optional.empty());
        when(ipBlockRepository.saveAndFlush(any(IpBlock.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IpBlockResponse response = ipBlockService.blockIp(1L, ipAddress, "test", null);

        assertThat(response.getIpAddress()).isEqualTo(normalizedIpAddress);
        assertThat(response.getAdmin().getAdminId()).isEqualTo(admin.getAdminId());
        verify(ipBlockRepository).saveAndFlush(any(IpBlock.class));
    }

    @Test
    @DisplayName("IPv6 리터럴도 차단 주소로 허용한다")
    void blockIp_acceptsIpv6Literal() {
        String ipAddress = "2001:DB8:0:0:0:0:0:1";
        String normalizedIpAddress = "2001:db8::1";

        when(ipBlockRepository.findByIdForUpdate(normalizedIpAddress)).thenReturn(Optional.empty());
        when(ipBlockRepository.saveAndFlush(any(IpBlock.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IpBlockResponse response = ipBlockService.blockIp(1L, ipAddress, "test", null);

        assertThat(response.getIpAddress()).isEqualTo(normalizedIpAddress);
        verify(ipBlockRepository).saveAndFlush(any(IpBlock.class));
    }

    @Test
    @DisplayName("Admin row가 없는 슈퍼 관리자도 처리자로 기록해 IP를 차단한다")
    void blockIp_withoutAdminRow_recordsProcessorUser() {
        String ipAddress = "127.0.0.1";
        when(moderationActorResolver.resolveModerationActor(1L))
                .thenReturn(new ModerationActorResolver.ModerationActor(adminUser, null));
        when(ipBlockRepository.findByIdForUpdate(ipAddress)).thenReturn(Optional.empty());
        when(ipBlockRepository.saveAndFlush(any(IpBlock.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IpBlockResponse response = ipBlockService.blockIp(1L, ipAddress, "test", null);

        assertThat(response.getAdmin().getAdminId()).isEqualTo(adminUser.getUserId());
        assertThat(response.getAdmin().getDisplayName()).isEqualTo(adminUser.getDisplayName());
        verify(ipBlockRepository).saveAndFlush(argThat(ipBlock ->
                ipBlock.getAdmin() == null && ipBlock.getProcessorUser().equals(adminUser)));
    }

    @Test
    @DisplayName("활성 관리자가 없으면 FORBIDDEN을 반환한다")
    void blockIp_forbiddenWhenNoActiveAdmin() {
        String ipAddress = "127.0.0.1";

        when(moderationActorResolver.resolveModerationActor(1L))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> ipBlockService.blockIp(1L, ipAddress, "test", null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

        verify(ipBlockRepository, never()).save(any(IpBlock.class));
    }

    @Test
    @DisplayName("권한 검증은 중복 차단 검사보다 먼저 수행한다")
    void blockIp_checksForbiddenBeforeDuplicate() {
        String ipAddress = "127.0.0.1";

        when(moderationActorResolver.resolveModerationActor(1L))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> ipBlockService.blockIp(1L, ipAddress, "test", NOW.plusDays(1)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

        verify(ipBlockRepository, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("과거 또는 현재 시각의 종료일은 허용하지 않는다")
    void blockIp_rejectsPastEndDate() {
        String ipAddress = "127.0.0.1";
        LocalDateTime invalidEndDate = NOW;

        assertThatThrownBy(() -> ipBlockService.blockIp(1L, ipAddress, "test", invalidEndDate))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);

        verify(ipBlockRepository, never()).save(any(IpBlock.class));
    }

    @Test
    @DisplayName("차단 사유가 너무 길면 검증 오류를 반환한다")
    void blockIp_rejectsTooLongReason() {
        String reason = "a".repeat(256);

        assertThatThrownBy(() -> ipBlockService.blockIp(1L, "127.0.0.1", reason, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);

        verify(ipBlockRepository, never()).findByIdForUpdate(any());
        verify(ipBlockRepository, never()).saveAndFlush(any(IpBlock.class));
    }

    @Test
    @DisplayName("IP 주소가 너무 길면 검증 오류를 반환한다")
    void blockIp_rejectsTooLongIpAddress() {
        assertThatThrownBy(() -> ipBlockService.blockIp(1L, "1".repeat(46), "test", null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);

        verify(ipBlockRepository, never()).findByIdForUpdate(any());
        verify(ipBlockRepository, never()).saveAndFlush(any(IpBlock.class));
    }

    @Test
    @DisplayName("CIDR 주소는 별도 설계 전까지 거부한다")
    void blockIp_rejectsCidrAddress() {
        assertThatThrownBy(() -> ipBlockService.blockIp(1L, "127.0.0.1/32", "test", null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);

        verify(ipBlockRepository, never()).findByIdForUpdate(any());
        verify(ipBlockRepository, never()).saveAndFlush(any(IpBlock.class));
    }

    @Test
    @DisplayName("IP 리터럴이 아닌 값은 거부한다")
    void blockIp_rejectsNonLiteralAddress() {
        assertThatThrownBy(() -> ipBlockService.blockIp(1L, "localhost", "test", null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);

        verify(ipBlockRepository, never()).findByIdForUpdate(any());
        verify(ipBlockRepository, never()).saveAndFlush(any(IpBlock.class));
    }

    @Test
    @DisplayName("활성 차단이 이미 있으면 중복으로 막는다")
    void blockIp_duplicateWhenActiveBlockExists() {
        String ipAddress = "127.0.0.1";
        IpBlock activeBlock = IpBlock.builder()
                .ipAddress(ipAddress)
                .admin(admin)
                .reason("existing")
                .startDate(NOW.minusHours(1))
                .endDate(null)
                .build();

        when(ipBlockRepository.findByIdForUpdate(ipAddress)).thenReturn(Optional.of(activeBlock));

        assertThatThrownBy(() -> ipBlockService.blockIp(1L, ipAddress, "test", null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_RESOURCE);

        verify(ipBlockRepository, never()).save(any(IpBlock.class));
    }

    @Test
    @DisplayName("만료된 차단 이력이 있으면 새 행 대신 재활성화한다")
    void blockIp_reusesExpiredBlock() {
        String ipAddress = "127.0.0.1";
        LocalDateTime expiredAt = NOW.minusDays(1);
        LocalDateTime newEndDate = NOW.plusDays(1);
        IpBlock expiredBlock = IpBlock.builder()
                .ipAddress(ipAddress)
                .admin(admin)
                .reason("old")
                .startDate(NOW.minusDays(2))
                .endDate(expiredAt)
                .build();

        when(ipBlockRepository.findByIdForUpdate(ipAddress)).thenReturn(Optional.of(expiredBlock));
        when(ipBlockRepository.saveAndFlush(expiredBlock)).thenReturn(expiredBlock);

        IpBlockResponse response = ipBlockService.blockIp(1L, ipAddress, "renewed", newEndDate);

        assertThat(response.getIpAddress()).isEqualTo(ipAddress);
        assertThat(expiredBlock.getReason()).isEqualTo("renewed");
        assertThat(expiredBlock.getEndDate()).isEqualTo(newEndDate);
        assertThat(expiredBlock.getStartDate()).isEqualTo(NOW);
        assertThat(expiredBlock.isActiveAt(NOW)).isTrue();
        verify(ipBlockRepository).saveAndFlush(expiredBlock);
    }

    @Test
    @DisplayName("동시 차단으로 저장 충돌이 나면 DUPLICATE_RESOURCE로 변환한다")
    void blockIp_mapsDuplicateSaveConflict() {
        String ipAddress = "127.0.0.1";

        when(ipBlockRepository.findByIdForUpdate(ipAddress)).thenReturn(Optional.empty());
        when(ipBlockRepository.saveAndFlush(any(IpBlock.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate ip block"));

        assertThatThrownBy(() -> ipBlockService.blockIp(1L, ipAddress, "test", null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_RESOURCE);
    }

    @Test
    @DisplayName("활성 차단 해제는 삭제 대신 만료 처리한다")
    void unblockIp_expiresActiveBlock() {
        String ipAddress = "127.0.0.1";
        IpBlock activeBlock = IpBlock.builder()
                .ipAddress(ipAddress)
                .admin(admin)
                .reason("test")
                .startDate(NOW.minusHours(1))
                .endDate(null)
                .build();

        when(ipBlockRepository.findActiveByIpAddress(eq(ipAddress), any(LocalDateTime.class)))
                .thenReturn(Optional.of(activeBlock));

        ipBlockService.unblockIp(" 127.0.0.1 ");

        assertThat(activeBlock.getEndDate()).isEqualTo(NOW);
        verify(ipBlockRepository, never()).delete(any());
    }

    @Test
    @DisplayName("활성 차단 목록만 페이지로 반환한다")
    void getBlockedIps_returnsActivePage() {
        IpBlock activeBlock = IpBlock.builder()
                .ipAddress("127.0.0.1")
                .admin(admin)
                .reason("test")
                .startDate(NOW)
                .endDate(null)
                .build();
        when(ipBlockRepository.findActiveBlocks(any(LocalDateTime.class), any()))
                .thenReturn(new PageImpl<>(List.of(activeBlock), PageRequest.of(0, 20), 1));

        var page = ipBlockService.getBlockedIps(PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getIpAddress()).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("IP 차단 목록은 페이지 요청이 없으면 기본 페이지 요청을 적용한다")
    void getBlockedIps_usesDefaultPageableWhenUnpaged() {
        when(ipBlockRepository.findActiveBlocks(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ipBlockService.getBlockedIps(Pageable.unpaged());

        Pageable pageable = captureBlockedIpsPageable();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().getOrderFor("startDate")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("startDate").isDescending()).isTrue();
        assertThat(pageable.getSort().getOrderFor("ipAddress")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("ipAddress").isDescending()).isTrue();
    }

    @Test
    @DisplayName("IP 차단 목록은 크기를 제한하고 허용되지 않은 정렬을 제거한다")
    void getBlockedIps_limitsSizeAndFiltersUnsupportedSort() {
        when(ipBlockRepository.findActiveBlocks(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 100), 0));

        ipBlockService.getBlockedIps(PageRequest.of(1, 250,
                Sort.by(Sort.Order.asc("reason"), Sort.Order.asc("endDate"))));

        Pageable pageable = captureBlockedIpsPageable();
        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(100);
        assertThat(pageable.getSort().getOrderFor("endDate")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("endDate").isAscending()).isTrue();
        assertThat(pageable.getSort().getOrderFor("ipAddress")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("ipAddress").isDescending()).isTrue();
        assertThat(pageable.getSort().getOrderFor("reason")).isNull();
    }

    @Test
    @DisplayName("IP 차단 여부는 활성 차단만 기준으로 판단한다")
    void isIpBlocked_checksOnlyActiveBlock() {
        when(ipBlockRepository.findActiveByIpAddress(eq("127.0.0.1"), any(LocalDateTime.class)))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(IpBlock.builder()
                        .ipAddress("127.0.0.1")
                        .admin(admin)
                        .reason("test")
                        .startDate(NOW)
                        .endDate(null)
                        .build()));

        assertThat(ipBlockService.isIpBlocked(" 127.0.0.1 ")).isFalse();
        assertThat(ipBlockService.isIpBlocked("127.0.0.1")).isTrue();
    }

    private Pageable captureBlockedIpsPageable() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(ipBlockRepository).findActiveBlocks(any(LocalDateTime.class), pageableCaptor.capture());
        return pageableCaptor.getValue();
    }
}
