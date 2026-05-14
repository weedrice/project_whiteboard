package com.weedrice.whiteboard.domain.admin.repository;

import com.weedrice.whiteboard.domain.admin.entity.IpBlock;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface IpBlockRepository extends JpaRepository<IpBlock, String> {
    Optional<IpBlock> findByIpAddress(String ipAddress);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ipBlock from IpBlock ipBlock where ipBlock.ipAddress = :ipAddress")
    Optional<IpBlock> findByIdForUpdate(@Param("ipAddress") String ipAddress);

    @Query("""
            select ipBlock
            from IpBlock ipBlock
            where ipBlock.ipAddress = :ipAddress
              and (ipBlock.endDate > :now or ipBlock.endDate is null)
            """)
    Optional<IpBlock> findActiveByIpAddress(@Param("ipAddress") String ipAddress, @Param("now") LocalDateTime now);

    @EntityGraph(attributePaths = {"admin", "admin.user", "processorUser"})
    @Query(value = """
            select ipBlock
            from IpBlock ipBlock
            where ipBlock.endDate > :now or ipBlock.endDate is null
            """, countQuery = """
            select count(ipBlock)
            from IpBlock ipBlock
            where ipBlock.endDate > :now or ipBlock.endDate is null
            """)
    Page<IpBlock> findActiveBlocks(@Param("now") LocalDateTime now, Pageable pageable);
}
