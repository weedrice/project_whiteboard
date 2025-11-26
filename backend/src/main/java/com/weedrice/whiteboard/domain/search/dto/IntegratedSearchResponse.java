package com.weedrice.whiteboard.domain.search.dto;

import com.weedrice.whiteboard.domain.post.entity.Post;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class IntegratedSearchResponse {
    private PostSearchResult posts;
    // private CommentSearchResult comments; // TODO: 댓글 검색 구현 시 추가
    // private UserSearchResult users; // TODO: 사용자 검색 구현 시 추가

    @Getter
    @Builder
    public static class PostSearchResult {
        private List<PostSummary> content;
        private long totalElements;
    }

    @Getter
    @Builder
    public static class PostSummary {
        private Long postId;
        private String title;
        private String contentsSnippet; // 검색어 하이라이팅 또는 요약
        private String boardName;
        private AuthorInfo author;
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    public static class AuthorInfo {
        private Long userId;
        private String displayName;
    }

    public static IntegratedSearchResponse fromPosts(Page<Post> postPage, String keyword) {
        List<PostSummary> postSummaries = postPage.getContent().stream()
                .map(post -> PostSummary.builder()
                        .postId(post.getPostId())
                        .title(post.getTitle())
                        .contentsSnippet(generateSnippet(post.getContents(), keyword)) // 스니펫 생성
                        .boardName(post.getBoard().getBoardName())
                        .author(AuthorInfo.builder()
                                .userId(post.getUser().getUserId())
                                .displayName(post.getUser().getDisplayName())
                                .build())
                        .createdAt(post.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return IntegratedSearchResponse.builder()
                .posts(PostSearchResult.builder()
                        .content(postSummaries)
                        .totalElements(postPage.getTotalElements())
                        .build())
                .build();
    }

    // 간단한 스니펫 생성 로직 (실제로는 더 정교한 로직 필요)
    private static String generateSnippet(String fullText, String keyword) {
        if (fullText == null || keyword == null || keyword.isEmpty()) {
            return fullText;
        }
        int keywordIndex = fullText.toLowerCase().indexOf(keyword.toLowerCase());
        if (keywordIndex == -1) {
            return fullText.substring(0, Math.min(fullText.length(), 100)) + (fullText.length() > 100 ? "..." : "");
        }
        int start = Math.max(0, keywordIndex - 50);
        int end = Math.min(fullText.length(), keywordIndex + keyword.length() + 50);
        String snippet = fullText.substring(start, end);
        if (start > 0) snippet = "..." + snippet;
        if (end < fullText.length()) snippet = snippet + "...";
        return snippet.replaceAll("(?i)" + keyword, "<b>" + keyword + "</b>"); // 간단한 하이라이팅
    }
}
