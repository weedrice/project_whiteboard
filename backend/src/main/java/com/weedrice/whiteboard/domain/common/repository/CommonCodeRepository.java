package com.weedrice.whiteboard.domain.common.repository;

import com.weedrice.whiteboard.domain.common.entity.CommonCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommonCodeRepository extends JpaRepository<CommonCode, String> {
    List<CommonCode> findAllByOrderByTypeCodeAsc();
}
