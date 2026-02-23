package com.weedrice.whiteboard.global.log.repository;

import com.weedrice.whiteboard.global.log.entity.ErrorLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ErrorLogRepository extends JpaRepository<ErrorLog, Long>, ErrorLogRepositoryCustom {

    long countByIsResolved(String isResolved);
}
