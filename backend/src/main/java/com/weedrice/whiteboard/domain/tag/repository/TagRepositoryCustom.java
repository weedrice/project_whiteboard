package com.weedrice.whiteboard.domain.tag.repository;

import java.util.Collection;
import java.time.LocalDateTime;

public interface TagRepositoryCustom {

    int insertIgnore(String tagName);

    int insertIgnoreAll(Collection<String> tagNames);

    int deleteOrphanBatch(LocalDateTime cutoff, int batchSize);
}
