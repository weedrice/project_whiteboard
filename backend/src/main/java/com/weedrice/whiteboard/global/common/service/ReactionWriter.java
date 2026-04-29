package com.weedrice.whiteboard.global.common.service;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class ReactionWriter {

    public void insertOrThrowDuplicate(Runnable insertAction, ErrorCode duplicateErrorCode) {
        try {
            insertAction.run();
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(duplicateErrorCode);
        }
    }
}
