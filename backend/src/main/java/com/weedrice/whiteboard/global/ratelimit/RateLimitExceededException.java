package com.weedrice.whiteboard.global.ratelimit;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.Getter;

@Getter
public class RateLimitExceededException extends BusinessException {

    private final RateLimitSnapshot snapshot;

    public RateLimitExceededException(RateLimitSnapshot snapshot) {
        super(ErrorCode.RATE_LIMIT_EXCEEDED);
        this.snapshot = snapshot;
    }
}
