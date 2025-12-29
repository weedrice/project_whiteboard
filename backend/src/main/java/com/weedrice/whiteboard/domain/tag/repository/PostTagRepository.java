package com.weedrice.whiteboard.domain.tag.repository;

import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.tag.entity.PostTag;
import com.weedrice.whiteboard.domain.tag.entity.PostTagId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostTagRepository extends JpaRepository<PostTag, PostTagId> {
    List<PostTag> findByPost(Post post);

    void deleteByPost(Post post);

    org.springframework.data.domain.Page<PostTag> findByTag_TagId(Long tagId,
            org.springframework.data.domain.Pageable pageable);
}
