package com.weedrice.whiteboard.global.log.service;

import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.global.common.util.ClientMetadataNormalizer;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.log.entity.Log;
import com.weedrice.whiteboard.global.log.repository.LogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

@Service
@RequiredArgsConstructor
public class LogService {
    private static final int DEFAULT_LOG_PAGE_SIZE = 20;
    private static final Sort LOG_LIST_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("logId"));

    private final LogRepository logRepository;

    @Async("taskExecutor")
    @Transactional(propagation = REQUIRES_NEW)
    public void saveLog(Log log) {
        logRepository.save(log);
    }

    @Async("taskExecutor")
    @Transactional(propagation = REQUIRES_NEW)
    public void saveLog(Long userId, String actionType, String ipAddress, String details) {
        Log log = Log.builder()
                .userId(userId)
                .actionType(actionType)
                .ipAddress(ClientMetadataNormalizer.normalizeIpAddress(ipAddress))
                .details(details)
                .build();
        logRepository.save(log);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    public Page<Log> getLogs(Pageable pageable) {
        return logRepository.findAll(applyStableSort(pageable));
    }

    private Pageable applyStableSort(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequestUtils.of(0, DEFAULT_LOG_PAGE_SIZE, LOG_LIST_SORT);
        }
        return PageRequestUtils.of(pageable.getPageNumber(), pageable.getPageSize(), LOG_LIST_SORT);
    }
}
