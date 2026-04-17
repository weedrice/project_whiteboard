package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.post.entity.Scrap;
import com.weedrice.whiteboard.domain.post.entity.ScrapId;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScrapRepository extends JpaRepository<Scrap, ScrapId> {
    @EntityGraph(attributePaths = { "post", "post.board", "post.user" })
    @Query(value = """
            SELECT s
            FROM Scrap s
            WHERE s.user = :user
            ORDER BY s.createdAt DESC
            """, countQuery = """
            SELECT COUNT(s)
            FROM Scrap s
            WHERE s.user = :user
            """)
    Page<Scrap> findPageByUserWithPostDetails(@Param("user") User user, Pageable pageable);

    java.util.List<Scrap> findByUserAndPostIn(User user,
            java.util.List<com.weedrice.whiteboard.domain.post.entity.Post> posts);

    long deleteByUser_UserIdAndPost_PostId(Long userId, Long postId);
}
