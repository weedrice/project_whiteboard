package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.post.entity.DraftPost;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DraftPostRepository extends JpaRepository<DraftPost, Long> {
    @EntityGraph(attributePaths = "board")
    @Query(value = """
            SELECT d
            FROM DraftPost d
            WHERE d.user = :user
            ORDER BY d.modifiedAt DESC
            """, countQuery = """
            SELECT COUNT(d)
            FROM DraftPost d
            WHERE d.user = :user
            """)
    Page<DraftPost> findPageByUserWithBoard(@Param("user") User user, Pageable pageable);

    Optional<DraftPost> findByDraftIdAndUser(Long draftId, User user);
    void deleteByBoard(Board board);
}
