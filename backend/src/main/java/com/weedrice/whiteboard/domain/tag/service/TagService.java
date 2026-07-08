package com.weedrice.whiteboard.domain.tag.service;

import com.weedrice.whiteboard.domain.tag.entity.Tag;
import com.weedrice.whiteboard.domain.tag.constant.TagConstraints;
import com.weedrice.whiteboard.domain.tag.repository.TagRepository;
import com.weedrice.whiteboard.global.common.util.TextInputNormalizer;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
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

    public Tag getByName(String tagName) {
        String normalizedTagName = TextInputNormalizer.normalizeRequired(tagName, TagConstraints.MAX_TAG_NAME_LENGTH);
        return tagRepository.findByTagName(normalizedTagName)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
}
