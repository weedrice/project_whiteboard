package com.weedrice.whiteboard.domain.admin.repository;

import com.weedrice.whiteboard.domain.admin.entity.IpBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface IpBlockRepository extends JpaRepository<IpBlock, String> {
    Optional<IpBlock> findByIpAddressAndEndDateAfterOrEndDateIsNull(String ipAddress, LocalDateTime now);
    List<IpBlock> findByEndDateAfterOrEndDateIsNull(LocalDateTime now);
}
