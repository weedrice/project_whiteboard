package com.weedrice.whiteboard.domain.tag.service;

import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.tag.entity.PostTag;
import com.weedrice.whiteboard.domain.tag.entity.Tag;
import com.weedrice.whiteboard.domain.tag.repository.PostTagRepository;
import com.weedrice.whiteboard.domain.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TagService {

    private final TagRepository tagRepository;
    private final PostTagRepository postTagRepository;

    @Transactional
    public void processTagsForPost(Post post, List<String> newTagNames) {
        // 기존 태그 가져오기
        List<PostTag> existingPostTags = postTagRepository.findByPost(post);
        Set<String> existingTagNames = existingPostTags.stream()
                .map(postTag -> postTag.getTag().getTagName())
                .collect(Collectors.toSet());

        // 삭제할 태그 처리
        for (PostTag postTag : existingPostTags) {
            if (!newTagNames.contains(postTag.getTag().getTagName())) {
                postTagRepository.delete(postTag);
                postTag.getTag().decrementPostCount(); // 태그 사용 횟수 감소
            }
        }

        // 추가할 태그 처리
        for (String newTagName : newTagNames) {
            if (!existingTagNames.contains(newTagName)) {
                Tag tag = tagRepository.findByTagName(newTagName)
                        .orElseGet(() -> tagRepository.save(new Tag(newTagName))); // 없으면 새로 생성

                PostTag postTag = PostTag.builder()
                        .post(post)
                        .tag(tag)
                        .build();
                postTagRepository.save(postTag);
                tag.incrementPostCount(); // 태그 사용 횟수 증가
            }
        }
    }

    public List<Tag> getPopularTags() {
        // TODO: 인기 태그 조회 로직 (예: 일정 기간 내 post_count가 높은 태그)
        // 현재는 단순히 post_count가 높은 순으로 정렬
        return tagRepository.findAll().stream()
                .sorted((t1, t2) -> t2.getPostCount().compareTo(t1.getPostCount()))
                .limit(10) // 상위 10개 태그
                .collect(Collectors.toList());
    }
}
