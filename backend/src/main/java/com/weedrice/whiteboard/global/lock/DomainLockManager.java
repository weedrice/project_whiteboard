package com.weedrice.whiteboard.global.lock;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DomainLockManager {

    public static final String BOARD_ORDER = "BOARD_ORDER";
    public static final String POST_DRAFT_CLEANUP = "POST_DRAFT_CLEANUP";

    private final DomainLockRepository domainLockRepository;

    public void lockBoardOrder() {
        domainLockRepository.findByNameForUpdate(BOARD_ORDER)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    public void lockPostDraftCleanup() {
        domainLockRepository.findByNameForUpdate(POST_DRAFT_CLEANUP)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
    }
}
