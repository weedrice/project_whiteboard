package com.weedrice.whiteboard.domain.tag.repository;

import java.util.Collection;

public interface TagRepositoryCustom {

    int insertIgnore(String tagName);

    int insertIgnoreAll(Collection<String> tagNames);
}
