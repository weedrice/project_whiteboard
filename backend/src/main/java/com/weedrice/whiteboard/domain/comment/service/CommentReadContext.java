package com.weedrice.whiteboard.domain.comment.service;

import com.weedrice.whiteboard.domain.user.entity.User;

import java.util.Set;

record CommentReadContext(User viewer, Set<Long> blockedUserIds) {
}
