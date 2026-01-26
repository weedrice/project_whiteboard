package com.weedrice.whiteboard.domain.emoticon.repository;

import com.weedrice.whiteboard.domain.emoticon.entity.EmoticonPurchase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmoticonPurchaseRepository extends JpaRepository<EmoticonPurchase, Long> {

    // 사용자가 해당 이모티콘을 구매했는지 확인
    @Query("SELECT ep FROM EmoticonPurchase ep WHERE ep.user.userId = :userId AND ep.emoticon.emoticonId = :emoticonId")
    Optional<EmoticonPurchase> findByUserIdAndEmoticonId(@Param("userId") Long userId, @Param("emoticonId") Long emoticonId);

    // 사용자가 해당 이모티콘을 구매했는지 여부
    boolean existsByUser_UserIdAndEmoticon_EmoticonId(Long userId, Long emoticonId);

    // 사용자가 구매한 이모티콘 목록
    @Query("SELECT ep FROM EmoticonPurchase ep JOIN FETCH ep.emoticon WHERE ep.user.userId = :userId ORDER BY ep.createdAt DESC")
    Page<EmoticonPurchase> findByUserId(@Param("userId") Long userId, Pageable pageable);
}
