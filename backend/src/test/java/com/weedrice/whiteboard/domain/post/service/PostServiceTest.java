package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.post.dto.PostCreateRequest;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
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
    private TagService tagService;

    @InjectMocks
    private PostService postService;

    private User user;
    private Board board;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .loginId("testuser")
                .password("password")
                .email("test@test.com")
                .displayName("Test User")
                .build();

        board = Board.builder()
                .boardName("Test Board")
                .creator(user)
                .build();
    }

    @Test
    @DisplayName("게시글 생성 성공")
    void createPost_success() {
        // given
        PostCreateRequest request = new PostCreateRequest(null, "Test Title", "Test Content", Collections.singletonList("tag1"), false, false);
        Post post = Post.builder()
                .board(board)
                .user(user)
                .title(request.getTitle())
                .contents(request.getContents())
                .build();

        when(userRepository.findById(any())).thenReturn(Optional.of(user));
        when(boardRepository.findById(any())).thenReturn(Optional.of(board));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        // when
        Post createdPost = postService.createPost(1L, 1L, request);

        // then
        assertThat(createdPost.getTitle()).isEqualTo(request.getTitle());
        assertThat(createdPost.getContents()).isEqualTo(request.getContents());
        verify(tagService).processTagsForPost(any(Post.class), any());
    }
}
