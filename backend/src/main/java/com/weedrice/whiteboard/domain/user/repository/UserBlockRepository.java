package com.weedrice.whiteboard.domain.user.repository;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserBlock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    boolean existsByUserAndTarget(User user, User target);

    boolean existsByUser_UserIdAndTarget_UserId(Long userId, Long targetUserId);

    @Query("""
            SELECT CASE WHEN COUNT(ub) > 0 THEN true ELSE false END
            FROM UserBlock ub
            WHERE (ub.user.userId = :userAId AND ub.target.userId = :userBId)
               OR (ub.user.userId = :userBId AND ub.target.userId = :userAId)
            """)
    boolean existsEitherDirection(@Param("userAId") Long userAId, @Param("userBId") Long userBId);

    Optional<UserBlock> findByUserAndTarget(User user, User target);

    Page<UserBlock> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    List<UserBlock> findByUser(User user);
}
