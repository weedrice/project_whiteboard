package com.weedrice.whiteboard.domain.tag.service;

import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.tag.entity.PostTag;
import com.weedrice.whiteboard.domain.tag.entity.Tag;
import com.weedrice.whiteboard.domain.tag.repository.PostTagRepository;
import com.weedrice.whiteboard.domain.tag.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    private TagRepository tagRepository;
    @Mock
    private PostTagRepository postTagRepository;

    @InjectMocks
    private TagService tagService;

    private Post post;
    private Tag existingTag;
    private PostTag existingPostTag;

    @BeforeEach
    void setUp() {
        post = Post.builder().build();
        existingTag = new Tag("existingTag");
        existingPostTag = PostTag.builder().post(post).tag(existingTag).build();
    }

    @Test
    @DisplayName("태그 처리 성공 - 신규 태그 추가")
    void processTagsForPost_addNewTag() {
        // given
        when(postTagRepository.findByPost(post)).thenReturn(Collections.emptyList());
        when(tagRepository.findByTagName("newTag")).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenReturn(new Tag("newTag"));

        // when
        tagService.processTagsForPost(post, Collections.singletonList("newTag"));

        // then
        verify(postTagRepository, times(1)).save(any(PostTag.class));
    }

    @Test
    @DisplayName("태그 처리 성공 - 기존 태그 삭제")
    void processTagsForPost_removeOldTag() {
        // given
        when(postTagRepository.findByPost(post)).thenReturn(Collections.singletonList(existingPostTag));

        // when
        tagService.processTagsForPost(post, Collections.emptyList());

        // then
        verify(postTagRepository, times(1)).delete(any(PostTag.class));
    }

    @Test
    @DisplayName("태그 처리 성공 - 태그 변경 없음")
    void processTagsForPost_noChange() {
        // given
        when(postTagRepository.findByPost(post)).thenReturn(Collections.singletonList(existingPostTag));

        // when
        tagService.processTagsForPost(post, Collections.singletonList("existingTag"));

        // then
        verify(postTagRepository, never()).save(any());
        verify(postTagRepository, never()).delete(any());
    }

    @Test
    @DisplayName("태그 처리 성공 - 태그 추가 및 삭제")
    void processTagsForPost_addAndRemove() {
        // given
        Tag tagToRemove = new Tag("tagToRemove");
        PostTag postTagToRemove = PostTag.builder().post(post).tag(tagToRemove).build();
        when(postTagRepository.findByPost(post)).thenReturn(Arrays.asList(existingPostTag, postTagToRemove));
        when(tagRepository.findByTagName("newTag")).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenReturn(new Tag("newTag"));

        // when
        tagService.processTagsForPost(post, Arrays.asList("existingTag", "newTag"));

        // then
        verify(postTagRepository, times(1)).save(any(PostTag.class));
        verify(postTagRepository, times(1)).delete(postTagToRemove);
    }
}
