package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.feed.repository.UserFeedRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserFeedCleanupServiceTest {

    @Mock
    private UserFeedRepository userFeedRepository;

    @InjectMocks
    private UserFeedCleanupService userFeedCleanupService;

    @Test
    void cleanupHardStalePostFeeds_deletesPostFeedsOnly() {
        when(userFeedRepository.deleteHardStalePostFeeds("POST")).thenReturn(3);

        int deletedCount = userFeedCleanupService.cleanupHardStalePostFeeds();

        assertThat(deletedCount).isEqualTo(3);
        verify(userFeedRepository).deleteHardStalePostFeeds("POST");
    }
}
