package com.weedrice.whiteboard.domain.search.repository;

import com.weedrice.whiteboard.domain.search.entity.SearchPersonalization;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SearchPersonalizationRepository extends JpaRepository<SearchPersonalization, Long> {
    Page<SearchPersonalization> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);
    List<SearchPersonalization> findByUserAndKeyword(User user, String keyword);
    void deleteByUserAndKeyword(User user, String keyword);
    void deleteByUser(User user);
}
