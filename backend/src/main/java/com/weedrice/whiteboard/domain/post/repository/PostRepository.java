package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long>, PostRepositoryCustom {
        List<Post> findByCreatedAtAfterAndIsDeleted(LocalDateTime dateTime, String isDeleted);

        Page<Post> findByUserAndIsDeletedOrderByCreatedAtDesc(User user, String isDeleted, Pageable pageable);

        List<Post> findByBoard_BoardIdAndIsNoticeAndIsDeletedOrderByCreatedAtDesc(Long boardId, String isNotice,
                        String isDeleted);
}
