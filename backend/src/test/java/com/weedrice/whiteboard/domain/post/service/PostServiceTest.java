package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.domain.post.dto.PostCreateRequest;
import com.weedrice.whiteboard.domain.post.dto.PostUpdateRequest;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.PostLikeId;
import com.weedrice.whiteboard.domain.post.repository.PostLikeRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.repository.PostVersionRepository;
import com.weedrice.whiteboard.domain.tag.service.TagService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BoardRepository boardRepository;
    @Mock
    private PostLikeRepository postLikeRepository;
    @Mock
    private TagService tagService;
    @Mock
    private PostVersionRepository postVersionRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private AdminRepository adminRepository;
    @Mock
    private PointService pointService;

    @InjectMocks
    private PostService postService;

    private User user;
    private Board board;
    private Post post;

    @BeforeEach
    void setUp() {
        user = User.builder().loginId("testuser").build();
        board = Board.builder().boardName("Test Board").creator(user).build();
        post = Post.builder().title("Test Post").contents("Test Contents").user(user).board(board).build();
    }

    @Test
    @DisplayName("게시글 생성 성공")
    void createPost_success() {
        // given
        Long userId = 1L;
        Long boardId = 1L;
        PostCreateRequest request = new PostCreateRequest(null, "New Post", "New Contents", Collections.emptyList(), false, false, false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(boardRepository.findById(boardId)).thenReturn(Optional.of(board));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        // when
        Post createdPost = postService.createPost(userId, boardId, request);

        // then
        assertThat(createdPost.getTitle()).isEqualTo("Test Post");
        verify(tagService).processTagsForPost(any(), any());
        verify(postVersionRepository).save(any());
        verify(pointService).addPoint(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("게시글 수정 성공")
    void updatePost_success() {
        // given
        Long userId = 1L;
        Long postId = 1L;
        PostUpdateRequest request = new PostUpdateRequest(null, "Updated Title", "Updated Contents", Collections.emptyList(), false, false);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // when
        Post updatedPost = postService.updatePost(userId, postId, request);

        // then
        assertThat(updatedPost.getTitle()).isEqualTo("Updated Title");
        verify(tagService).processTagsForPost(any(), any());
        verify(postVersionRepository).save(any());
    }

    @Test
    @DisplayName("게시글 좋아요 성공")
    void likePost_success() {
        // given
        Long userId = 1L;
        Long postId = 1L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(postLikeRepository.existsById(any(PostLikeId.class))).thenReturn(false);

        // when
        postService.likePost(userId, postId);

        // then
        verify(postLikeRepository).save(any());
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    @DisplayName("게시글 좋아요 취소 성공")
    void unlikePost_success() {
        // given
        Long userId = 1L;
        Long postId = 1L;
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(postLikeRepository.existsById(any(PostLikeId.class))).thenReturn(true);

        // when
        postService.unlikePost(userId, postId);

        // then
        verify(postLikeRepository).deleteById(any());
    }
}
