package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.PostVersion;
import com.weedrice.whiteboard.domain.post.repository.PostVersionRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PostVersionRecorder {

    private final PostVersionRepository postVersionRepository;

    void record(Post post, User modifier, String versionType, String originalTitle,
            String originalContents) {
        PostVersion postVersion = PostVersion.builder()
                .post(post)
                .modifier(modifier)
                .versionType(versionType)
                .originalTitle(originalTitle)
                .originalContents(originalContents)
                .build();
        postVersionRepository.save(postVersion);
    }
}
