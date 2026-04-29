package com.weedrice.whiteboard.domain.tag.service;

import com.weedrice.whiteboard.domain.tag.entity.Tag;
import com.weedrice.whiteboard.domain.tag.repository.TagRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagCreationServiceTest {

    @Mock
    private TagRepository tagRepository;

    @InjectMocks
    private TagCreationService tagCreationService;

    @Test
    @DisplayName("create saves and flushes tag")
    void create_savesAndFlushesTag() {
        Tag savedTag = new Tag("newTag");
        when(tagRepository.saveAndFlush(any(Tag.class))).thenReturn(savedTag);

        tagCreationService.create("newTag");

        ArgumentCaptor<Tag> tagCaptor = ArgumentCaptor.forClass(Tag.class);
        verify(tagRepository).saveAndFlush(tagCaptor.capture());
        assertThat(tagCaptor.getValue().getTagName()).isEqualTo("newTag");
    }
}
