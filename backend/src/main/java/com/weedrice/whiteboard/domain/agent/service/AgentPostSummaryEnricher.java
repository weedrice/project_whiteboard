package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.entity.Post;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
class AgentPostSummaryEnricher {

    private final CommentRepository commentRepository;

    Page<PostSummary> fromPosts(Page<Post> postPage, Long agentId) {
        List<PostSummary> content = postPage.getContent().stream()
                .map(PostSummary::from)
                .toList();
        return enrich(content, postPage.getPageable(), postPage.getTotalElements(), agentId);
    }

    Page<PostSummary> enrich(Page<PostSummary> postPage, Long agentId) {
        return enrich(postPage.getContent(), postPage.getPageable(), postPage.getTotalElements(), agentId);
    }

    private Page<PostSummary> enrich(List<PostSummary> content, org.springframework.data.domain.Pageable pageable,
            long totalElements, Long agentId) {
        if (content.isEmpty()) {
            return new PageImpl<>(content, pageable, totalElements);
        }

        Set<Long> postIdsWithMyComment = resolvePostIdsWithMyComment(content, agentId);
        List<PostSummary> enriched = content.stream()
                .peek(summary -> summary.setHasMyComment(postIdsWithMyComment.contains(summary.getPostId())))
                .toList();
        return new PageImpl<>(enriched, pageable, totalElements);
    }

    private Set<Long> resolvePostIdsWithMyComment(List<PostSummary> content, Long agentId) {
        if (agentId == null || content.isEmpty()) {
            return Collections.emptySet();
        }
        List<Long> postIds = content.stream()
                .map(PostSummary::getPostId)
                .toList();
        return new HashSet<>(commentRepository.findDistinctPostIdsByPost_PostIdInAndAgent_AgentIdAndIsDeletedFalse(
                postIds, agentId));
    }
}
