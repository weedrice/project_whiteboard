package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.port.PostUserReadPort;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostReadContextResolverTest {
    @Mock
    private PostUserReadPort userReadPort;
    @Mock
    private AdminRepository adminRepository;

    private PostReadContextResolver resolver;
    private PostReadContext context;
    private Board publicBoard;

    @BeforeEach
    void setUp() {
        resolver = new PostReadContextResolver(userReadPort, adminRepository);
        User viewer = User.builder().loginId("viewer").build();
        ReflectionTestUtils.setField(viewer, "userId", 2L);
        context = new PostReadContext(viewer, 2L, List.of(), Set.of(), Set.of());
        publicBoard = board(10L, true);
    }

    @Test
    void boardContext_batchesAllDistinctBoardsIncludingPublicBoards() {
        Board privateBoard = board(20L, false);
        when(adminRepository.findActiveBoardIdsByUserIdAndBoardIds(2L, List.of(10L, 20L)))
                .thenReturn(List.of(10L));

        PostReadContext resolved = resolver.withAdminBoardIds(
                context, List.of(publicBoard, privateBoard, publicBoard));

        assertThat(resolved.activeAdminBoardIds()).containsExactly(10L);
        verify(adminRepository).findActiveBoardIdsByUserIdAndBoardIds(2L, List.of(10L, 20L));
        verifyNoMoreInteractions(adminRepository);
    }

    @Test
    void postVisibilityContext_skipsAdminLookupForOrdinaryPublicPosts() {
        Post ordinaryPost = Post.builder().board(publicBoard).userId(1L).isSecret(false).build();

        PostReadContext resolved = resolver.withAdminBoardIdsForPosts(context, List.of(ordinaryPost));

        assertThat(resolved.activeAdminBoardIds()).isEmpty();
        verifyNoInteractions(adminRepository);
    }

    @Test
    void postVisibilityContext_stillResolvesPublicSecretPostAdminAccess() {
        Post secretPost = Post.builder().board(publicBoard).userId(1L).isSecret(true).build();
        when(adminRepository.findActiveBoardIdsByUserIdAndBoardIds(2L, List.of(10L)))
                .thenReturn(List.of(10L));

        PostReadContext resolved = resolver.withAdminBoardIdsForPosts(context, List.of(secretPost));

        assertThat(resolved.activeAdminBoardIds()).containsExactly(10L);
        verify(adminRepository).findActiveBoardIdsByUserIdAndBoardIds(2L, List.of(10L));
        verifyNoMoreInteractions(adminRepository);
    }

    private Board board(Long boardId, boolean publicBoard) {
        Board board = Board.builder().boardName("Board " + boardId).build();
        ReflectionTestUtils.setField(board, "boardId", boardId);
        ReflectionTestUtils.setField(board, "isActive", true);
        ReflectionTestUtils.setField(board, "isPublic", publicBoard);
        return board;
    }
}
