package com.weedrice.whiteboard.domain.ad.dto;

import com.weedrice.whiteboard.domain.ad.entity.Ad;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdResponse {
    private Long adId;
    private String title;
    private String imageUrl;
    private String targetUrl;
    private String placement;

    public static AdResponse from(Ad ad) {
        if (ad == null) {
            return null;
        }
        return AdResponse.builder()
                .adId(ad.getAdId())
                .title(ad.getAdName())
                .imageUrl(ad.getImageUrl())
                .targetUrl(ad.getTargetUrl())
                .placement(ad.getPlacement())
                .build();
    }
}
