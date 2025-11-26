package com.weedrice.whiteboard.domain.search.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PopularKeywordResponse {
    private List<KeywordInfo> keywords;

    @Getter
    @Builder
    public static class KeywordInfo {
        private int rank;
        private String keyword;
        private long count;
    }

    public static PopularKeywordResponse from(List<SearchService.PopularKeywordDto> popularKeywords) {
        List<KeywordInfo> keywordInfos = new java.util.ArrayList<>();
        for (int i = 0; i < popularKeywords.size(); i++) {
            SearchService.PopularKeywordDto dto = popularKeywords.get(i);
            keywordInfos.add(KeywordInfo.builder()
                    .rank(i + 1)
                    .keyword(dto.getKeyword())
                    .count(dto.getCount())
                    .build());
        }
        return PopularKeywordResponse.builder().keywords(keywordInfos).build();
    }
}
