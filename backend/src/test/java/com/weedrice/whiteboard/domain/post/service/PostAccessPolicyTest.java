package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.constant.BoardPolicyConstants;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.inquiry.legacy.InquiryLegacyWritePolicy;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class PostAccessPolicyTest {

    private AdminRepository adminRepository;
    private InquiryLegacyWritePolicy inquiryLegacyWritePolicy;
    private PostAccessPolicy postAccessPolicy;
    private User viewer;
    private User author;

    @BeforeEach
    void setUp() {
        adminRepository = mock(AdminRepository.class);
        inquiryLegacyWritePolicy = mock(InquiryLegacyWritePolicy.class);
        org.mockito.Mockito.when(inquiryLegacyWritePolicy.areLegacyWritesEnabled()).thenReturn(true);
        postAccessPolicy = new PostAccessPolicy(
                new BoardAccessPolicy(adminRepository),
                inquiryLegacyWritePolicy);
        viewer = user(1L, "viewer");
        author = user(2L, "author");
    }

    @Test
    void isReadable_returnsTrueWhenValidateReadableAllowsAccess() {
        Post post = post(board(10L, "public-board", true, true), author, false);

        assertThat(postAccessPolicy.isReadable(post, viewer)).isTrue();
        postAccessPolicy.validateReadable(post, viewer);
    }

    @Test
    void isReadable_doesNotLookupAdminAccessForPublicNonSecretPost() {
        Post post = post(board(10L, "public-board", true, true), author, false);

        assertThat(postAccessPolicy.isReadable(post, viewer)).isTrue();

        verifyNoInteractions(adminRepository);
    }

    @Test
    void isReadable_doesNotLookupAdminAccessForOwnSecretPost() {
        Post post = post(board(10L, "public-board", true, true), viewer, true);

        assertThat(postAccessPolicy.isReadable(post, viewer)).isTrue();

        verifyNoInteractions(adminRepository);
    }

    @Test
    void isReadable_doesNotLookupAdminAccessForOwnPrivateInquiryPost() {
        Post post = post(board(10L, BoardPolicyConstants.INQUIRY_BOARD_URL, true, false), viewer, false);

        assertThat(postAccessPolicy.isReadable(post, viewer)).isTrue();

        verifyNoInteractions(adminRepository);
    }

    @Test
    void isReadable_returnsFalseWhenValidateReadableRejectsAccess() {
        Post post = post(board(10L, "public-board", true, true), author, false);

        assertThat(postAccessPolicy.isReadable(post, viewer, true)).isFalse();
        assertThatThrownBy(() -> postAccessPolicy.validateReadable(post, viewer, true))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POST_NOT_FOUND);
    }

    @Test
    void isReadable_allowsPrivateInquiryBoardForAuthor() {
        Post post = post(board(10L, BoardPolicyConstants.INQUIRY_BOARD_URL, true, false), author, false);

        assertThat(postAccessPolicy.isReadable(post, author, false, Set.of())).isTrue();
    }

    @Test
    void isReadable_rejectsLegacyInquiryAuthorAfterWriteCutover() {
        org.mockito.Mockito.when(inquiryLegacyWritePolicy.areLegacyWritesEnabled()).thenReturn(false);
        Post post = post(board(10L, BoardPolicyConstants.INQUIRY_BOARD_URL, true, false), author, false);

        assertThat(postAccessPolicy.isReadable(post, author, false, Set.of())).isFalse();
    }

    @Test
    void isReadable_allowsUsableSuperAdminAfterLegacyInquiryWriteCutover() {
        org.mockito.Mockito.when(inquiryLegacyWritePolicy.areLegacyWritesEnabled()).thenReturn(false);
        User superAdmin = user(3L, "super-admin");
        superAdmin.grantSuperAdminRole();
        Post post = post(board(10L, BoardPolicyConstants.INQUIRY_BOARD_URL, true, false), author, false);

        assertThat(postAccessPolicy.isReadable(post, superAdmin, true, Set.of())).isTrue();
    }

    @Test
    void isReadable_allowsInactiveSecretPostForPrecomputedBoardAdmin() {
        Post post = post(board(10L, "admin-board", false, true), author, true);

        assertThat(postAccessPolicy.isReadable(post, viewer, false, Set.of(10L))).isTrue();
    }

    @Test
    void isReadable_rejectsInactiveBoardForNonAuthorWithoutAdminAccess() {
        Post post = post(board(10L, "inactive-board", false, true), author, false);

        assertThat(postAccessPolicy.isReadable(post, viewer, false, Set.of())).isFalse();
    }

    @Test
    void isReadable_rejectsBlindedPostForAuthorAndBoardAdmin() {
        Post post = post(board(10L, "public-board", true, true), viewer, false);
        post.blind("reported", java.time.LocalDateTime.now());

        assertThat(postAccessPolicy.isReadable(post, viewer, false, Set.of(10L))).isFalse();
        assertThatThrownBy(() -> postAccessPolicy.validateReadable(post, viewer, false, Set.of(10L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POST_NOT_FOUND);
    }

    private User user(Long userId, String loginId) {
        User user = User.builder()
                .loginId(loginId)
                .email(loginId + "@test.com")
                .password("password")
                .displayName(loginId)
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);
        return user;
    }

    private Board board(Long boardId, String boardUrl, boolean isActive, boolean isPublic) {
        Board board = Board.builder()
                .boardName(boardUrl)
                .boardUrl(boardUrl)
                .creator(author)
                .isPublic(isPublic)
                .build();
        ReflectionTestUtils.setField(board, "boardId", boardId);
        if (!isActive) {
            board.deactivate();
        }
        return board;
    }

    private Post post(Board board, User author, boolean isSecret) {
        return Post.builder()
                .board(board)
                .user(author)
                .title("title")
                .contents("contents")
                .isSecret(isSecret)
                .build();
    }
}
