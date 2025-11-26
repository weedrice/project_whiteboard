package com.weedrice.whiteboard.domain.search.dto;

import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.user.entity.User;
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
    private CommentSearchResult comments;
    private UserSearchResult users;

    @Getter
    @Builder
    public static class PostSearchResult {
        private List<PostSummary> content;
        private long totalElements;
    }

    @Getter
    @Builder
    public static class CommentSearchResult {
        private List<CommentSummary> content;
        private long totalElements;
    }

    @Getter
    @Builder
    public static class UserSearchResult {
        private List<UserSummary> content;
        private long totalElements;
    }

    @Getter
    @Builder
    public static class PostSummary {
        private Long postId;
        private String title;
        private String contentsSnippet;
        private String boardName;
        private AuthorInfo author;
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    public static class CommentSummary {
        private Long commentId;
        private String contentsSnippet;
        private Long postId;
        private String postTitle;
        private AuthorInfo author;
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    public static class UserSummary {
        private Long userId;
        private String displayName;
        private String profileImageUrl;
    }

    @Getter
    @Builder
    public static class AuthorInfo {
        private Long userId;
        private String displayName;
    }

    public static IntegratedSearchResponse from(Page<Post> postPage, Page<Comment> commentPage, Page<User> userPage, String keyword) {
        return IntegratedSearchResponse.builder()
                .posts(fromPosts(postPage, keyword))
                .comments(fromComments(commentPage, keyword))
                .users(fromUsers(userPage))
                .build();
    }

    private static PostSearchResult fromPosts(Page<Post> postPage, String keyword) {
        if (postPage == null) return null;
        List<PostSummary> postSummaries = postPage.getContent().stream()
                .map(post -> PostSummary.builder()
                        .postId(post.getPostId())
                        .title(post.getTitle())
                        .contentsSnippet(generateSnippet(post.getContents(), keyword))
                        .boardName(post.getBoard().getBoardName())
                        .author(AuthorInfo.builder()
                                .userId(post.getUser().getUserId())
                                .displayName(post.getUser().getDisplayName())
                                .build())
                        .createdAt(post.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return PostSearchResult.builder()
                .content(postSummaries)
                .totalElements(postPage.getTotalElements())
                .build();
    }

    private static CommentSearchResult fromComments(Page<Comment> commentPage, String keyword) {
        if (commentPage == null) return null;
        List<CommentSummary> commentSummaries = commentPage.getContent().stream()
                .map(comment -> CommentSummary.builder()
                        .commentId(comment.getCommentId())
                        .contentsSnippet(generateSnippet(comment.getContent(), keyword))
                        .postId(comment.getPost().getPostId())
                        .postTitle(comment.getPost().getTitle())
                        .author(AuthorInfo.builder()
                                .userId(comment.getUser().getUserId())
                                .displayName(comment.getUser().getDisplayName())
                                .build())
                        .createdAt(comment.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return CommentSearchResult.builder()
                .content(commentSummaries)
                .totalElements(commentPage.getTotalElements())
                .build();
    }

    private static UserSearchResult fromUsers(Page<User> userPage) {
        if (userPage == null) return null;
        List<UserSummary> userSummaries = userPage.getContent().stream()
                .map(user -> UserSummary.builder()
                        .userId(user.getUserId())
                        .displayName(user.getDisplayName())
                        .profileImageUrl(user.getProfileImageUrl())
                        .build())
                .collect(Collectors.toList());

        return UserSearchResult.builder()
                .content(userSummaries)
                .totalElements(userPage.getTotalElements())
                .build();
    }

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
        return snippet.replaceAll("(?i)" + keyword, "<b>" + keyword + "</b>");
    }
}
