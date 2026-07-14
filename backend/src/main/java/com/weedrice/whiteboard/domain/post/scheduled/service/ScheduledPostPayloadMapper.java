package com.weedrice.whiteboard.domain.post.scheduled.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weedrice.whiteboard.domain.post.dto.PollRequest;
import com.weedrice.whiteboard.domain.post.dto.PostCreateRequest;
import com.weedrice.whiteboard.domain.post.scheduled.dto.ScheduledPostRequest;
import com.weedrice.whiteboard.domain.post.scheduled.entity.ScheduledPost;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
class ScheduledPostPayloadMapper {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Long>> LONG_LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    String tagsJson(ScheduledPostRequest request) {
        return toJson(request.getTags());
    }

    String fileIdsJson(ScheduledPostRequest request) {
        return toJson(request.getFileIds());
    }

    String pollJson(ScheduledPostRequest request) {
        return toJson(request.getPoll());
    }

    PostCreateRequest toPostCreateRequest(ScheduledPost scheduledPost) {
        return new PostCreateRequest(
                scheduledPost.getCategoryId(),
                scheduledPost.getTitle(),
                scheduledPost.getContents(),
                fromJson(scheduledPost.getTagsJson(), STRING_LIST_TYPE),
                Boolean.TRUE.equals(scheduledPost.getIsNotice()),
                Boolean.TRUE.equals(scheduledPost.getIsNsfw()),
                Boolean.TRUE.equals(scheduledPost.getIsSpoiler()),
                Boolean.TRUE.equals(scheduledPost.getIsSecret()),
                scheduledPost.getDraftId(),
                fromJson(scheduledPost.getFileIdsJson(), LONG_LIST_TYPE),
                fromJson(scheduledPost.getPollJson(), PollRequest.class),
                scheduledPost.getSeriesId());
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private <T> T fromJson(String value, TypeReference<T> typeReference) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(value, typeReference);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private <T> T fromJson(String value, Class<T> type) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
