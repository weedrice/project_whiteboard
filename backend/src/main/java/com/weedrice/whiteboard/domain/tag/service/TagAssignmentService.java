package com.weedrice.whiteboard.domain.tag.service;

import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.tag.constant.TagConstraints;
import com.weedrice.whiteboard.domain.tag.entity.PostTag;
import com.weedrice.whiteboard.domain.tag.entity.Tag;
import com.weedrice.whiteboard.domain.tag.repository.PostTagRepository;
import com.weedrice.whiteboard.domain.tag.repository.TagRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TagAssignmentService {

    private final TagRepository tagRepository;
    private final PostTagRepository postTagRepository;
    private final TagCreationService tagCreationService;

    @Transactional
    public void assignTags(Post post, List<String> requestedTagNames) {
        Set<String> normalizedTagNames = normalizeTagNames(requestedTagNames);
        List<PostTag> existingPostTags = postTagRepository.findByPost(post);
        Map<String, PostTag> existingPostTagsByName = existingPostTags.stream()
                .collect(Collectors.toMap(postTag -> postTag.getTag().getTagName(), Function.identity()));

        for (PostTag postTag : existingPostTags) {
            if (!normalizedTagNames.contains(postTag.getTag().getTagName())) {
                removePostTag(postTag);
            }
        }

        for (String tagName : normalizedTagNames) {
            if (!existingPostTagsByName.containsKey(tagName)) {
                addPostTag(post, tagName);
            }
        }
    }

    @Transactional
    public void clearTags(Post post) {
        postTagRepository.findByPost(post).forEach(this::removePostTag);
    }

    public List<String> getTagNames(Post post) {
        return postTagRepository.findByPost(post).stream()
                .map(postTag -> postTag.getTag().getTagName())
                .toList();
    }

    public void validateTags(List<String> requestedTagNames) {
        normalizeTagNames(requestedTagNames);
    }

    private void addPostTag(Post post, String tagName) {
        Tag tag = findOrCreateTag(tagName);
        postTagRepository.save(PostTag.builder()
                .post(post)
                .tag(tag)
                .build());
        tagRepository.incrementPostCount(tag.getTagId());
    }

    private void removePostTag(PostTag postTag) {
        postTagRepository.delete(postTag);
        tagRepository.decrementPostCount(postTag.getTag().getTagId());
    }

    private Set<String> normalizeTagNames(List<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> normalizedTagNames = new LinkedHashSet<>();
        for (String tagName : tagNames) {
            if (tagName == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
            String normalizedTagName = tagName.trim();
            if (normalizedTagName.isBlank()
                    || normalizedTagName.length() > TagConstraints.MAX_TAG_NAME_LENGTH) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
            normalizedTagNames.add(normalizedTagName);
        }

        if (normalizedTagNames.size() > TagConstraints.MAX_POST_TAG_COUNT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalizedTagNames;
    }

    private Tag findOrCreateTag(String tagName) {
        return tagRepository.findByTagName(tagName)
                .orElseGet(() -> createTag(tagName));
    }

    private Tag createTag(String tagName) {
        try {
            return tagCreationService.create(tagName);
        } catch (DataIntegrityViolationException ex) {
            return tagRepository.findByTagName(tagName)
                    .orElseThrow(() -> new BusinessException(ErrorCode.DUPLICATE_RESOURCE));
        }
    }
}
