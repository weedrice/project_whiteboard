package com.weedrice.whiteboard.domain.user.repository;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserBlock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    boolean existsByUserAndTarget(User user, User target);

    Optional<UserBlock> findByUserAndTarget(User user, User target);

    Page<UserBlock> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);
}