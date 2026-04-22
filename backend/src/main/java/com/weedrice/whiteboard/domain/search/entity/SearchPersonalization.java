package com.weedrice.whiteboard.domain.search.entity;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "search_personalization",
        indexes = {
                @Index(name = "idx_search_personalization_user", columnList = "user_id, searched_at DESC")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_search_personalization_user_normalized_keyword",
                        columnNames = {"user_id", "normalized_keyword"})
        }
)
public class SearchPersonalization extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "keyword", length = 255, nullable = false)
    private String keyword;

    @Column(name = "normalized_keyword", length = 255, nullable = false)
    private String normalizedKeyword;

    @Column(name = "searched_at", nullable = false)
    private LocalDateTime searchedAt;

    @Builder
    public SearchPersonalization(User user, String keyword, String normalizedKeyword, LocalDateTime searchedAt) {
        this.user = user;
        this.keyword = keyword;
        this.normalizedKeyword = normalizedKeyword;
        this.searchedAt = searchedAt;
    }

    public void refresh(String keyword, LocalDateTime searchedAt) {
        this.keyword = keyword;
        this.searchedAt = searchedAt;
    }
}
