package com.weedrice.whiteboard.domain.tag.service;

import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.tag.entity.PostTag;
import com.weedrice.whiteboard.domain.tag.entity.Tag;
import com.weedrice.whiteboard.domain.tag.repository.PostTagRepository;
import com.weedrice.whiteboard.domain.tag.repository.TagRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagAssignmentServiceTest {

    @Mock
    private TagRepository tagRepository;
    @Mock
    private PostTagRepository postTagRepository;

    @InjectMocks
    private TagAssignmentService tagAssignmentService;

    private Post post;
    private Tag existingTag;
    private PostTag existingPostTag;

    @BeforeEach
    void setUp() {
        post = Post.builder().build();
        existingTag = new Tag("existingTag");
        ReflectionTestUtils.setField(existingTag, "tagId", 10L);
        existingPostTag = PostTag.builder().post(post).tag(existingTag).build();
    }

    @Test
    @DisplayName("assignTags adds new tag")
    void assignTags_addNewTag() {
        when(postTagRepository.findByPost(post)).thenReturn(Collections.emptyList());
        Tag savedTag = new Tag("newTag");
        ReflectionTestUtils.setField(savedTag, "tagId", 20L);
        when(tagRepository.findByTagNameInForUpdate(Collections.singleton("newTag")))
                .thenReturn(List.of(), List.of(savedTag));

        tagAssignmentService.assignTags(post, Collections.singletonList("newTag"));

        verify(tagRepository).insertIgnore("newTag");
        verify(postTagRepository).save(any(PostTag.class));
        verify(tagRepository).incrementPostCountIn(List.of(20L));
    }

    @Test
    @DisplayName("assignTags reuses tag loaded after duplicate insert-ignore")
    void assignTags_reusesTagLoadedAfterDuplicateInsertIgnore() {
        when(postTagRepository.findByPost(post)).thenReturn(Collections.emptyList());
        Tag concurrentlyCreatedTag = new Tag("newTag");
        ReflectionTestUtils.setField(concurrentlyCreatedTag, "tagId", 20L);
        when(tagRepository.findByTagNameInForUpdate(Collections.singleton("newTag")))
                .thenReturn(List.of(), List.of(concurrentlyCreatedTag));
        when(tagRepository.insertIgnore("newTag")).thenReturn(0);

        tagAssignmentService.assignTags(post, Collections.singletonList("newTag"));

        verify(tagRepository).insertIgnore("newTag");
        verify(postTagRepository).save(any(PostTag.class));
        verify(tagRepository).incrementPostCountIn(List.of(20L));
    }

    @Test
    @DisplayName("assignTags inserts missing tags in stable name order")
    void assignTags_insertsMissingTagsInStableNameOrder() {
        when(postTagRepository.findByPost(post)).thenReturn(Collections.emptyList());
        Tag alphaTag = new Tag("alpha");
        Tag zuluTag = new Tag("zulu");
        ReflectionTestUtils.setField(alphaTag, "tagId", 21L);
        ReflectionTestUtils.setField(zuluTag, "tagId", 22L);
        Set<String> requestedTags = new LinkedHashSet<>(Arrays.asList("zulu", "alpha"));
        when(tagRepository.findByTagNameInForUpdate(requestedTags))
                .thenReturn(List.of(), List.of(alphaTag, zuluTag));

        tagAssignmentService.assignTags(post, Arrays.asList("zulu", "alpha"));

        InOrder inOrder = inOrder(tagRepository);
        inOrder.verify(tagRepository).insertIgnore("alpha");
        inOrder.verify(tagRepository).insertIgnore("zulu");
        verify(tagRepository).incrementPostCountIn(List.of(22L, 21L));
    }

    @Test
    @DisplayName("assignTags preserves duplicate error when inserted tag cannot be loaded")
    void assignTags_preservesDuplicateErrorWhenInsertedTagCannotBeLoaded() {
        when(postTagRepository.findByPost(post)).thenReturn(Collections.emptyList());
        when(tagRepository.findByTagNameInForUpdate(Collections.singleton("newTag")))
                .thenReturn(List.of(), List.of());

        assertThatThrownBy(() -> tagAssignmentService.assignTags(post, Collections.singletonList("newTag")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_RESOURCE);

        verify(tagRepository).insertIgnore("newTag");
        verify(postTagRepository, never()).save(any(PostTag.class));
        verify(tagRepository, never()).incrementPostCountIn(any());
    }

    @Test
    @DisplayName("assignTags removes old tag")
    void assignTags_removeOldTag() {
        when(postTagRepository.findByPost(post)).thenReturn(Collections.singletonList(existingPostTag));

        tagAssignmentService.assignTags(post, Collections.emptyList());

        verify(postTagRepository).delete(existingPostTag);
        verify(tagRepository).decrementPostCountIn(List.of(10L));
    }

    @Test
    @DisplayName("assignTags keeps unchanged tags")
    void assignTags_noChange() {
        when(postTagRepository.findByPost(post)).thenReturn(Collections.singletonList(existingPostTag));

        tagAssignmentService.assignTags(post, Collections.singletonList("existingTag"));

        verify(postTagRepository, never()).save(any());
        verify(postTagRepository, never()).delete(any());
    }

    @Test
    @DisplayName("assignTags treats null tags as empty")
    void assignTags_nullTagsTreatedAsEmpty() {
        when(postTagRepository.findByPost(post)).thenReturn(Collections.singletonList(existingPostTag));

        tagAssignmentService.assignTags(post, null);

        verify(postTagRepository).delete(existingPostTag);
        verify(tagRepository).decrementPostCountIn(List.of(10L));
        verify(postTagRepository, never()).save(any());
    }

    @Test
    @DisplayName("assignTags adds and removes tags together")
    void assignTags_addAndRemove() {
        Tag tagToRemove = new Tag("tagToRemove");
        ReflectionTestUtils.setField(tagToRemove, "tagId", 11L);
        PostTag postTagToRemove = PostTag.builder().post(post).tag(tagToRemove).build();
        when(postTagRepository.findByPost(post)).thenReturn(Arrays.asList(existingPostTag, postTagToRemove));
        Tag savedTag = new Tag("newTag");
        ReflectionTestUtils.setField(savedTag, "tagId", 12L);
        when(tagRepository.findByTagNameInForUpdate(Collections.singleton("newTag")))
                .thenReturn(List.of(), List.of(savedTag));

        tagAssignmentService.assignTags(post, Arrays.asList("existingTag", "newTag"));

        verify(tagRepository).insertIgnore("newTag");
        verify(postTagRepository).save(any(PostTag.class));
        verify(postTagRepository).delete(postTagToRemove);
        verify(tagRepository).incrementPostCountIn(List.of(12L));
        verify(tagRepository).decrementPostCountIn(List.of(11L));
    }

    @Test
    @DisplayName("assignTags normalizes whitespace and duplicates")
    void assignTags_normalizesInput() {
        Tag javaTag = new Tag("Java");
        ReflectionTestUtils.setField(javaTag, "tagId", 31L);
        when(postTagRepository.findByPost(post)).thenReturn(Collections.emptyList());
        Tag springTag = new Tag("Spring");
        ReflectionTestUtils.setField(springTag, "tagId", 22L);
        when(tagRepository.findByTagNameInForUpdate(new LinkedHashSet<>(List.of("Java", "Spring"))))
                .thenReturn(List.of(springTag, javaTag));

        tagAssignmentService.assignTags(post, List.of("  Java  ", "Spring", "Java"));

        verify(postTagRepository, times(2)).save(any(PostTag.class));
        verify(tagRepository).incrementPostCountIn(List.of(31L, 22L));
    }

    @Test
    @DisplayName("assignTags rejects blank tag names before changes")
    void assignTags_rejectsBlankTagNames() {
        assertThatThrownBy(() -> tagAssignmentService.assignTags(post, List.of("Java", "   ")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(postTagRepository, never()).findByPost(any());
        verify(postTagRepository, never()).save(any());
        verify(postTagRepository, never()).delete(any());
    }

    @Test
    @DisplayName("assignTags rejects too long tag names before changes")
    void assignTags_rejectsTooLongTagNames() {
        String tooLongTagName = "a".repeat(101);

        assertThatThrownBy(() -> tagAssignmentService.assignTags(post, List.of(tooLongTagName)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(postTagRepository, never()).findByPost(any());
        verify(postTagRepository, never()).save(any());
        verify(postTagRepository, never()).delete(any());
    }

    @Test
    @DisplayName("assignTags rejects more than ten normalized tag names before changes")
    void assignTags_rejectsTooManyTagNames() {
        List<String> tagNames = IntStream.rangeClosed(1, 11)
                .mapToObj(index -> "tag" + index)
                .toList();

        assertThatThrownBy(() -> tagAssignmentService.assignTags(post, tagNames))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(postTagRepository, never()).findByPost(any());
        verify(postTagRepository, never()).save(any());
        verify(postTagRepository, never()).delete(any());
    }

    @Test
    @DisplayName("clearTags removes every tag assignment")
    void clearTags_removesAllTags() {
        Tag secondTag = new Tag("secondTag");
        ReflectionTestUtils.setField(secondTag, "tagId", 21L);
        PostTag secondPostTag = PostTag.builder().post(post).tag(secondTag).build();
        when(postTagRepository.findByPost(post)).thenReturn(List.of(existingPostTag, secondPostTag));

        tagAssignmentService.clearTags(post);

        verify(postTagRepository).delete(existingPostTag);
        verify(postTagRepository).delete(secondPostTag);
        verify(tagRepository).decrementPostCountIn(List.of(10L, 21L));
    }

    @Test
    @DisplayName("getTagNames returns assigned tag names")
    void getTagNames_returnsAssignedTagNames() {
        Tag secondTag = new Tag("secondTag");
        PostTag secondPostTag = PostTag.builder().post(post).tag(secondTag).build();
        when(postTagRepository.findByPost(post)).thenReturn(List.of(existingPostTag, secondPostTag));

        List<String> tagNames = tagAssignmentService.getTagNames(post);

        assertThat(tagNames).containsExactly("existingTag", "secondTag");
    }
}
