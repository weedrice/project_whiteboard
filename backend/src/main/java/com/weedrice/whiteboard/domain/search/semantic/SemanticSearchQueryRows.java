package com.weedrice.whiteboard.domain.search.semantic;

import java.util.List;

record SemanticSearchQueryRows(List<SemanticSearchRow> results, long totalElements) {
}
