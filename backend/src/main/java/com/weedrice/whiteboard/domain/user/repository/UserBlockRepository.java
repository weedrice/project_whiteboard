package com.weedrice.whiteboard.domain.user.repository;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserBlock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    boolean existsByUser_UserIdAndTarget_UserId(Long userId, Long targetUserId);

    @Query("""
            SELECT CASE WHEN COUNT(ub) > 0 THEN true ELSE false END
            FROM UserBlock ub
            WHERE (ub.user.userId = :userAId AND ub.target.userId = :userBId)
               OR (ub.user.userId = :userBId AND ub.target.userId = :userAId)
            """)
    boolean existsEitherDirection(@Param("userAId") Long userAId, @Param("userBId") Long userBId);

    @Query("""
            SELECT ub.target.userId
            FROM UserBlock ub
            WHERE ub.user.userId = :userId
            """)
    List<Long> findTargetUserIdsByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT CASE
                     WHEN ub.user.userId = :userId THEN ub.target.userId
                     ELSE ub.user.userId
                   END
            FROM UserBlock ub
            WHERE ub.user.userId = :userId
               OR ub.target.userId = :userId
            """)
    List<Long> findBlockedUserIdsEitherDirectionByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT CASE
                     WHEN ub.user.userId = :userId THEN ub.target.userId
                     ELSE ub.user.userId
                   END
            FROM UserBlock ub
            WHERE (ub.user.userId = :userId AND ub.target.userId IN :candidateUserIds)
               OR (ub.target.userId = :userId AND ub.user.userId IN :candidateUserIds)
            """)
    List<Long> findBlockedCandidateUserIdsEitherDirection(
            @Param("userId") Long userId,
            @Param("candidateUserIds") List<Long> candidateUserIds);

    Optional<UserBlock> findByUserAndTarget(User user, User target);
    @EntityGraph(attributePaths = "target")
    @Query(value = """
            SELECT ub
            FROM UserBlock ub
            WHERE ub.user = :user
              AND ub.target.status = 'ACTIVE'
              AND ub.target.deletedAt IS NULL
            ORDER BY ub.createdAt DESC, ub.relationId DESC
            """, countQuery = """
            SELECT COUNT(ub)
            FROM UserBlock ub
            WHERE ub.user = :user
              AND ub.target.status = 'ACTIVE'
              AND ub.target.deletedAt IS NULL
            """)
    Page<UserBlock> findPageByUserWithTarget(@Param("user") User user, Pageable pageable);

    @EntityGraph(attributePaths = "target")
    @Query("""
            SELECT ub
            FROM UserBlock ub
            WHERE ub.user = :user
            """)
    List<UserBlock> findByUserWithTarget(@Param("user") User user);

    @EntityGraph(attributePaths = "user")
    @Query("""
            SELECT ub
            FROM UserBlock ub
            WHERE ub.target = :target
            """)
    List<UserBlock> findByTargetWithUser(@Param("target") User target);
}
