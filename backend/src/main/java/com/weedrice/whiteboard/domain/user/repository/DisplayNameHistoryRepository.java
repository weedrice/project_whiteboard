package com.weedrice.whiteboard.domain.user.repository;

import com.weedrice.whiteboard.domain.user.entity.DisplayNameHistory;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DisplayNameHistoryRepository extends JpaRepository<DisplayNameHistory, Long> {

    List<DisplayNameHistory> findByUserOrderByChangedAtDesc(User user);
}