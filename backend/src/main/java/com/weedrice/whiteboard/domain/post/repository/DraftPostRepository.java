package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.post.entity.DraftPost;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DraftPostRepository extends JpaRepository<DraftPost, Long> {
    Page<DraftPost> findByUserOrderByModifiedAtDesc(User user, Pageable pageable);
    Optional<DraftPost> findByDraftIdAndUser(Long draftId, User user);
    void deleteByBoard(Board board);
}
