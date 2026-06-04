package com.weedrice.whiteboard.domain.comment.service;

import org.springframework.data.domain.Sort;

public final class CommentReadSorts {

    public static final Sort READ_ORDER = Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("commentId"));

    private CommentReadSorts() {
    }
}
