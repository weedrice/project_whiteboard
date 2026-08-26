package com.weedrice.whiteboard.global.common.repository;

import com.weedrice.whiteboard.global.common.entity.GlobalConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;

public interface GlobalConfigRepository extends JpaRepository<GlobalConfig, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT config FROM GlobalConfig config WHERE config.configKey IN :keys ORDER BY config.configKey")
    List<GlobalConfig> findAllByKeysForUpdate(@Param("keys") Collection<String> keys);
}
