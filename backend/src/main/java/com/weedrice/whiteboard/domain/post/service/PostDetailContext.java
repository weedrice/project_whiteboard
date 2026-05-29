package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.ViewHistory;

record PostDetailContext(Post post, PostReadContext readContext, ViewHistory viewHistory) {
}
