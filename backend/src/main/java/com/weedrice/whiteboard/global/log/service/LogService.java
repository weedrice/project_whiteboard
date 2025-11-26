package com.weedrice.whiteboard.global.log.service;

import com.weedrice.whiteboard.global.log.entity.Log;
import com.weedrice.whiteboard.global.log.repository.LogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LogService {

    private final LogRepository logRepository;

    @Transactional
    public void saveLog(Log log) {
        logRepository.save(log);
    }

    @Transactional
    public void saveLog(Long userId, String actionType, String ipAddress, String details) {
        Log log = Log.builder()
                .userId(userId)
                .actionType(actionType)
                .ipAddress(ipAddress)
                .details(details)
                .build();
        logRepository.save(log);
    }

    @Transactional(readOnly = true)
    public Page<Log> getLogs(Pageable pageable) {
        return logRepository.findAll(pageable);
    }
}
