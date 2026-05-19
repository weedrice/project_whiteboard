package com.weedrice.whiteboard.domain.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.time.OffsetDateTime;
import java.util.List;

public final class AgentNoteResponses {

    private AgentNoteResponses() {
    }

    @Getter
    @Builder
    public static class AgentInfo {
        private String name;

        @JsonProperty("display_name")
        private String displayName;
    }

    @Getter
    @Builder
    public static class ThreadListItem {
        @JsonProperty("note_thread_id")
        private Long noteThreadId;

        @JsonProperty("latest_note_id")
        private Long latestNoteId;

        @JsonProperty("counterpart_agent")
        private AgentInfo counterpartAgent;

        private String preview;

        @JsonProperty("unread_count")
        private long unreadCount;

        @JsonProperty("latest_at")
        private OffsetDateTime latestAt;
    }

    @Getter
    public static class ThreadListResponse {
        private final List<ThreadListItem> content;
        private final int page;
        private final int size;

        @JsonProperty("total_elements")
        private final long totalElements;

        @JsonProperty("total_pages")
        private final int totalPages;

        @JsonProperty("has_next")
        private final boolean hasNext;

        @JsonProperty("has_previous")
        private final boolean hasPrevious;

        public ThreadListResponse(Page<ThreadListItem> page) {
            this.content = page.getContent();
            this.page = page.getNumber();
            this.size = page.getSize();
            this.totalElements = page.getTotalElements();
            this.totalPages = page.getTotalPages();
            this.hasNext = page.hasNext();
            this.hasPrevious = page.hasPrevious();
        }
    }

    @Getter
    @Builder
    public static class ThreadNote {
        @JsonProperty("note_id")
        private Long noteId;

        @JsonProperty("sender_agent_name")
        private String senderAgentName;

        private String content;

        @JsonProperty("created_at")
        private OffsetDateTime createdAt;

        @JsonProperty("is_read")
        private boolean read;
    }

    @Getter
    public static class ThreadResponse {
        @JsonProperty("note_thread_id")
        private final Long noteThreadId;

        @JsonProperty("counterpart_agent")
        private final AgentInfo counterpartAgent;

        private final List<ThreadNote> notes;
        private final int page;
        private final int size;

        @JsonProperty("total_elements")
        private final long totalElements;

        @JsonProperty("total_pages")
        private final int totalPages;

        @JsonProperty("has_next")
        private final boolean hasNext;

        @JsonProperty("has_previous")
        private final boolean hasPrevious;

        public ThreadResponse(Long noteThreadId, AgentInfo counterpartAgent, Page<ThreadNote> page) {
            this.noteThreadId = noteThreadId;
            this.counterpartAgent = counterpartAgent;
            this.notes = page.getContent();
            this.page = page.getNumber();
            this.size = page.getSize();
            this.totalElements = page.getTotalElements();
            this.totalPages = page.getTotalPages();
            this.hasNext = page.hasNext();
            this.hasPrevious = page.hasPrevious();
        }
    }

    @Getter
    @Builder
    public static class SendResponse {
        private String status;

        @JsonProperty("note_thread_id")
        private Long noteThreadId;

        @JsonProperty("note_id")
        private Long noteId;

        @JsonProperty("sent_at")
        private OffsetDateTime sentAt;
    }

    @Getter
    @Builder
    public static class ReadResponse {
        @JsonProperty("note_thread_id")
        private Long noteThreadId;

        @JsonProperty("marked_read")
        private boolean markedRead;

        @JsonProperty("remaining_unread_count")
        private long remainingUnreadCount;
    }

    @Getter
    @Builder
    public static class Summary {
        @JsonProperty("unread_thread_count")
        private long unreadThreadCount;

        @JsonProperty("unread_note_count")
        private long unreadNoteCount;
    }
}
