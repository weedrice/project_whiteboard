package com.weedrice.whiteboard.domain.emoticon.repository;

public record EmoticonSearchCondition(
        String keyword,
        SearchType searchType,
        SortType sortType) {

    public enum SearchType {
        NAME,
        CREATOR,
        TAG,
        ALL
    }

    public enum SortType {
        LATEST,
        OLDEST,
        POPULAR
    }
}
