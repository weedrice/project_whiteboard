package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.post.entity.Scrap;
import com.weedrice.whiteboard.domain.post.entity.ScrapId;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScrapRepository extends JpaRepository<Scrap, ScrapId> {
    Page<Scrap> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    java.util.List<Scrap> findByUserAndPostIn(User user,
            java.util.List<com.weedrice.whiteboard.domain.post.entity.Post> posts);
}
