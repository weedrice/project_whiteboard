package com.weedrice.whiteboard.domain.message.service;

import com.weedrice.whiteboard.domain.message.entity.MessageQueue;
import com.weedrice.whiteboard.domain.message.repository.MessageQueueRepository;
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
class MessageServiceTest {

    @Mock
    private MessageQueueRepository messageQueueRepository;

    @InjectMocks
    private MessageService messageService;

    @Test
    @DisplayName("이메일 큐 저장 성공")
    void queueEmail_success() {
        // given
        User user = User.builder().email("test@test.com").build();
        String content = "Test Email Content";

        // when
        messageService.queueEmail(user, content);

        // then
        verify(messageQueueRepository).save(any(MessageQueue.class));
    }
}
