package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.search.semantic.SemanticRelatedPostResult;
import com.weedrice.whiteboard.domain.search.semantic.SemanticRelatedPostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
                semanticRelatedPostService,
                postFacadeReadService);
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(sourcePost));
        when(sourcePost.getPostId()).thenReturn(1L);
        when(sourcePost.getBoard()).thenReturn(board);
        when(board.getBoardUrl()).thenReturn("free");
        when(postReadContextResolver.resolve(null)).thenReturn(PostReadContext.anonymous());
        when(postReadContextResolver.withAdminBoardIdsForPosts(
                PostReadContext.anonymous(),
                List.of(sourcePost)))
                .thenReturn(PostReadContext.anonymous());
    }

    @Test
    void getRelatedPosts_doesNotFallbackWhenSourceEmbeddingHasNoSimilarMatches() {
        when(semanticRelatedPostService.findRelatedPostIds(1L, "free", null, 3))
                .thenReturn(new SemanticRelatedPostResult(true, List.of()));

        assertThat(service.getRelatedPosts(1L, null, 3)).isEmpty();

        verify(postRepository, never()).findPublicRelatedFallbackPostIds(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());
        verify(postFacadeReadService, never()).getPostSummariesByIds(anyList(), nullable(Long.class));
    }

    @Test
    void getRelatedPosts_usesFallbackOnlyWhenSourceEmbeddingIsUnavailable() {
        when(semanticRelatedPostService.findRelatedPostIds(1L, "free", null, 3))
                .thenReturn(new SemanticRelatedPostResult(false, List.of()));
        when(board.getBoardId()).thenReturn(10L);
        when(postRepository.findPublicRelatedFallbackPostIds(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        assertThat(service.getRelatedPosts(1L, null, 3)).isEmpty();

        verify(postRepository).findPublicRelatedFallbackPostIds(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any());
    }
}
