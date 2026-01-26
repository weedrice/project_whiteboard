package com.weedrice.whiteboard.domain.emoticon.dto;

import com.weedrice.whiteboard.domain.emoticon.entity.EmoticonImage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmoticonImageDto {
    private Long imageId;
    private Long emoticonId;
    private String imageUrl;
    private Integer sortOrder;

    public static EmoticonImageDto from(EmoticonImage image) {
        return EmoticonImageDto.builder()
                .imageId(image.getImageId())
                .emoticonId(image.getEmoticonMaster().getEmoticonId())
                .imageUrl(image.getImageUrl())
                .sortOrder(image.getSortOrder())
                .build();
    }
}
