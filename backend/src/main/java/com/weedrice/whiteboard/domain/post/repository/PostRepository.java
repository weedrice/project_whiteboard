package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long>, PostRepositoryCustom {
        List<Post> findByCreatedAtAfterAndIsDeleted(LocalDateTime dateTime, Boolean isDeleted);
        Page<Post> findByUserAndIsDeleted(User user, Boolean isDeleted, Pageable pageable);
        List<Post> findByBoard_BoardIdAndIsNoticeAndIsDeletedOrderByCreatedAtDesc(Long boardId, Boolean isNotice, Boolean isDeleted);
        
        // 최신 게시글 N개 조회 (BoardList에서 사용)
        List<Post> findByBoardAndIsDeletedOrderByCreatedAtDesc(Board board, Boolean isDeleted, Pageable pageable);
    
        long countByUserAndIsDeleted(User user, Boolean isDeleted); // Added for UserProfileDto

    void deleteByBoard(Board board);

    @org.springframework.data.jpa.repository.Query("SELECT p FROM Post p " +
            "WHERE p.createdAt >= :since " +
            "AND p.isDeleted = false " +
            "AND EXISTS (SELECT f FROM File f WHERE f.relatedId = p.postId AND f.relatedType = 'POST_CONTENT' AND f.mimeType LIKE 'image/%') "
            +
            "ORDER BY (p.viewCount * 1 + p.likeCount * 10) DESC")
    List<Post> findTrendingPosts(@org.springframework.data.repository.query.Param("since") LocalDateTime since,
            Pageable pageable);
}
