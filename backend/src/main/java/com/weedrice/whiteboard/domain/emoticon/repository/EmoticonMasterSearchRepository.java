package com.weedrice.whiteboard.domain.emoticon.repository;

import com.weedrice.whiteboard.domain.emoticon.entity.EmoticonMaster;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface EmoticonMasterSearchRepository {

    Page<EmoticonMaster> searchActive(EmoticonSearchCondition condition, Pageable pageable);
}
