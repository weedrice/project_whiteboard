package com.weedrice.whiteboard.domain.comment.service;

import com.weedrice.whiteboard.domain.user.entity.User;

import java.util.List;

record CommentReadContext(User viewer, List<Long> blockedUserIds) {
}
