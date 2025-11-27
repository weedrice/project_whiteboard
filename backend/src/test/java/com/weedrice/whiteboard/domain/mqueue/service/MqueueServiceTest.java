package com.weedrice.whiteboard.domain.mqueue.service;

import com.weedrice.whiteboard.domain.mqueue.entity.MessageQueue;
import com.weedrice.whiteboard.domain.mqueue.repository.MessageQueueRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MqueueServiceTest {

    @Mock
    private MessageQueueRepository messageQueueRepository;

    @InjectMocks
    private MqueueService mqueueService;

    @Test
    @DisplayName("이메일 큐에 추가 성공")
    void queueEmail_success() {
        // given
        User user = User.builder().build();
        String content = "Test Email";

        // when
        mqueueService.queueEmail(user, content);

        // then
        verify(messageQueueRepository).save(any(MessageQueue.class));
    }
}
