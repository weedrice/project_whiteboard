package com.weedrice.whiteboard.domain.tag.service;

import com.weedrice.whiteboard.domain.search.semantic.SemanticSearchService;
import com.weedrice.whiteboard.domain.tag.dto.TagSuggestionResponse;
import com.weedrice.whiteboard.domain.tag.model.PublicTagCount;
import com.weedrice.whiteboard.domain.tag.repository.PostTagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagSuggestionServiceTest {

    @Mock
    private SemanticSearchService semanticSearchService;

    @Mock
    private PostTagRepository postTagRepository;

    @Mock
    private TagService tagService;

    @Test
    void blankInputUsesPublicVisiblePopularTagFallback() {
        when(tagService.getPopularTags()).thenReturn(List.of(
                new PublicTagCount(1L, "public-one", 2L),
                new PublicTagCount(2L, "public-two", 1L)));
        TagSuggestionService service = new TagSuggestionService(
                semanticSearchService,
                postTagRepository,
                tagService);

        TagSuggestionResponse response = service.suggest(null, null);

        assertThat(response.getSuggestions()).containsExactly("public-one", "public-two");
        verify(tagService).getPopularTags();
    }
}
