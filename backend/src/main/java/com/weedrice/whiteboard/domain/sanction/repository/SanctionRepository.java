package com.weedrice.whiteboard.domain.sanction.repository;

import com.weedrice.whiteboard.domain.sanction.entity.Sanction;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

public interface SanctionRepository extends JpaRepository<Sanction, Long> {
    @EntityGraph(attributePaths = {"targetUser", "admin"})
    Page<Sanction> findByTargetUser(User targetUser, Pageable pageable);
    @EntityGraph(attributePaths = {"targetUser", "admin"})
    Page<Sanction> findAll(Pageable pageable);
    Optional<Sanction> findFirstByTargetUserAndTypeAndEndDateAfterOrderByEndDateDesc(User targetUser, String type, LocalDateTime now);
    Optional<Sanction> findTopByTargetUserOrderByCreatedAtDesc(User targetUser);
    long countByTargetUser(User targetUser);

    @Query("""
            SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END
            FROM Sanction s
            WHERE s.targetUser = :targetUser
              AND UPPER(s.type) = 'BAN'
              AND s.startDate <= :now
              AND (s.endDate IS NULL OR s.endDate > :now)
            """)
    boolean existsActiveBan(@Param("targetUser") User targetUser, @Param("now") LocalDateTime now);

    @Query("""
            SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END
            FROM Sanction s
            WHERE s.targetUser = :targetUser
              AND UPPER(s.type) IN :types
              AND s.startDate <= :now
              AND (s.endDate IS NULL OR s.endDate > :now)
            """)
    boolean existsActiveTypeIn(@Param("targetUser") User targetUser,
                               @Param("types") Collection<String> types,
                               @Param("now") LocalDateTime now);
}
