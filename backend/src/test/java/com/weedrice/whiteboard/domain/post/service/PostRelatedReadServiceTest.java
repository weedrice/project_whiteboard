package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.actor.ActorUserPrincipal;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.search.semantic.SemanticRelatedPostResult;
import com.weedrice.whiteboard.domain.search.semantic.SemanticRelatedPostService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostRelatedReadServiceTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private PostReadContextResolver postReadContextResolver;
    @Mock
    private PostAccessPolicy postAccessPolicy;
    @Mock
    private SemanticRelatedPostService semanticRelatedPostService;
    @Mock
    private PostFacadeReadService postFacadeReadService;
    @Mock
    private Post sourcePost;
    @Mock
    private Board board;

    private PostRelatedReadService service;

    @BeforeEach
    void setUp() {
        service = new PostRelatedReadService(
                postRepository,
                postReadContextResolver,
                postAccessPolicy,
                new BoardAccessPolicy(mock(AdminRepository.class)),
                semanticRelatedPostService,
                postFacadeReadService);
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(sourcePost));
    }

    @Test
    void getRelatedPosts_doesNotFallbackWhenSourceEmbeddingHasNoSimilarMatches() {
        stubIndexedBoard();
        stubContext(null, PostReadContext.anonymous());
        when(semanticRelatedPostService.findRelatedPostIds(1L, "free", null, 3))
                .thenReturn(new SemanticRelatedPostResult(true, List.of()));

        assertThat(service.getRelatedPosts(1L, null, 3)).isEmpty();

        verify(postRepository, never()).findPublicRelatedFallbackPostIds(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyCollection(),
                org.mockito.ArgumentMatchers.any());
        verify(postFacadeReadService, never()).getPostSummariesByIds(anyList(), nullable(Long.class));
    }

    @Test
    void getRelatedPosts_usesFallbackOnlyWhenSourceEmbeddingIsUnavailable() {
        stubIndexedBoard();
        stubContext(null, PostReadContext.anonymous());
        when(semanticRelatedPostService.findRelatedPostIds(1L, "free", null, 3))
                .thenReturn(new SemanticRelatedPostResult(false, List.of()));
        when(board.getBoardId()).thenReturn(10L);
        when(postRepository.findPublicRelatedFallbackPostIds(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(List.of(-1L)),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        assertThat(service.getRelatedPosts(1L, null, 3)).isEmpty();

        verify(postRepository).findPublicRelatedFallbackPostIds(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(List.of(-1L)),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getRelatedPosts_passesBlockedAuthorsIntoFallbackCandidateQuery() {
        stubIndexedBoard();
        ActorUserPrincipal viewer = org.mockito.Mockito.mock(ActorUserPrincipal.class);
        List<Long> blockedUserIds = List.of(9L, 11L);
        PostReadContext context = new PostReadContext(viewer, 7L, blockedUserIds, Set.copyOf(blockedUserIds), Set.of());
        stubContext(7L, context);
        when(semanticRelatedPostService.findRelatedPostIds(1L, "free", 7L, 3))
                .thenReturn(new SemanticRelatedPostResult(false, List.of()));
        when(board.getBoardId()).thenReturn(10L);
        when(postRepository.findPublicRelatedFallbackPostIds(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(blockedUserIds),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        assertThat(service.getRelatedPosts(1L, 7L, 3)).isEmpty();

        verify(postRepository).findPublicRelatedFallbackPostIds(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(blockedUserIds),
                org.mockito.ArgumentMatchers.eq(org.springframework.data.domain.PageRequest.of(0, 3)));
    }

    @ParameterizedTest
    @CsvSource({
            "false, true, free",
            "true, false, free",
            "true, true, inquiry"
    })
    void getRelatedPosts_omitsRecommendationsForReadableUnindexedBoards(
            boolean active, boolean publicBoard, String boardUrl) {
        Board sourceBoard = Board.builder().boardUrl(boardUrl).isPublic(publicBoard).build();
        if (!active) {
            sourceBoard.deactivate();
        }
        when(sourcePost.getBoard()).thenReturn(sourceBoard);
        stubContext(null, PostReadContext.anonymous());

        assertThat(service.getRelatedPosts(1L, null, 3)).isEmpty();

        verify(postAccessPolicy).validateReadable(sourcePost, null, false, Set.of());
        verifyNoInteractions(semanticRelatedPostService, postFacadeReadService);
        verify(postRepository, never()).findPublicRelatedFallbackPostIds(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyCollection(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getRelatedPosts_preservesSourceReadDenialBeforeSkippingRecommendations() {
        stubContext(null, PostReadContext.anonymous());
        doThrow(new BusinessException(ErrorCode.POST_NOT_FOUND))
                .when(postAccessPolicy).validateReadable(sourcePost, null, false, Set.of());

        assertThatThrownBy(() -> service.getRelatedPosts(1L, null, 3))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.POST_NOT_FOUND);

        verifyNoInteractions(semanticRelatedPostService, postFacadeReadService);
        verify(sourcePost, never()).getBoard();
    }

    private void stubIndexedBoard() {
        when(sourcePost.getPostId()).thenReturn(1L);
        when(sourcePost.getBoard()).thenReturn(board);
        when(board.getBoardUrl()).thenReturn("free");
        when(board.getIsActive()).thenReturn(true);
        when(board.getIsPublic()).thenReturn(true);
    }

    private void stubContext(Long viewerId, PostReadContext context) {
        when(postReadContextResolver.resolve(viewerId)).thenReturn(context);
        when(postReadContextResolver.withAdminBoardIdsForPosts(context, List.of(sourcePost))).thenReturn(context);
    }
}
