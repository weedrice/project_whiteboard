package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.PostLike;
import com.weedrice.whiteboard.domain.post.entity.PostLikeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PostLikeRepository extends JpaRepository<PostLike, PostLikeId> {
    List<PostLike> findByUserIdAndPostIn(Long userId, List<Post> posts);

    @Query("""
            SELECT pl.post.postId
            FROM PostLike pl
            WHERE pl.userId = :userId
              AND pl.post.postId IN :postIds
            """)
    List<Long> findPostIdsByUserIdAndPostIdIn(@Param("userId") Long userId,
            @Param("postIds") Collection<Long> postIds);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM PostLike pl WHERE pl.userId = :userId AND pl.post.postId = :postId")
    int deleteByUserIdAndPostId(@Param("userId") Long userId, @Param("postId") Long postId);
}
