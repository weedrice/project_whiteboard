package com.weedrice.whiteboard.domain.sanction.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.service.ModerationActorResolver;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.comment.service.CommentReadSupport;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.sanction.dto.SanctionResponse;
import com.weedrice.whiteboard.domain.sanction.entity.Sanction;
import com.weedrice.whiteboard.domain.sanction.repository.SanctionRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserLifecycleService;
import com.weedrice.whiteboard.domain.user.service.UserReadableResolver;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SanctionServiceTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 7, 7, 12, 0);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            FIXED_NOW.toInstant(ZoneOffset.UTC),
            ZoneOffset.UTC);

    @Mock private SanctionRepository sanctionRepository;
    @Mock private UserRepository userRepository;
    @Mock private PostRepository postRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private ModerationActorResolver moderationActorResolver;
    @Mock private UserLifecycleService userLifecycleService;
    @Mock private SanctionPolicyService sanctionPolicyService;
    @Mock private UserReadableResolver userReadableResolver;

    private SanctionService sanctionService;

    private User adminUser;
    private User targetUser;
    private Admin admin;

    @BeforeEach
    void setUp() {
        adminUser = User.builder().build();
        ReflectionTestUtils.setField(adminUser, "userId", 1L);
        adminUser.grantSuperAdminRole();

        targetUser = User.builder().build();
        ReflectionTestUtils.setField(targetUser, "userId", 2L);

        admin = Admin.builder().user(adminUser).build();
        ReflectionTestUtils.setField(admin, "adminId", 10L);

        sanctionService = new SanctionService(
                sanctionRepository,
                moderationActorResolver,
                sanctionPolicyService,
                new SanctionRequestValidator(FIXED_CLOCK),
                new SanctionTargetResolver(userRepository, postRepository, new CommentReadSupport(commentRepository)),
                new SanctionEffectApplier(userLifecycleService),
                userReadableResolver,
                FIXED_CLOCK);
        lenient().when(moderationActorResolver.resolveModerationActor(1L))
                .thenReturn(new ModerationActorResolver.ModerationActor(adminUser, admin));
    }

    private PageRequest defaultSanctionPageable() {
        return PageRequest.of(0, 20,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("sanctionId")));
    }

    @Test
    @DisplayName("create sanction succeeds")
    void createSanction_success() {
        when(moderationActorResolver.resolveModerationActor(1L))
                .thenReturn(new ModerationActorResolver.ModerationActor(adminUser, admin));
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(targetUser));
        when(postRepository.findById(100L)).thenReturn(Optional.of(Post.builder()
                .user(targetUser)
                .title("Post")
                .contents("Content")
                .build()));

        Sanction savedSanction = Sanction.builder()
                .targetUser(targetUser)
                .admin(admin)
                .type("BAN")
                .remark("Test")
                .startDate(FIXED_NOW)
                .build();
        ReflectionTestUtils.setField(savedSanction, "sanctionId", 1L);
        when(sanctionRepository.save(any(Sanction.class))).thenReturn(savedSanction);

        Long sanctionId = sanctionService.createSanction(1L, 2L, "BAN", "Test", null, 100L, "post");

        assertThat(sanctionId).isEqualTo(1L);
        verify(userRepository).findByIdForUpdate(2L);
        verify(userLifecycleService).suspendPrelockedUser(targetUser);
        verify(userLifecycleService, never()).suspendUser(any(User.class));
        verify(sanctionRepository).save(argThat(sanction ->
                Long.valueOf(100L).equals(sanction.getContentId())
                        && "POST".equals(sanction.getContentType())
                        && FIXED_NOW.equals(sanction.getStartDate())));
    }

    @Test
    @DisplayName("temporary ban keeps user status active")
    void createSanction_temporaryBan_keepsUserStatusActive() {
        when(moderationActorResolver.resolveModerationActor(1L))
                .thenReturn(new ModerationActorResolver.ModerationActor(adminUser, admin));
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(targetUser));

        Sanction savedSanction = Sanction.builder()
                .targetUser(targetUser)
                .admin(admin)
                .type("BAN")
                .remark("Temp ban")
                .startDate(FIXED_NOW)
                .endDate(FIXED_NOW.plusDays(1))
                .build();
        ReflectionTestUtils.setField(savedSanction, "sanctionId", 2L);
        when(sanctionRepository.save(any(Sanction.class))).thenReturn(savedSanction);

        sanctionService.createSanction(1L, 2L, "BAN", "Temp ban", FIXED_NOW.plusDays(1), null, null);

        assertThat(targetUser.getStatus()).isEqualTo("ACTIVE");
        verify(userLifecycleService, never()).suspendPrelockedUser(any(User.class));
    }

    @Test
    @DisplayName("permanent ban rejects deleted users")
    void createSanction_permanentBanRejectsDeletedUser() {
        targetUser.delete(java.time.LocalDateTime.of(2026, 7, 7, 12, 0));
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(targetUser));

        assertThatThrownBy(() -> sanctionService.createSanction(1L, 2L, "BAN", "Deleted user", null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_ACTIVE);

        verify(userLifecycleService, never()).suspendPrelockedUser(any(User.class));
    }

    @Test
    @DisplayName("create sanction normalizes type to uppercase")
    void createSanction_normalizesTypeToUpperCase() {
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(targetUser));

        Sanction savedSanction = Sanction.builder()
                .targetUser(targetUser)
                .admin(admin)
                .type("BAN")
                .remark("Temp ban")
                .startDate(FIXED_NOW)
                .endDate(FIXED_NOW.plusDays(1))
                .build();
        ReflectionTestUtils.setField(savedSanction, "sanctionId", 3L);
        when(sanctionRepository.save(any(Sanction.class))).thenReturn(savedSanction);

        sanctionService.createSanction(1L, 2L, "ban", "Temp ban", FIXED_NOW.plusDays(1), null, null);

        verify(sanctionRepository).save(argThat(sanction -> "BAN".equals(sanction.getType())));
    }

    @Test
    @DisplayName("create sanction trims remark before save")
    void createSanction_trimsRemarkBeforeSave() {
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(targetUser));

        Sanction savedSanction = Sanction.builder()
                .targetUser(targetUser)
                .admin(admin)
                .type("WARNING")
                .remark("Trimmed")
                .startDate(FIXED_NOW)
                .build();
        ReflectionTestUtils.setField(savedSanction, "sanctionId", 4L);
        when(sanctionRepository.save(any(Sanction.class))).thenReturn(savedSanction);

        sanctionService.createSanction(1L, 2L, "WARNING", "  Trimmed  ", null, null, null);

        verify(sanctionRepository).save(argThat(sanction -> "Trimmed".equals(sanction.getRemark())));
    }

    @Test
    @DisplayName("create sanction rejects overlong remark")
    void createSanction_rejectsOverlongRemark() {

        assertThatThrownBy(() -> sanctionService.createSanction(
                1L, 2L, "WARNING", "a".repeat(256), null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(sanctionRepository, never()).save(any(Sanction.class));
    }

    @Test
    @DisplayName("reject endDate when it is not in the future")
    void createSanction_rejectsPastOrImmediateEndDate() {

        assertThatThrownBy(() -> sanctionService.createSanction(1L, 2L, "BAN", "Expired", FIXED_NOW, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        assertThatThrownBy(() -> sanctionService.createSanction(
                1L, 2L, "MUTE", "Expired", FIXED_NOW.minusMinutes(1), null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        assertThatThrownBy(() -> sanctionService.createSanction(
                1L, 2L, "WARNING", "Expired", FIXED_NOW.minusMinutes(1), null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("reject unsupported sanction type")
    void createSanction_rejectsUnsupportedType() {

        assertThatThrownBy(() -> sanctionService.createSanction(1L, 2L, "BLOCK", "Invalid", null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("reject incomplete sanction content metadata")
    void createSanction_rejectsIncompleteContentMetadata() {

        assertThatThrownBy(() -> sanctionService.createSanction(1L, 2L, "WARNING", "Invalid", null, 100L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        assertThatThrownBy(() -> sanctionService.createSanction(1L, 2L, "WARNING", "Invalid", null, null, "POST"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("reject unsupported sanction content type")
    void createSanction_rejectsUnsupportedContentType() {

        assertThatThrownBy(() -> sanctionService.createSanction(1L, 2L, "WARNING", "Invalid", null, 100L, "ARTICLE"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("reject sanction without target user id")
    void createSanction_rejectsNullTargetUserId() {

        assertThatThrownBy(() -> sanctionService.createSanction(1L, null, "WARNING", "Invalid", null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(userRepository, never()).findByIdForUpdate(any());
        verify(sanctionRepository, never()).save(any(Sanction.class));
    }

    @Test
    @DisplayName("reject sanction when post target is missing")
    void createSanction_rejectsMissingPostTarget() {
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(targetUser));
        when(postRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sanctionService.createSanction(1L, 2L, "WARNING", "Invalid", null, 100L, "POST"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POST_NOT_FOUND);

        verify(sanctionRepository, never()).save(any(Sanction.class));
    }

    @Test
    @DisplayName("reject sanction when post target is deleted")
    void createSanction_rejectsDeletedPostTarget() {
        Post post = Post.builder()
                .user(targetUser)
                .title("Post")
                .contents("Content")
                .build();
        post.deletePost();
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(targetUser));
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> sanctionService.createSanction(1L, 2L, "WARNING", "Invalid", null, 100L, "POST"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POST_NOT_FOUND);

        verify(sanctionRepository, never()).save(any(Sanction.class));
    }

    @Test
    @DisplayName("reject sanction when post owner differs from target user")
    void createSanction_rejectsPostOwnerMismatch() {
        User otherUser = User.builder().build();
        ReflectionTestUtils.setField(otherUser, "userId", 3L);
        Post post = Post.builder()
                .user(otherUser)
                .title("Post")
                .contents("Content")
                .build();
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(targetUser));
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> sanctionService.createSanction(1L, 2L, "WARNING", "Invalid", null, 100L, "POST"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(sanctionRepository, never()).save(any(Sanction.class));
    }

    @Test
    @DisplayName("reject sanction when comment target is missing or deleted")
    void createSanction_rejectsMissingCommentTarget() {
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(targetUser));
        when(commentRepository.findNonDeletedByIdWithRelations(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sanctionService.createSanction(1L, 2L, "WARNING", "Invalid", null, 100L, "COMMENT"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMENT_NOT_FOUND);

        verify(sanctionRepository, never()).save(any(Sanction.class));
    }

    @Test
    @DisplayName("reject sanction when comment post is deleted")
    void createSanction_rejectsCommentOnDeletedPost() {
        Post post = Post.builder()
                .user(targetUser)
                .title("Post")
                .contents("Content")
                .build();
        post.deletePost();
        Comment comment = Comment.builder()
                .post(post)
                .user(targetUser)
                .depth(0)
                .content("Comment")
                .build();
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(targetUser));
        when(commentRepository.findNonDeletedByIdWithRelations(100L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> sanctionService.createSanction(1L, 2L, "WARNING", "Invalid", null, 100L, "COMMENT"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMENT_NOT_FOUND);

        verify(sanctionRepository, never()).save(any(Sanction.class));
    }

    @Test
    @DisplayName("reject sanction when user content target is inactive")
    void createSanction_rejectsInactiveUserContentTarget() {
        User inactiveUser = User.builder().build();
        ReflectionTestUtils.setField(inactiveUser, "userId", 3L);
        inactiveUser.suspend();
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(targetUser));
        when(userRepository.findById(3L)).thenReturn(Optional.of(inactiveUser));

        assertThatThrownBy(() -> sanctionService.createSanction(1L, 2L, "WARNING", "Invalid", null, 3L, "USER"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_ACTIVE);

        verify(sanctionRepository, never()).save(any(Sanction.class));
    }

    @Test
    @DisplayName("create sanction without admin row records processor user")
    void createSanction_withoutAdmin_recordsProcessorUser() {
        when(moderationActorResolver.resolveModerationActor(1L))
                .thenReturn(new ModerationActorResolver.ModerationActor(adminUser, null));
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(targetUser));
        Sanction savedSanction = Sanction.builder()
                .targetUser(targetUser)
                .processorUser(adminUser)
                .type("BAN")
                .remark("Test")
                .startDate(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(savedSanction, "sanctionId", 10L);
        when(sanctionRepository.save(any(Sanction.class))).thenReturn(savedSanction);

        Long sanctionId = sanctionService.createSanction(1L, 2L, "BAN", "Test", null, null, null);

        assertThat(sanctionId).isEqualTo(10L);
        verify(sanctionRepository).save(argThat(sanction ->
                sanction.getAdmin() == null && sanction.getProcessorUser().equals(adminUser)));
    }

    @Test
    @DisplayName("get sanctions returns mapped responses")
    void getSanctions_returnsMappedResponses() {

        Sanction sanction = Sanction.builder()
                .targetUser(targetUser)
                .admin(admin)
                .type("BAN")
                .remark("Test")
                .startDate(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(sanction, "sanctionId", 1L);

        PageRequest pageable = PageRequest.of(0, 20);
        PageRequest safePageable = defaultSanctionPageable();
        when(sanctionRepository.findAll(safePageable))
                .thenReturn(new PageImpl<>(List.of(sanction), safePageable, 1));

        Page<SanctionResponse> responses = sanctionService.getSanctions(null, pageable);

        assertThat(responses.getContent()).hasSize(1);
        assertThat(responses.getContent().get(0).getSanctionId()).isEqualTo(1L);
        assertThat(responses.getContent().get(0).getAdminId()).isEqualTo(admin.getAdminId());
        verify(sanctionRepository).findAll(safePageable);
    }

    @Test
    @DisplayName("get sanctions uses stable default sort when pageable is unsorted")
    void getSanctions_appliesStableDefaultSort() {
        PageRequest requestedPageable = PageRequest.of(0, 20);
        PageRequest safePageable = defaultSanctionPageable();
        when(sanctionRepository.findAll(safePageable))
                .thenReturn(new PageImpl<>(List.of(), safePageable, 0));

        sanctionService.getSanctions(null, requestedPageable);

        verify(sanctionRepository).findAll(safePageable);
    }

    @Test
    @DisplayName("get sanctions by target user uses stable default sort when pageable is unsorted")
    void getSanctionsByTargetUser_appliesStableDefaultSort() {
        PageRequest requestedPageable = PageRequest.of(0, 20);
        PageRequest safePageable = defaultSanctionPageable();
        when(sanctionRepository.findByTargetUser_UserId(2L, safePageable))
                .thenReturn(new PageImpl<>(List.of(), safePageable, 0));

        sanctionService.getSanctions(2L, requestedPageable);

        verify(sanctionRepository).findByTargetUser_UserId(2L, safePageable);
        verify(userReadableResolver).ensureExists(2L);
    }

    @Test
    @DisplayName("get sanctions by missing target user preserves USER_NOT_FOUND")
    void getSanctionsByTargetUser_missingUser_throwsUserNotFound() {
        PageRequest requestedPageable = PageRequest.of(0, 20);
        PageRequest safePageable = defaultSanctionPageable();
        when(sanctionRepository.findByTargetUser_UserId(999L, safePageable))
                .thenReturn(new PageImpl<>(List.of(), safePageable, 0));
        doThrow(new BusinessException(ErrorCode.USER_NOT_FOUND))
                .when(userReadableResolver).ensureExists(999L);

        assertThatThrownBy(() -> sanctionService.getSanctions(999L, requestedPageable))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("isUserBanned returns true when an active ban exists")
    void isUserBanned_trueWhenActiveBanExists() {
        when(sanctionPolicyService.isUserBanned(targetUser)).thenReturn(true);

        assertThat(sanctionService.isUserBanned(targetUser)).isTrue();
    }

    @Test
    @DisplayName("isUserMuted returns true when an active mute exists")
    void isUserMuted_trueWhenActiveMuteExists() {
        when(sanctionPolicyService.isUserMuted(targetUser)).thenReturn(true);

        assertThat(sanctionService.isUserMuted(targetUser)).isTrue();
    }

    @Test
    @DisplayName("inactive user is rejected by write validation")
    void validateNotBanned_rejectsInactiveUser() {
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE))
                .when(sanctionPolicyService).validateNotBanned(targetUser);

        assertThatThrownBy(() -> sanctionService.validateNotBanned(targetUser))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_ACTIVE);
    }

    @Test
    @DisplayName("muted user is rejected by write validation")
    void validateNotMuted_rejectsMutedUser() {
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE))
                .when(sanctionPolicyService).validateNotMuted(targetUser);

        assertThatThrownBy(() -> sanctionService.validateNotMuted(targetUser))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_ACTIVE);
    }
}
