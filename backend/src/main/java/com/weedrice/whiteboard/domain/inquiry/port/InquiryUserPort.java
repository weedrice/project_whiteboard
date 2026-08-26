package com.weedrice.whiteboard.domain.inquiry.port;

import java.util.Collection;
import java.util.Map;

public interface InquiryUserPort {
    Long lockActiveUserId(Long userId);

    Long lockUserId(Long userId);

    Map<Long, String> findDisplayNames(Collection<Long> userIds);

    boolean isUsableSuperAdmin(Long userId);

}
