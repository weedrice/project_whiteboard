package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.post.dto.PostVersionResponse;
import com.weedrice.whiteboard.domain.post.entity.PostVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostVersionRepository extends JpaRepository<PostVersion, Long> {
    @Query("select count(version) from PostVersion version where version.post.postId = :postId and version.versionType = :versionType")
    long countByPostIdAndVersionType(@Param("postId") Long postId, @Param("versionType") String versionType);

    @Query("""
            select new com.weedrice.whiteboard.domain.post.dto.PostVersionResponse(
                version.historyId,
                version.versionType,
                coalesce(version.originalTitle,
                    case when version.versionType = 'CREATE' then (
                        select edit.originalTitle from PostVersion edit
                        where edit.post = post and edit.versionType = 'MODIFY'
                        order by edit.createdAt, edit.historyId
                        limit 1
                    ) end, post.title),
                version.createdAt
            )
            from PostVersion version
            join version.post post
            where post.postId = :postId
            order by version.createdAt desc
            """)
    List<PostVersionResponse> findVersionResponsesByPostId(@Param("postId") Long postId);
}
