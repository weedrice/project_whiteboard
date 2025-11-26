package com.weedrice.whiteboard.domain.sanction.repository;

import com.weedrice.whiteboard.domain.sanction.entity.Sanction;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface SanctionRepository extends JpaRepository<Sanction, Long> {
    Page<Sanction> findByTargetUser(User targetUser, Pageable pageable);
    Optional<Sanction> findFirstByTargetUserAndTypeAndEndDateAfterOrderByEndDateDesc(User targetUser, String type, LocalDateTime now);
}
