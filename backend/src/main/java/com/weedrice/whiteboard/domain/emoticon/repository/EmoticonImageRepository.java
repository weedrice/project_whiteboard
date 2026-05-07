package com.weedrice.whiteboard.domain.emoticon.repository;

import com.weedrice.whiteboard.domain.emoticon.entity.EmoticonImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmoticonImageRepository extends JpaRepository<EmoticonImage, Long> {

    // 특정 이모티콘의 이미지 목록 조회
    @Query("SELECT i FROM EmoticonImage i WHERE i.emoticonMaster.emoticonId = :emoticonId ORDER BY i.sortOrder ASC")
    List<EmoticonImage> findByEmoticonId(@Param("emoticonId") Long emoticonId);

    boolean existsByImageUrlIn(List<String> imageUrls);

    @Query("SELECT DISTINCT i.imageUrl FROM EmoticonImage i WHERE i.imageUrl IN :imageUrls")
    List<String> findReferencedImageUrls(@Param("imageUrls") List<String> imageUrls);

    // 특정 이모티콘의 이미지 삭제
    void deleteByEmoticonMaster_EmoticonId(Long emoticonId);
}
