package com.weedrice.whiteboard.domain.tag.service;

import com.weedrice.whiteboard.domain.tag.constant.TagConstraints;
import com.weedrice.whiteboard.domain.tag.entity.Tag;
import com.weedrice.whiteboard.domain.tag.model.PublicTagCount;
import com.weedrice.whiteboard.domain.tag.repository.TagRepository;
import com.weedrice.whiteboard.global.common.util.TextInputNormalizer;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TagService {

    private static final int POPULAR_TAG_LIMIT = 10;

    private final TagRepository tagRepository;

    public List<PublicTagCount> getPopularTags() {
        return tagRepository.findPopularPublicTagCounts(PageRequest.of(0, POPULAR_TAG_LIMIT)).stream()
                .map(tag -> new PublicTagCount(tag.getTagId(), tag.getTagName(), tag.getPostCount()))
                .toList();
    }

    public Tag getByName(String tagName) {
        return findByName(tagName)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    public Optional<Tag> findByName(String tagName) {
        String normalizedTagName = TextInputNormalizer.normalizeRequired(tagName, TagConstraints.MAX_TAG_NAME_LENGTH);
        return tagRepository.findByTagName(normalizedTagName);
    }
}
