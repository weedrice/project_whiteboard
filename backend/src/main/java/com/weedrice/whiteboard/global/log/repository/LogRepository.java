package com.weedrice.whiteboard.global.log.repository;

import com.weedrice.whiteboard.global.log.entity.Log;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LogRepository extends JpaRepository<Log, Long> {
}
