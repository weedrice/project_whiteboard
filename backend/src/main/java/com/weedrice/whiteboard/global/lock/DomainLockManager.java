package com.weedrice.whiteboard.global.lock;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DomainLockManager {

    public static final String BOARD_ORDER = "BOARD_ORDER";

    private final DomainLockRepository domainLockRepository;

    public void lockBoardOrder() {
        domainLockRepository.findByNameForUpdate(BOARD_ORDER)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
    }
}
