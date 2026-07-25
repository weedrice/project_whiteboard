package com.weedrice.whiteboard.domain.file.service;

import java.util.Locale;

/**
 * 업로드 대상별 크기·해상도 상한.
 *
 * <p>이 값들은 원래 프론트엔드에만 있어서 UI를 거치지 않는 호출에는 강제력이 없었다.
 * 스페이스 아이콘에 10MB 이미지를, 프로필 이미지에 16384×16384 이미지를 올릴 수 있었다.
 *
 * <p>{@link #GENERIC}은 대상을 지정하지 않은 요청의 기본값이며 기존 동작과 같다.
 * 새 업로드 화면을 붙일 때는 대상을 명시해 서버에서도 같은 제한이 걸리게 한다.
 */
public enum FileUploadTarget {

    /** 대상을 지정하지 않은 업로드. 기존 계약을 유지한다. */
    GENERIC(10L * 1024 * 1024, 0, 0),

    /** 게시글 본문 이미지. */
    POST_CONTENT(10L * 1024 * 1024, 0, 0),

    /** 스페이스 아이콘. 작게 렌더링되므로 원본도 작아야 한다. */
    BOARD_ICON(2L * 1024 * 1024, 0, 0),

    /** 프로필 이미지. 프론트에서 100×100으로 줄여 올린다. */
    PROFILE_IMAGE(10L * 1024 * 1024, 512, 512),

    /** 이모티콘 이미지. */
    EMOTICON(10L * 1024 * 1024, 0, 0);

    private final long maxSizeBytes;
    private final int maxWidth;
    private final int maxHeight;

    FileUploadTarget(long maxSizeBytes, int maxWidth, int maxHeight) {
        this.maxSizeBytes = maxSizeBytes;
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
    }

    public long getMaxSizeBytes() {
        return maxSizeBytes;
    }

    /** 0이면 대상별 해상도 제한이 없고 전역 상한만 적용된다. */
    public int getMaxWidth() {
        return maxWidth;
    }

    /** 0이면 대상별 해상도 제한이 없고 전역 상한만 적용된다. */
    public int getMaxHeight() {
        return maxHeight;
    }

    public boolean hasDimensionLimit() {
        return maxWidth > 0 && maxHeight > 0;
    }

    /**
     * 알 수 없는 값이면 {@link #GENERIC}으로 떨어진다.
     * 클라이언트가 오래된 이름을 보내도 업로드가 막히지 않게 하려는 의도다.
     */
    public static FileUploadTarget from(String value) {
        if (value == null || value.isBlank()) {
            return GENERIC;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return GENERIC;
        }
    }
}
