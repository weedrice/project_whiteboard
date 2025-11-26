package com.weedrice.whiteboard.global.common.repository;

import com.weedrice.whiteboard.global.common.entity.CommonCodeDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommonCodeDetailRepository extends JpaRepository<CommonCodeDetail, Long> {

    @Query("SELECT d FROM CommonCodeDetail d WHERE d.commonCode.typeCode = :typeCode AND d.isActive = 'Y' ORDER BY d.sortOrder")
    List<CommonCodeDetail> findActiveDetailsByTypeCode(@Param("typeCode") String typeCode);
}
