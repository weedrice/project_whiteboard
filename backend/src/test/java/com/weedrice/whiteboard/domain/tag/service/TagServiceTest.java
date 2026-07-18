package com.weedrice.whiteboard.domain.tag.service;

import com.weedrice.whiteboard.domain.tag.model.PublicTagCount;
import com.weedrice.whiteboard.domain.tag.repository.TagRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
        TagRepository.PublicTagCountProjection tag1 = publicTag(1L, "tag1", 2L);
        TagRepository.PublicTagCountProjection tag2 = publicTag(2L, "tag2", 1L);

        when(tagRepository.findPopularPublicTagCounts(any(Pageable.class)))
                .thenReturn(List.of(tag1, tag2));

        List<PublicTagCount> popularTags = tagService.getPopularTags();

        assertThat(popularTags).hasSize(2);
        assertThat(popularTags.get(0).tagName()).isEqualTo("tag1");
        assertThat(popularTags.get(0).postCount()).isEqualTo(2L);
        verify(tagRepository).findPopularPublicTagCounts(any(Pageable.class));
    }

    private TagRepository.PublicTagCountProjection publicTag(Long id, String name, long count) {
        TagRepository.PublicTagCountProjection projection = mock(TagRepository.PublicTagCountProjection.class);
        when(projection.getTagId()).thenReturn(id);
        when(projection.getTagName()).thenReturn(name);
        when(projection.getPostCount()).thenReturn(count);
        return projection;
    }
}
