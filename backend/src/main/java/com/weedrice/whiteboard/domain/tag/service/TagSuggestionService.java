package com.weedrice.whiteboard.domain.tag.service;

import com.weedrice.whiteboard.domain.search.semantic.SemanticSearchResultResponse;
import com.weedrice.whiteboard.domain.search.semantic.SemanticSearchService;
import com.weedrice.whiteboard.domain.tag.dto.TagSuggestionRequest;
import com.weedrice.whiteboard.domain.tag.dto.TagSuggestionResponse;
import com.weedrice.whiteboard.domain.tag.entity.PostTag;
import com.weedrice.whiteboard.domain.tag.entity.Tag;
import com.weedrice.whiteboard.domain.tag.model.PublicTagCount;
import com.weedrice.whiteboard.domain.tag.repository.PostTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TagSuggestionService {

    private static final int MAX_SUGGESTIONS = 5;
    private static final int SIMILAR_POST_SEARCH_SIZE = 20;

    private final SemanticSearchService semanticSearchService;
    private final PostTagRepository postTagRepository;
    private final TagService tagService;

    public TagSuggestionResponse suggest(TagSuggestionRequest request, Long currentUserId) {
        String query = buildQuery(request);
        Set<String> excluded = normalizeExistingTags(request != null ? request.getExistingTags() : null);
        LinkedHashSet<String> suggestions = new LinkedHashSet<>();

        if (StringUtils.hasText(query)) {
            Page<SemanticSearchResultResponse> similarPosts = semanticSearchService.search(
                    query,
                    "POST",
                    request != null ? request.getBoardUrl() : null,
                    0,
                    SIMILAR_POST_SEARCH_SIZE,
                    currentUserId);
            addSimilarPostTags(suggestions, similarPosts.getContent(), excluded);
        }

        if (suggestions.size() < MAX_SUGGESTIONS) {
            addPopularTags(suggestions, excluded);
        }

        return TagSuggestionResponse.builder()
                .suggestions(suggestions.stream().limit(MAX_SUGGESTIONS).toList())
                .build();
    }

    private void addSimilarPostTags(LinkedHashSet<String> suggestions, List<SemanticSearchResultResponse> similarPosts,
            Set<String> excluded) {
        List<Long> postIds = similarPosts.stream()
                .map(SemanticSearchResultResponse::getPostId)
                .filter(postId -> postId != null)
                .distinct()
                .toList();
        if (postIds.isEmpty()) {
            return;
        }

        Map<String, Long> countsByTag = postTagRepository.findByPost_PostIdIn(postIds).stream()
                .map(PostTag::getTag)
                .map(Tag::getTagName)
                .filter(StringUtils::hasText)
                .filter(tagName -> !excluded.contains(normalizeKey(tagName)))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        countsByTag.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue).reversed()
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .forEach(suggestions::add);
    }

    private void addPopularTags(LinkedHashSet<String> suggestions, Set<String> excluded) {
        tagService.getPopularTags().stream()
                .map(PublicTagCount::tagName)
                .filter(StringUtils::hasText)
                .filter(tagName -> !excluded.contains(normalizeKey(tagName)))
                .forEach(suggestions::add);
    }

    private String buildQuery(TagSuggestionRequest request) {
        if (request == null) {
            return "";
        }
        String title = request.getTitle() != null ? request.getTitle() : "";
        String contents = request.getContents() != null ? request.getContents() : "";
        return (title + "\n" + contents)
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private Set<String> normalizeExistingTags(List<String> existingTags) {
        if (existingTags == null || existingTags.isEmpty()) {
            return Set.of();
        }
        return existingTags.stream()
                .filter(StringUtils::hasText)
                .map(this::normalizeKey)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private String normalizeKey(String tagName) {
        return tagName.trim().toLowerCase(Locale.ROOT);
    }
}
