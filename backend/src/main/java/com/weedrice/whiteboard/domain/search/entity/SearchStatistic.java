package com.weedrice.whiteboard.domain.search.entity;

import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDate;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "search_statistics", uniqueConstraints = {
        @UniqueConstraint(name = "uk_search_statistics_keyword_date", columnNames = {"keyword", "search_date"})
}, indexes = {
        @Index(name = "idx_search_statistics_date", columnList = "search_date, search_count DESC"),
        @Index(name = "idx_search_statistics_keyword", columnList = "keyword, search_date DESC")
})
public class SearchStatistic extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id; // PK를 keyword, search_date 복합키 대신 단일 ID로 변경

    @Column(name = "keyword", length = 255, nullable = false)
    private String keyword;

    @Column(name = "search_date", nullable = false)
    private LocalDate searchDate;

    @Column(name = "search_count", nullable = false)
    private Integer searchCount;

    @Builder
    public SearchStatistic(String keyword, LocalDate searchDate) {
        this.keyword = keyword;
        this.searchDate = searchDate;
        this.searchCount = 0;
    }

    public void incrementSearchCount() {
        this.searchCount++;
    }
}
