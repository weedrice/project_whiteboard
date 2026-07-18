package com.weedrice.whiteboard.domain.tag.dto;

import com.weedrice.whiteboard.domain.tag.entity.Tag;
import com.weedrice.whiteboard.domain.tag.model.PublicTagCount;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class TagResponse {
    private List<TagInfo> tags;

    @Getter
    @Builder
    public static class TagInfo {
        private Long tagId;
        private String tagName;
        private int postCount;
    }

    public static TagResponse from(List<Tag> tags) {
        List<TagInfo> tagInfos = tags.stream()
                .map(tag -> TagInfo.builder()
                        .tagId(tag.getTagId())
                        .tagName(tag.getTagName())
                        .postCount(tag.getPostCount())
                        .build())
                .collect(Collectors.toList());
        return TagResponse.builder().tags(tagInfos).build();
    }

    public static TagResponse fromPublicCounts(List<PublicTagCount> tags) {
        List<TagInfo> tagInfos = tags.stream()
                .map(tag -> TagInfo.builder()
                        .tagId(tag.tagId())
                        .tagName(tag.tagName())
                        .postCount(Math.toIntExact(tag.postCount()))
                        .build())
                .collect(Collectors.toList());
        return TagResponse.builder().tags(tagInfos).build();
    }
}
