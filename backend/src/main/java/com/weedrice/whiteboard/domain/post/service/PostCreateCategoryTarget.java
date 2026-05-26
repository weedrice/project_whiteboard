package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.entity.BoardCategory;

record PostCreateCategoryTarget(
        BoardCategory category,
        boolean categoryWriteRolePrevalidated) {
}
