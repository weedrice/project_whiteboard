package com.weedrice.whiteboard.domain.common.repository;

import com.weedrice.whiteboard.domain.common.entity.CommonCodeDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommonCodeDetailRepository extends JpaRepository<CommonCodeDetail, Long> {
    List<CommonCodeDetail> findByCommonCode_TypeCodeAndIsActiveOrderBySortOrderAsc(String typeCode, Boolean isActive);
    Optional<CommonCodeDetail> findByCommonCode_TypeCodeAndCodeValue(String typeCode, String codeValue);
}
