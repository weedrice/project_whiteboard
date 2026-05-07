package com.weedrice.whiteboard.domain.post.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.weedrice.whiteboard.domain.file.entity.FileStorageStatus;
import com.weedrice.whiteboard.domain.file.entity.QFile;
import com.weedrice.whiteboard.domain.post.entity.QPost;

final class TrendingPostRankingPolicy {

    private static final String RELATED_TYPE_POST_CONTENT = "POST_CONTENT";
    private static final String IMAGE_MIME_PATTERN = "image/%";
    private static final String IMAGE_TAG_MARKER = "<img";
    private static final String VIDEO_EMBED_TAG_MARKER = "<iframe";
    private static final int VIEW_SCORE_WEIGHT = 1;
    private static final int LIKE_SCORE_WEIGHT = 10;

    private TrendingPostRankingPolicy() {
    }

    static BooleanExpression mediaCondition(QPost post, QFile file) {
        BooleanExpression hasAttachedImage = JPAExpressions.selectOne()
                .from(file)
                .where(
                        file.relatedId.eq(post.postId),
                        file.relatedType.eq(RELATED_TYPE_POST_CONTENT),
                        file.mimeType.like(IMAGE_MIME_PATTERN),
                        activeFileStorageStatus(file))
                .exists();

        BooleanExpression hasImgTagInContent = post.contents.containsIgnoreCase(IMAGE_TAG_MARKER);
        BooleanExpression hasVideoInContent = post.contents.containsIgnoreCase(VIDEO_EMBED_TAG_MARKER);
        return hasAttachedImage.or(hasImgTagInContent).or(hasVideoInContent);
    }

    private static BooleanExpression activeFileStorageStatus(QFile file) {
        return file.storageStatus.eq(FileStorageStatus.ACTIVE)
                .or(file.storageStatus.isNull());
    }

    static NumberExpression<Integer> score(QPost post) {
        return post.viewCount.multiply(VIEW_SCORE_WEIGHT)
                .add(post.likeCount.multiply(LIKE_SCORE_WEIGHT));
    }
}
