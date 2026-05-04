package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostLatestReadService {

    private final PostRepository postRepository;
    private final UserBlockService userBlockService;
    private final PostSummaryAssembler postSummaryAssembler;

    public Map<Long, List<PostSummary>> getLatestPostsByBoards(
            List<Long> boardIds,
            int limit,
            Long currentUserId,
            Set<Long> secretVisibleBoardIds) {
        if (boardIds == null || boardIds.isEmpty() || limit <= 0) {
            return Collections.emptyMap();
        }

        List<Long> blockedUserIds = resolveBlockedUserIds(currentUserId);

        List<Long> latestPostIds = postRepository.findLatestPostIdsByBoardIds(
                boardIds,
                limit,
                blockedUserIds,
                secretVisibleBoardIds,
                currentUserId);
        if (latestPostIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, Post> postsById = postRepository.findByPostIdIn(latestPostIds).stream()
                .collect(Collectors.toMap(Post::getPostId, post -> post));

        List<Post> orderedPosts = latestPostIds.stream()
                .map(postsById::get)
                .filter(Objects::nonNull)
                .toList();
        List<PostSummary> summaries = postSummaryAssembler.assembleLatestPosts(orderedPosts, currentUserId);

        Map<Long, List<PostSummary>> latestPostsByBoardId = new LinkedHashMap<>();
        for (PostSummary summary : summaries) {
            latestPostsByBoardId.computeIfAbsent(summary.getBoardId(), ignored -> new ArrayList<>())
                    .add(summary);
        }
        return latestPostsByBoardId;
    }

    private List<Long> resolveBlockedUserIds(Long currentUserId) {
        if (currentUserId == null) {
            return null;
        }
        return userBlockService.getBlockedUserIdsEitherDirection(currentUserId);
    }
}
