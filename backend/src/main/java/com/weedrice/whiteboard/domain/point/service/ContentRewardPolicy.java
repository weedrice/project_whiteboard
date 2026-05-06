package com.weedrice.whiteboard.domain.point.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ContentRewardPolicy {

    POST("POINT_POST_CREATE_REWARD", 50, "POST", "게시글 작성", "게시글 삭제"),
    COMMENT("POINT_COMMENT_CREATE_REWARD", 10, "COMMENT", "댓글 작성", "댓글 삭제");

    private final String configKey;
    private final int defaultCreateReward;
    private final String relatedType;
    private final String createDescription;
    private final String deleteDescription;
}
