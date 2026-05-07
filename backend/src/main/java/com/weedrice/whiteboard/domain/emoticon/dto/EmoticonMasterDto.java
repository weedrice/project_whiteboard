package com.weedrice.whiteboard.domain.emoticon.dto;

import com.weedrice.whiteboard.domain.emoticon.entity.EmoticonImage;
import com.weedrice.whiteboard.domain.emoticon.entity.EmoticonMaster;
import com.weedrice.whiteboard.domain.file.support.FileUrlResolver;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmoticonMasterDto {
    private Long emoticonId;
    private String name;
    private String thumbnailUrl;
    private List<String> tags;
    private Boolean isActive;
    private Long creatorId;
    private String creatorName;
    private Integer purchaseCount;
    private List<EmoticonImageDto> images;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;

    public static EmoticonMasterDto from(EmoticonMaster master) {
        return EmoticonMasterDto.builder()
                .emoticonId(master.getEmoticonId())
                .name(master.getName())
                .thumbnailUrl(FileUrlResolver.normalize(master.getThumbnailUrl()))
                .tags(master.getTags())
                .isActive("Y".equals(master.getIsActive()))
                .creatorId(master.getCreator() != null ? master.getCreator().getUserId() : null)
                .creatorName(master.getCreator() != null ? master.getCreator().getDisplayName() : null)
                .purchaseCount(master.getPurchaseCount())
                .images(master.getImages().stream()
                        .sorted(Comparator.comparing(EmoticonImage::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                                .thenComparing(EmoticonImage::getImageId, Comparator.nullsLast(Long::compareTo)))
                        .map(EmoticonImageDto::from)
                        .collect(Collectors.toList()))
                .createdAt(master.getCreatedAt())
                .modifiedAt(master.getModifiedAt())
                .build();
    }

    // 이미지 제외 버전 (목록 조회용)
    public static EmoticonMasterDto fromWithoutImages(EmoticonMaster master) {
        Long creatorId = master.getCreator() != null ? master.getCreator().getUserId() : null;
        return fromWithoutImages(master, creatorId, null);
    }

    public static EmoticonMasterDto fromWithoutImages(EmoticonMaster master, Long creatorId, String creatorName) {
        return EmoticonMasterDto.builder()
                .emoticonId(master.getEmoticonId())
                .name(master.getName())
                .thumbnailUrl(FileUrlResolver.normalize(master.getThumbnailUrl()))
                .tags(master.getTags())
                .isActive("Y".equals(master.getIsActive()))
                .creatorId(creatorId)
                .creatorName(creatorName)
                .purchaseCount(master.getPurchaseCount())
                .createdAt(master.getCreatedAt())
                .modifiedAt(master.getModifiedAt())
                .build();
    }
}
