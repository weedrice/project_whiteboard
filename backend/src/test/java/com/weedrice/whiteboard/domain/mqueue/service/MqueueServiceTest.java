package com.weedrice.whiteboard.domain.mqueue.service;

import com.weedrice.whiteboard.domain.mqueue.entity.MessageQueue;
import com.weedrice.whiteboard.domain.mqueue.repository.MessageQueueRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.email.EmailService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MqueueServiceTest {

    @Mock
    private MessageQueueRepository messageQueueRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private MqueueService mqueueService;

    @Test
    @DisplayName("메일 큐에 추가 성공")
    void queueEmail_success() {
        User user = User.builder().build();
        String content = "Test Email";

        mqueueService.queueEmail(user, content);

        verify(messageQueueRepository).save(any(MessageQueue.class));
    }

    @Test
    @DisplayName("메일 전송 성공 시 SENT 처리")
    void sendEmail_success() {
        User user = User.builder().email("user@test.com").build();
        MessageQueue message = MessageQueue.builder()
                .targetUser(user)
                .deliveryMethod("EMAIL")
                .content("<p>Hello</p>")
                .build();
        ReflectionTestUtils.setField(message, "status", "PROCESSING");
        when(messageQueueRepository.findById(1L)).thenReturn(Optional.of(message));
        doAnswer(invocation -> {
            Consumer<Object> consumer = invocation.getArgument(0);
            consumer.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        mqueueService.sendEmail(1L);

        verify(emailService).sendEmail("user@test.com", "[noviIs] Notification", "<p>Hello</p>");
        verify(messageQueueRepository).save(message);
        assertThat(message.getStatus()).isEqualTo("SENT");
    }

    @Test
    @DisplayName("메일 전송 실패 시 재시도 상태로 되돌린다")
    void sendEmail_failure() {
        User user = User.builder().email("user@test.com").build();
        MessageQueue message = MessageQueue.builder()
                .targetUser(user)
                .deliveryMethod("EMAIL")
                .content("<p>Hello</p>")
                .build();
        ReflectionTestUtils.setField(message, "status", "PROCESSING");
        when(messageQueueRepository.findById(1L)).thenReturn(Optional.of(message));
        doThrow(new BusinessException(ErrorCode.EMAIL_SEND_FAILED))
                .when(emailService).sendEmail(eq("user@test.com"), eq("[noviIs] Notification"), eq("<p>Hello</p>"));
        doAnswer(invocation -> {
            Consumer<Object> consumer = invocation.getArgument(0);
            consumer.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        mqueueService.sendEmail(1L);

        verify(messageQueueRepository).save(message);
        assertThat(message.getStatus()).isEqualTo("PENDING");
        assertThat(message.getRetryCount()).isEqualTo(1);
    }
}
