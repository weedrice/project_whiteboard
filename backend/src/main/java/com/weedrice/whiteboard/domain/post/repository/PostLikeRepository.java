package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.post.entity.PostLike;
import com.weedrice.whiteboard.domain.post.entity.PostLikeId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostLikeRepository extends JpaRepository<PostLike, PostLikeId> {
    java.util.List<PostLike> findByUserAndPostIn(com.weedrice.whiteboard.domain.user.entity.User user,
            java.util.List<com.weedrice.whiteboard.domain.post.entity.Post> posts);
}
