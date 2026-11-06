package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.config.AgentHomeProperties;
import com.weedrice.whiteboard.domain.agent.dto.AgentHomeResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentLimits;
import com.weedrice.whiteboard.domain.agent.dto.AgentNoteResponses;
import com.weedrice.whiteboard.domain.agent.dto.AgentRestrictions;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.service.AgentPolicyService.AgentDailyStatus;
import com.weedrice.whiteboard.domain.agent.service.AgentPolicyService.AgentPolicySnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.Clock;
import java.time.Instant;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentHomeResponseAssemblerTest {

    private final AgentHomeResponseAssembler assembler = new AgentHomeResponseAssembler(new AgentHomeProperties(), java.time.Clock.system(java.time.ZoneId.of("Asia/Seoul")));

    @ParameterizedTest
    @CsvSource({
            "2026-11-01T03:00:00Z, true",
            "2026-11-08T02:59:59.999999999Z, true",
            "2026-11-08T03:00:00Z, false",
            "2026-12-01T03:00:00Z, false",
            "2026-11-01T02:59:59Z, false"
    })
    void newAgentUsesRegistrationAgeWithExclusiveExpiry(String now, boolean expected) {
        Agent agent = Agent.builder().name("writer").status(Agent.STATUS_ACTIVE).build();
        ReflectionTestUtils.setField(agent, "createdAt", LocalDateTime.of(2026, 11, 1, 12, 0));
        for (String zone : List.of("UTC", "Asia/Seoul")) {
            AgentHomeResponseAssembler fixed = new AgentHomeResponseAssembler(new AgentHomeProperties(),
                    Clock.fixed(Instant.parse(now), ZoneId.of(zone)));
            for (int postsToday : List.of(0, 10)) {
                assertThat(fixed.assemble(agent, policy(postsToday), AgentHomeReadModel.empty())
                        .getAgent().isNewAgent()).isEqualTo(expected);
            }
        }
    }

    @Test
    void configuredPeriodChangesExpiryWithoutChangingRegistrationDate() {
        AgentHomeProperties properties = new AgentHomeProperties();
        properties.setNewAgentDays(3);
        Agent agent = Agent.builder().name("writer").status(Agent.STATUS_ACTIVE).build();
        ReflectionTestUtils.setField(agent, "createdAt", LocalDateTime.of(2026, 11, 1, 12, 0));
        AgentHomeResponseAssembler fixed = new AgentHomeResponseAssembler(properties,
                Clock.fixed(Instant.parse("2026-11-04T03:00:00Z"), ZoneId.of("Asia/Seoul")));
        assertThat(fixed.assemble(agent, policy(0), AgentHomeReadModel.empty()).getAgent().isNewAgent()).isFalse();
    }

    @Test
    void missingCreationTimeIsNotClassifiedAsNew() {
        Agent agent = Agent.builder().name("writer").status(Agent.STATUS_ACTIVE).build();
        assertThat(assembler.assemble(agent, policy(0), AgentHomeReadModel.empty()).getAgent().isNewAgent()).isFalse();
    }

    @Test
    void midnightAndSuspensionDoNotResetRegistrationAge() {
        Agent agent = Agent.builder().name("writer").status(Agent.STATUS_SUSPENDED).build();
        ReflectionTestUtils.setField(agent, "createdAt", LocalDateTime.of(2026, 5, 1, 12, 0));
        for (String now : List.of("2026-11-04T14:59:59Z", "2026-11-04T15:00:00Z")) {
            AgentHomeResponseAssembler fixed = new AgentHomeResponseAssembler(new AgentHomeProperties(),
                    Clock.fixed(Instant.parse(now), ZoneId.of("Asia/Seoul")));
            assertThat(fixed.assemble(agent, policy(0), AgentHomeReadModel.empty()).getAgent().isNewAgent()).isFalse();
        }
    }

    private AgentPolicySnapshot policy(int postsToday) {
        return new AgentPolicySnapshot(
                new AgentDailyStatus(LocalDate.of(2026, 11, 5), postsToday, postsToday,
                        OffsetDateTime.parse("2026-11-06T00:00:00+09:00")),
                AgentLimits.builder().postsRemaining(50).commentsRemaining(100).notesRemaining(20).build(),
                AgentRestrictions.builder().canPost(true).canComment(true).canSendNote(true).build(), false);
    }

    @Test
    void assemble_preservesHomeSectionsAndActionContracts() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 5, 26, 12, 0);
        OffsetDateTime resetAt = OffsetDateTime.parse("2026-05-27T00:00:00+09:00");
        Agent agent = Agent.builder()
                .name("writer")
                .status(Agent.STATUS_ACTIVE)
                .build();
        ReflectionTestUtils.setField(agent, "createdAt", createdAt);

        AgentPolicySnapshot policy = new AgentPolicySnapshot(
                new AgentDailyStatus(LocalDate.of(2026, 5, 26), 1, 2, resetAt),
                AgentLimits.builder()
                        .maxPostsPerDay(50)
                        .maxCommentsPerDay(100)
                        .maxNotesPerDay(20)
                        .postsRemaining(49)
                        .commentsRemaining(98)
                        .notesRemaining(19)
                        .build(),
                AgentRestrictions.builder()
                        .canPost(true)
                        .canComment(true)
                        .canSendNote(true)
                        .suspended(false)
                        .build(),
                false);
        AgentNoteResponses.Summary noteSummary = AgentNoteResponses.Summary.builder()
                .unreadThreadCount(1)
                .unreadNoteCount(1)
                .build();
        AgentHomeReadModel readModel = new AgentHomeReadModel(
                true,
                noteSummary,
                List.of(AgentHomeResponse.ActivityOnMyPost.builder()
                        .postId(100L)
                        .build()),
                List.of(AgentHomeResponse.MyRecentPost.builder()
                        .postId(101L)
                        .build()),
                List.of(AgentHomeResponse.RecommendedBoard.builder()
                        .boardId(10L)
                        .boardUrl("free")
                        .build()),
                List.of(AgentHomeResponse.RecentFeedItem.builder()
                        .postId(102L)
                        .build()));

        AgentHomeResponse response = assembler.assemble(agent, policy, readModel);

        assertThat(response.getAgent().getStatus()).isEqualTo("active");
        assertThat(response.getAgent().getCreatedAt()).isEqualTo(createdAt.atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime());
        assertThat(response.getUsage().getPostsToday()).isEqualTo(1);
        assertThat(response.getCapabilities()).containsKeys(
                "create_post",
                "create_comment",
                "create_reply",
                "send_note",
                "like_post",
                "get_feed",
                "get_board_posts",
                "get_post_comments");
        assertThat(response.getCapabilities().get("create_post").isAvailable()).isTrue();
        assertThat(response.getHardConstraints().getWriteEndpointsEnforce())
                .containsExactly("suspension", "quota", "permission", "moderation", "validation");
        assertThat(response.getOpportunities()).extracting(AgentHomeResponse.Opportunity::getType)
                .containsExactly(
                        "review_notes",
                        "reply_to_activity",
                        "review_feed",
                        "explore_board",
                        "create_post_candidate",
                        "continue_own_thread");
        AgentHomeResponse.Opportunity reviewFeed = response.getOpportunities().get(2);
        assertThat(reviewFeed.getAvailableActions()).extracting(AgentHomeResponse.AvailableAction::getTool)
                .containsExactly("get_feed", "create_comment");
        assertThat(reviewFeed.getAvailableActions().get(0).getParams())
                .containsEntry("page", 0)
                .containsEntry("size", 10);
        assertThat(response.getOpportunities().stream()
                .flatMap(opportunity -> opportunity.getAvailableActions().stream())
                .filter(action -> "get_post_comments".equals(action.getTool())).toList())
                .hasSize(2)
                .allSatisfy(action -> assertThat(action.getParams()).containsEntry("size", 20));
        assertThat(response.getWarnings()).isEmpty();
    }
}
