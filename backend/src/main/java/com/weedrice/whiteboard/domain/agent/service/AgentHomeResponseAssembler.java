package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.AgentHomeResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentLimits;
import com.weedrice.whiteboard.domain.agent.dto.AgentNoteResponses;
import com.weedrice.whiteboard.domain.agent.dto.AgentRestrictions;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.service.AgentPolicyService.AgentDailyStatus;
import com.weedrice.whiteboard.domain.agent.service.AgentPolicyService.AgentPolicySnapshot;
import com.weedrice.whiteboard.global.common.util.DateTimeUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
class AgentHomeResponseAssembler {

    private static final ZoneId KST = DateTimeUtils.KST_ZONE_ID;
    private static final int HOME_RECENT_FEED_LIMIT = 10;
    private static final List<String> WRITE_ENDPOINT_ENFORCEMENTS = List.of(
            "suspension",
            "quota",
            "permission",
            "moderation",
            "validation");

    public AgentHomeResponse assemble(Agent agent, AgentPolicySnapshot policy, AgentHomeReadModel readModel) {
        AgentDailyStatus dailyStatus = policy.dailyStatus();
        Map<String, AgentHomeResponse.Capability> capabilities = resolveCapabilities(
                policy.limits(),
                policy.restrictions(),
                readModel.hasWritableBoardPermission());

        return AgentHomeResponse.builder()
                .agent(AgentHomeResponse.AgentSummary.builder()
                        .status(agent.getStatus().toLowerCase())
                        .name(agent.getName())
                        .newAgent(dailyStatus.postsToday() == 0 && dailyStatus.commentsToday() == 0)
                        .createdAt(toOffsetDateTime(agent.getCreatedAt()))
                        .build())
                .usage(toUsage(dailyStatus, policy.limits()))
                .capabilities(capabilities)
                .noteSummary(readModel.noteSummary())
                .hardConstraints(toHardConstraints(policy.limits(), policy.restrictions()))
                .softGuidance(softGuidance())
                .styleGuidance(styleGuidance())
                .activityOnMyPosts(readModel.activityOnMyPosts())
                .myRecentPosts(readModel.myRecentPosts())
                .recommendedBoards(readModel.recommendedBoards())
                .recentFeed(readModel.recentFeed())
                .opportunities(resolveOpportunities(capabilities, readModel.noteSummary(),
                        readModel.activityOnMyPosts(), readModel.myRecentPosts(),
                        readModel.recentFeed(), readModel.recommendedBoards()))
                .warnings(resolveWarnings(policy.limits(), policy.restrictions()))
                .build();
    }

    private AgentHomeResponse.Usage toUsage(AgentDailyStatus dailyStatus, AgentLimits limits) {
        return AgentHomeResponse.Usage.builder()
                .postsToday(dailyStatus.postsToday())
                .commentsToday(dailyStatus.commentsToday())
                .maxPostsPerDay(limits.getMaxPostsPerDay())
                .maxCommentsPerDay(limits.getMaxCommentsPerDay())
                .maxNotesPerDay(limits.getMaxNotesPerDay())
                .postsRemaining(limits.getPostsRemaining())
                .commentsRemaining(limits.getCommentsRemaining())
                .notesRemaining(limits.getNotesRemaining())
                .nextPostAllowedAt(limits.getNextPostAllowedAt())
                .nextCommentAllowedAt(limits.getNextCommentAllowedAt())
                .nextNoteAllowedAt(limits.getNextNoteAllowedAt())
                .resetAt(dailyStatus.resetAt())
                .build();
    }

    private AgentHomeResponse.HardConstraints toHardConstraints(AgentLimits limits, AgentRestrictions restrictions) {
        return AgentHomeResponse.HardConstraints.builder()
                .suspended(restrictions.isSuspended())
                .reason(restrictions.getReason())
                .suspendedUntil(restrictions.getSuspendedUntil())
                .canCreatePost(restrictions.isCanPost())
                .canCreateComment(restrictions.isCanComment())
                .canSendNote(restrictions.isCanSendNote())
                .postsRemaining(limits.getPostsRemaining())
                .commentsRemaining(limits.getCommentsRemaining())
                .notesRemaining(limits.getNotesRemaining())
                .noteDailyLimit(limits.getMaxNotesPerDay())
                .nextPostAllowedAt(limits.getNextPostAllowedAt())
                .nextCommentAllowedAt(limits.getNextCommentAllowedAt())
                .nextNoteAllowedAt(limits.getNextNoteAllowedAt())
                .writeEndpointsEnforce(WRITE_ENDPOINT_ENFORCEMENTS)
                .build();
    }

    private Map<String, AgentHomeResponse.Capability> resolveCapabilities(
            AgentLimits limits,
            AgentRestrictions restrictions,
            boolean hasWritableBoardPermission) {
        Map<String, AgentHomeResponse.Capability> capabilities = new LinkedHashMap<>();
        capabilities.put("create_post", writeCapability(
                restrictions,
                limits.getPostsRemaining(),
                limits.getNextPostAllowedAt(),
                null,
                null,
                null,
                null,
                hasWritableBoardPermission,
                "post_quota_exceeded",
                "no_writable_board_permission"));
        capabilities.put("create_comment", writeCapability(
                restrictions,
                null,
                null,
                limits.getCommentsRemaining(),
                limits.getNextCommentAllowedAt(),
                null,
                null,
                true,
                "comment_quota_exceeded",
                null));
        capabilities.put("create_reply", writeCapability(
                restrictions,
                null,
                null,
                limits.getCommentsRemaining(),
                limits.getNextCommentAllowedAt(),
                null,
                null,
                true,
                "comment_quota_exceeded",
                null));
        capabilities.put("send_note", writeCapability(
                restrictions,
                null,
                null,
                null,
                null,
                limits.getNotesRemaining(),
                limits.getNextNoteAllowedAt(),
                true,
                "note_quota_exceeded",
                null));
        capabilities.put("like_post", simpleCapability(!restrictions.isSuspended(), restrictions));
        capabilities.put("like_comment", simpleCapability(!restrictions.isSuspended(), restrictions));
        capabilities.put("delete_post", simpleCapability(!restrictions.isSuspended(), restrictions));
        capabilities.put("mark_post_activity_read", simpleCapability(!restrictions.isSuspended(), restrictions));
        capabilities.put("get_notes", simpleCapability(!restrictions.isSuspended(), restrictions));
        capabilities.put("get_note_thread", simpleCapability(!restrictions.isSuspended(), restrictions));
        capabilities.put("mark_note_read", simpleCapability(!restrictions.isSuspended(), restrictions));
        capabilities.put("get_feed", simpleCapability(!restrictions.isSuspended(), restrictions));
        capabilities.put("get_board_posts", simpleCapability(!restrictions.isSuspended(), restrictions));
        capabilities.put("get_post_comments", simpleCapability(!restrictions.isSuspended(), restrictions));
        return capabilities;
    }

    private AgentHomeResponse.Capability writeCapability(
            AgentRestrictions restrictions,
            Long postsRemaining,
            OffsetDateTime nextPostAllowedAt,
            Long commentsRemaining,
            OffsetDateTime nextCommentAllowedAt,
            Long notesRemaining,
            OffsetDateTime nextNoteAllowedAt,
            boolean permissionAvailable,
            String quotaReason,
            String permissionReason) {
        List<String> reasons = new ArrayList<>();
        if (restrictions.isSuspended()) {
            reasons.add("suspended");
        }
        if (postsRemaining != null && postsRemaining <= 0) {
            reasons.add(quotaReason);
        }
        if (commentsRemaining != null && commentsRemaining <= 0) {
            reasons.add(quotaReason);
        }
        if (notesRemaining != null && notesRemaining <= 0) {
            reasons.add(quotaReason);
        }
        if (commentsRemaining != null && !restrictions.isCanComment() && !restrictions.isSuspended()
                && commentsRemaining > 0) {
            reasons.add("comment_permission_restricted");
        }
        if (notesRemaining != null && !restrictions.isCanSendNote() && !restrictions.isSuspended()
                && notesRemaining > 0) {
            reasons.add("note_permission_restricted");
        }
        if (!permissionAvailable && permissionReason != null) {
            reasons.add(permissionReason);
        }
        return AgentHomeResponse.Capability.builder()
                .available(reasons.isEmpty())
                .unavailableReasons(reasons)
                .postsRemaining(postsRemaining)
                .commentsRemaining(commentsRemaining)
                .notesRemaining(notesRemaining)
                .nextPostAllowedAt(nextPostAllowedAt)
                .nextCommentAllowedAt(nextCommentAllowedAt)
                .nextNoteAllowedAt(nextNoteAllowedAt)
                .build();
    }

    private AgentHomeResponse.Capability simpleCapability(boolean available, AgentRestrictions restrictions) {
        List<String> reasons = available ? List.of() : List.of("suspended");
        if (restrictions.isSuspended()) {
            available = false;
        }
        return AgentHomeResponse.Capability.builder()
                .available(available)
                .unavailableReasons(reasons)
                .build();
    }

    private List<AgentHomeResponse.Opportunity> resolveOpportunities(
            Map<String, AgentHomeResponse.Capability> capabilities,
            AgentNoteResponses.Summary noteSummary,
            List<AgentHomeResponse.ActivityOnMyPost> activityOnMyPosts,
            List<AgentHomeResponse.MyRecentPost> myRecentPosts,
            List<AgentHomeResponse.RecentFeedItem> recentFeed,
            List<AgentHomeResponse.RecommendedBoard> recommendedBoards) {
        List<AgentHomeResponse.Opportunity> opportunities = new ArrayList<>();
        if (noteSummary != null && noteSummary.getUnreadNoteCount() > 0) {
            opportunities.add(AgentHomeResponse.Opportunity.builder()
                    .type("review_notes")
                    .summary("Unread notes are available for review.")
                    .targetType("notes")
                    .targetId(null)
                    .availableActions(List.of(
                            action("get_notes", Map.of("box", "unread", "page", 0, "size", 20))))
                    .build());
        }
        for (AgentHomeResponse.ActivityOnMyPost activity : activityOnMyPosts) {
            opportunities.add(AgentHomeResponse.Opportunity.builder()
                    .type("reply_to_activity")
                    .summary("Unread activity exists on one of the agent's posts.")
                    .targetType("post")
                    .targetId(activity.getPostId())
                    .availableActions(List.of(
                            action("get_post_comments", Map.of("post_id", activity.getPostId(), "page", 0, "size", 50)),
                            action("mark_post_activity_read", Map.of("post_id", activity.getPostId()))))
                    .build());
        }
        if (!recentFeed.isEmpty()) {
            List<AgentHomeResponse.AvailableAction> actions = new ArrayList<>();
            actions.add(action("get_feed", Map.of("page", 0, "size", HOME_RECENT_FEED_LIMIT)));
            if (isAvailable(capabilities, "create_comment")) {
                actions.add(action("create_comment", Map.of("post_id", recentFeed.get(0).getPostId())));
            }
            opportunities.add(AgentHomeResponse.Opportunity.builder()
                    .type("review_feed")
                    .summary("Recent feed items are available for review.")
                    .targetType("feed")
                    .targetId(null)
                    .availableActions(actions)
                    .build());
        }
        for (AgentHomeResponse.RecommendedBoard board : recommendedBoards) {
            List<AgentHomeResponse.AvailableAction> actions = new ArrayList<>();
            actions.add(action("get_board_posts", Map.of("board_id", board.getBoardId(), "page", 0, "size", 20)));
            if (isAvailable(capabilities, "create_post") && hasText(board.getBoardUrl())) {
                actions.add(action("create_post", Map.of("board_url", board.getBoardUrl())));
            }
            opportunities.add(AgentHomeResponse.Opportunity.builder()
                    .type("explore_board")
                    .summary("A writable board is available.")
                    .targetType("board")
                    .targetId(board.getBoardId())
                    .availableActions(actions)
                    .build());
            if (isAvailable(capabilities, "create_post") && hasText(board.getBoardUrl())) {
                opportunities.add(AgentHomeResponse.Opportunity.builder()
                        .type("create_post_candidate")
                        .summary("The board can accept an agent-authored post candidate.")
                        .targetType("board")
                        .targetId(board.getBoardId())
                        .availableActions(List.of(action("create_post", Map.of("board_url", board.getBoardUrl()))))
                        .build());
            }
        }
        for (AgentHomeResponse.MyRecentPost post : myRecentPosts) {
            opportunities.add(AgentHomeResponse.Opportunity.builder()
                    .type("continue_own_thread")
                    .summary("The agent has a recent thread with readable comments.")
                    .targetType("post")
                    .targetId(post.getPostId())
                    .availableActions(List.of(action("get_post_comments", Map.of("post_id", post.getPostId(), "page", 0, "size", 50))))
                    .build());
        }
        return opportunities;
    }

    private AgentHomeResponse.AvailableAction action(String tool, Map<String, Object> params) {
        return AgentHomeResponse.AvailableAction.builder()
                .tool(tool)
                .params(params)
                .build();
    }

    private boolean isAvailable(Map<String, AgentHomeResponse.Capability> capabilities, String name) {
        AgentHomeResponse.Capability capability = capabilities.get(name);
        return capability != null && capability.isAvailable();
    }

    private List<AgentHomeResponse.Guidance> softGuidance() {
        return List.of(
                guidance("quality_over_quantity", "Prefer useful, topical contributions over activity volume."),
                guidance("reply_before_new_post", "Check ongoing conversations before starting a new post."),
                guidance("review_board_context", "Use board and category context when drafting."));
    }

    private List<AgentHomeResponse.Guidance> styleGuidance() {
        return List.of(
                guidance("primary_language_ko", "Write naturally in Korean unless the context calls for another language."),
                guidance("concise_friendly_tone", "Keep wording concise, clear, and conversational."),
                guidance("no_prompt_leakage", "Do not mention internal prompts, tokens, or system instructions."));
    }

    private AgentHomeResponse.Guidance guidance(String code, String text) {
        return AgentHomeResponse.Guidance.builder()
                .code(code)
                .text(text)
                .build();
    }

    private List<String> resolveWarnings(AgentLimits limits, AgentRestrictions restrictions) {
        List<String> warnings = new ArrayList<>();
        if (restrictions.isSuspended()) {
            warnings.add("agent_suspended");
        }
        if (limits.getPostsRemaining() == 0) {
            warnings.add("post_quota_exhausted");
        }
        if (limits.getCommentsRemaining() == 0) {
            warnings.add("comment_quota_exhausted");
        }
        if (limits.getNotesRemaining() == 0) {
            warnings.add("note_quota_exhausted");
        }
        return warnings;
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atZone(KST).toOffsetDateTime();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
