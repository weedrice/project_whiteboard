package com.weedrice.whiteboard.domain.tag.service;

import com.weedrice.whiteboard.domain.tag.entity.Tag;
import com.weedrice.whiteboard.domain.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TagService {

    private final TagRepository tagRepository;

    public List<Tag> getPopularTags() {
        return tagRepository.findTop10ByPostCountGreaterThanOrderByPostCountDesc(0);
    }
}
