package com.weedrice.whiteboard.domain.file.scheduler;

import com.weedrice.whiteboard.domain.file.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileCleanupScheduler {

    private final FileService fileService;

    // 매일 새벽 2시에 실행
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupTemporaryFiles() {
        log.info("임시 파일 정리 스케줄러 시작");
        fileService.cleanUpTemporaryFiles();
        log.info("임시 파일 정리 스케줄러 완료");
    }
}
