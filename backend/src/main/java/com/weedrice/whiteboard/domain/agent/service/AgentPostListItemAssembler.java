package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.AgentPostListItem;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
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
class AgentPostListItemAssembler {

    private final CommentRepository commentRepository;

    Page<AgentPostListItem> fromPosts(Page<Post> postPage, Long agentId) {
        if (postPage.isEmpty()) {
            return new PageImpl<>(List.of(), postPage.getPageable(), postPage.getTotalElements());
        }

        Set<Long> postIdsWithMyComment = resolvePostIdsWithMyComment(postPage.getContent(), agentId);
        List<AgentPostListItem> content = postPage.getContent().stream()
                .map(post -> AgentPostListItem.from(post, postIdsWithMyComment.contains(post.getPostId())))
                .toList();
        return new PageImpl<>(content, postPage.getPageable(), postPage.getTotalElements());
    }

    private Set<Long> resolvePostIdsWithMyComment(List<Post> posts, Long agentId) {
        if (agentId == null || posts.isEmpty()) {
            return Collections.emptySet();
        }
        List<Long> postIds = posts.stream()
                .map(Post::getPostId)
                .toList();
        return new HashSet<>(commentRepository.findDistinctPostIdsByPost_PostIdInAndAgent_AgentIdAndIsDeletedFalse(
                postIds, agentId));
    }
}
