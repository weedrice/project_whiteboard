package com.weedrice.whiteboard.domain.ad.dto;

import com.weedrice.whiteboard.domain.ad.entity.Ad;
import lombok.Builder;

@Builder
public record AdResponse(Long adId, String title, String imageUrl, String targetUrl, String placement) {

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
