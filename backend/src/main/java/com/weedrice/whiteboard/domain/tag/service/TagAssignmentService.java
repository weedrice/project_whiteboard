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

        List<PostTag> postTagsToRemove = existingPostTags.stream()
                .filter(postTag -> !normalizedTagNames.contains(postTag.getTag().getTagName()))
                .toList();
        Set<String> tagNamesToAdd = normalizedTagNames.stream()
                .filter(tagName -> !existingPostTagsByName.containsKey(tagName))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<Long> removedTagIds = removePostTags(postTagsToRemove);
        List<Long> addedTagIds = addPostTags(post, tagNamesToAdd);
        decrementPostCounts(removedTagIds);
        incrementPostCounts(addedTagIds);
    }

    @Transactional
    public void clearTags(Post post) {
        List<Long> removedTagIds = removePostTags(postTagRepository.findByPost(post));
        decrementPostCounts(removedTagIds);
    }

    public List<String> getTagNames(Post post) {
        return postTagRepository.findByPost(post).stream()
                .map(postTag -> postTag.getTag().getTagName())
                .toList();
    }

    public void validateTags(List<String> requestedTagNames) {
        normalizeTagNames(requestedTagNames);
    }

    private List<Long> addPostTags(Post post, Set<String> tagNamesToAdd) {
        if (tagNamesToAdd.isEmpty()) {
            return List.of();
        }

        Map<String, Tag> tagsByName = tagRepository.findByTagNameInForUpdate(tagNamesToAdd).stream()
                .collect(Collectors.toMap(Tag::getTagName, Function.identity()));
        return tagNamesToAdd.stream()
                .map(tagName -> addPostTag(post, tagName, tagsByName))
                .toList();
    }

    private Long addPostTag(Post post, String tagName, Map<String, Tag> tagsByName) {
        Tag tag = tagsByName.computeIfAbsent(tagName, this::createTag);
        postTagRepository.save(PostTag.builder()
                .post(post)
                .tag(tag)
                .build());
        return tag.getTagId();
    }

    private List<Long> removePostTags(List<PostTag> postTags) {
        return postTags.stream()
                .map(this::removePostTag)
                .toList();
    }

    private Long removePostTag(PostTag postTag) {
        postTagRepository.delete(postTag);
        return postTag.getTag().getTagId();
    }

    private void incrementPostCounts(List<Long> tagIds) {
        if (!tagIds.isEmpty()) {
            tagRepository.incrementPostCountIn(tagIds);
        }
    }

    private void decrementPostCounts(List<Long> tagIds) {
        if (!tagIds.isEmpty()) {
            tagRepository.decrementPostCountIn(tagIds);
        }
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

    private Tag createTag(String tagName) {
        try {
            return tagCreationService.create(tagName);
        } catch (DataIntegrityViolationException ex) {
            return tagRepository.findByTagName(tagName)
                    .orElseThrow(() -> new BusinessException(ErrorCode.DUPLICATE_RESOURCE));
        }
    }
}
