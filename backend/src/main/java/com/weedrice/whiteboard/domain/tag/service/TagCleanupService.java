package com.weedrice.whiteboard.domain.tag.service;

import com.weedrice.whiteboard.domain.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TagCleanupService {

    private static final long ORPHAN_TAG_TTL_HOURS = 24L;

    private final TagRepository tagRepository;
    private final Clock clock;

    @Transactional
    public int cleanupOrphanTags() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusHours(ORPHAN_TAG_TTL_HOURS);
        return tagRepository.deleteOrphanTagsCreatedBefore(cutoff);
    }
}
