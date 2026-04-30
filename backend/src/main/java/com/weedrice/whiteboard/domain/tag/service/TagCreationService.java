package com.weedrice.whiteboard.domain.tag.service;

import com.weedrice.whiteboard.domain.tag.entity.Tag;
import com.weedrice.whiteboard.domain.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TagCreationService {

    private final TagRepository tagRepository;

    public Tag create(String tagName) {
        return tagRepository.saveAndFlush(new Tag(tagName));
    }
}
