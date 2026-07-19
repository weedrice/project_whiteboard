package com.weedrice.whiteboard.domain.auth.repository;

import com.weedrice.whiteboard.domain.auth.entity.LoginHistory;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {

    Page<LoginHistory> findByUser(User user, Pageable pageable);

    Page<LoginHistory> findByIpAddressOrderByCreatedAtDesc(String ipAddress, Pageable pageable);

    Optional<LoginHistory> findTopByUserAndIsSuccessTrueOrderByCreatedAtDescHistoryIdDesc(User user);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            DELETE FROM login_histories
            WHERE history_id IN (
                SELECT history_id
                FROM login_histories
                WHERE created_at < :cutoff
                ORDER BY created_at ASC, history_id ASC
                LIMIT :batchSize
            )
            """, nativeQuery = true)
    int deleteCreatedBeforeBatch(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("batchSize") int batchSize);
}
