package com.weedrice.whiteboard.domain.user.repository;

import com.weedrice.whiteboard.domain.user.entity.PasswordHistory;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, Long> {
    List<PasswordHistory> findTop4ByUserOrderByCreatedAtDescHistoryIdDesc(User user);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            DELETE FROM password_histories
            WHERE history_id IN (
                SELECT history_id
                FROM password_histories
                WHERE user_id = :userId
                ORDER BY created_at DESC, history_id DESC
                OFFSET 4
            )
            """, nativeQuery = true)
    int deleteOlderThanLatestFour(@Param("userId") Long userId);
}
