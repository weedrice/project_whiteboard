package com.weedrice.whiteboard.domain.point.dto;

import com.weedrice.whiteboard.domain.point.entity.UserPoint;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserPointResponse {
    private int currentPoint;

    public static UserPointResponse from(UserPoint userPoint) {
        return UserPointResponse.builder()
                .currentPoint(userPoint.getCurrentPoint())
                .build();
    }
}
