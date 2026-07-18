package com.weedrice.whiteboard.domain.point.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ContentRewardPolicy {

    POST("POINT_POST_CREATE_REWARD", 50, "POST", "게시글 작성", "게시글 삭제"),
    COMMENT("POINT_COMMENT_CREATE_REWARD", 10, "COMMENT", "댓글 작성", "댓글 삭제"),
    ATTENDANCE("POINT_ATTENDANCE_CREATE_REWARD", 10, "ATTENDANCE", "출석 체크", "출석 보상 회수"),
    ATTENDANCE_STREAK_7("POINT_ATTENDANCE_STREAK_7_REWARD", 30, "ATTENDANCE_STREAK_7", "7일 연속 출석 보너스", "7일 연속 출석 보상 회수"),
    ATTENDANCE_STREAK_30("POINT_ATTENDANCE_STREAK_30_REWARD", 100, "ATTENDANCE_STREAK_30", "30일 연속 출석 보너스", "30일 연속 출석 보상 회수");

    private final String configKey;
    private final int defaultCreateReward;
    private final String relatedType;
    private final String createDescription;
    private final String deleteDescription;
}
