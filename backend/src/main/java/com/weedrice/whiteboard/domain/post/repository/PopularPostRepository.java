package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.post.entity.PopularPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PopularPostRepository extends JpaRepository<PopularPost, Long> {
    Page<PopularPost> findByRankingTypeOrderByRankAsc(String rankingType, Pageable pageable);
}
