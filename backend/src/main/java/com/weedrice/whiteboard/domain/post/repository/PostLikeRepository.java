package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.post.entity.PostLike;
import com.weedrice.whiteboard.domain.post.entity.PostLikeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PostLikeRepository extends JpaRepository<PostLike, PostLikeId> {
    java.util.List<PostLike> findByUserAndPostIn(com.weedrice.whiteboard.domain.user.entity.User user,
            java.util.List<com.weedrice.whiteboard.domain.post.entity.Post> posts);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM PostLike pl WHERE pl.id.user = :userId AND pl.id.post = :postId")
    int deleteByUserIdAndPostId(Long userId, Long postId);
}
