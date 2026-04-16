package com.weedrice.whiteboard.domain.tag.service;

import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.tag.entity.PostTag;
import com.weedrice.whiteboard.domain.tag.entity.Tag;
import com.weedrice.whiteboard.domain.tag.repository.PostTagRepository;
import com.weedrice.whiteboard.domain.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TagService {

    private final TagRepository tagRepository;
    private final PostTagRepository postTagRepository;

    @Transactional
    public void processTagsForPost(Post post, List<String> newTagNames) {
        Set<String> requestedTagNames = normalizeTagNames(newTagNames);

        List<PostTag> existingPostTags = postTagRepository.findByPost(post);
        Set<String> existingTagNames = existingPostTags.stream()
                .map(postTag -> postTag.getTag().getTagName())
                .collect(Collectors.toSet());

        for (PostTag postTag : existingPostTags) {
            if (!requestedTagNames.contains(postTag.getTag().getTagName())) {
                postTagRepository.delete(postTag);
                tagRepository.decrementPostCount(postTag.getTag().getTagId());
            }
        }

        for (String newTagName : requestedTagNames) {
            if (!existingTagNames.contains(newTagName)) {
                Tag tag = findOrCreateTag(newTagName);

                PostTag postTag = PostTag.builder()
                        .post(post)
                        .tag(tag)
                        .build();
                postTagRepository.save(postTag);
                tagRepository.incrementPostCount(tag.getTagId());
            }
        }
    }

    public List<Tag> getPopularTags() {
        return tagRepository.findTop10ByPostCountGreaterThanOrderByPostCountDesc(0);
    }

    private Set<String> normalizeTagNames(List<String> newTagNames) {
        if (newTagNames == null || newTagNames.isEmpty()) {
            return Collections.emptySet();
        }

        return newTagNames.stream()
                .filter(tagName -> tagName != null && !tagName.isBlank())
                .map(String::trim)
                .filter(tagName -> !tagName.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Tag findOrCreateTag(String tagName) {
        return tagRepository.findByTagName(tagName)
                .orElseGet(() -> saveHandlingDuplicate(tagName));
    }

    private Tag saveHandlingDuplicate(String tagName) {
        try {
            return tagRepository.save(new Tag(tagName));
        } catch (DataIntegrityViolationException ex) {
            return tagRepository.findByTagName(tagName)
                    .orElseThrow(() -> ex);
        }
    }
}
