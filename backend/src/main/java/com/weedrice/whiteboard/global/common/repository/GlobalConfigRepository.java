package com.weedrice.whiteboard.global.common.repository;

import com.weedrice.whiteboard.global.common.entity.GlobalConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GlobalConfigRepository extends JpaRepository<GlobalConfig, String> {
}
