package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.entity.UserKeywordSubscription;
import com.weedrice.whiteboard.domain.notification.repository.UserKeywordSubscriptionRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeywordSubscriptionServiceTest {

    @Mock UserKeywordSubscriptionRepository repository;
    @Mock UserWritableResolver userResolver;
    KeywordSubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new KeywordSubscriptionService(repository, userResolver);
    }

    @Test
    void getAndExistingSubscribeAreIdempotent() {
        UserKeywordSubscription subscription = subscription("spring");
        when(repository.findByUser_UserIdOrderByCreatedAtDescSubscriptionIdDesc(1L)).thenReturn(List.of(subscription));
        when(repository.findByUser_UserIdAndKeyword(1L, "spring")).thenReturn(Optional.of(subscription));

        assertEquals(1, service.getSubscriptions(1L).size());
        assertEquals("spring", service.subscribe(1L, "  SPRING ").getKeyword());

        verify(repository, never()).save(any());
    }

    @Test
    void createsAndRemovesNormalizedKeyword() {
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(2L);
        when(userResolver.resolve(2L)).thenReturn(user);
        when(repository.findByUser_UserIdAndKeyword(2L, "java")).thenReturn(Optional.empty());
        when(repository.countByUser_UserId(2L)).thenReturn(9L);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals("java", service.subscribe(2L, " Java ").getKeyword());

        UserKeywordSubscription saved = subscription("java");
        when(repository.findByUser_UserIdAndKeyword(2L, "java")).thenReturn(Optional.of(saved));
        service.unsubscribe(2L, "JAVA");
        verify(repository).delete(saved);
    }

    @Test
    void rejectsInvalidOrOverLimitKeywords() {
        assertThrows(BusinessException.class, () -> service.subscribe(3L, null));
        assertThrows(BusinessException.class, () -> service.subscribe(3L, " "));
        assertThrows(BusinessException.class, () -> service.subscribe(3L, "x".repeat(51)));

        User user = mock(User.class);
        when(user.getUserId()).thenReturn(3L);
        when(userResolver.resolve(3L)).thenReturn(user);
        when(repository.findByUser_UserIdAndKeyword(3L, "full")).thenReturn(Optional.empty());
        when(repository.countByUser_UserId(3L)).thenReturn(10L);
        assertThrows(BusinessException.class, () -> service.subscribe(3L, "full"));
    }

    private static UserKeywordSubscription subscription(String keyword) {
        return UserKeywordSubscription.builder().user(mock(User.class)).keyword(keyword).build();
    }
}
