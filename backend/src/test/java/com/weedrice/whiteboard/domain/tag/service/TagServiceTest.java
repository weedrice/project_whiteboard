package com.weedrice.whiteboard.domain.tag.service;

import com.weedrice.whiteboard.domain.tag.entity.Tag;
import com.weedrice.whiteboard.domain.tag.repository.TagRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    private TagRepository tagRepository;

    @InjectMocks
    private TagService tagService;

    @Test
    @DisplayName("getPopularTags returns repository result")
    void getPopularTags_success() {
        Tag tag1 = new Tag("tag1");
        tag1.incrementPostCount();
        tag1.incrementPostCount();
        Tag tag2 = new Tag("tag2");
        tag2.incrementPostCount();

        when(tagRepository.findTop10ByPostCountGreaterThanOrderByPostCountDesc(0))
                .thenReturn(List.of(tag1, tag2));

        List<Tag> popularTags = tagService.getPopularTags();

        assertThat(popularTags).hasSize(2);
        assertThat(popularTags.get(0).getTagName()).isEqualTo("tag1");
        verify(tagRepository).findTop10ByPostCountGreaterThanOrderByPostCountDesc(0);
    }
}
