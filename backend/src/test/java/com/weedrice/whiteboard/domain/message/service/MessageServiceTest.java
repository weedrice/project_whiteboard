package com.weedrice.whiteboard.domain.message.service;

import com.weedrice.whiteboard.domain.message.entity.Message;
import com.weedrice.whiteboard.domain.message.repository.MessageRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserBlockService userBlockService;

    @InjectMocks
    private MessageService messageService;

    private User sender;
    private User receiver;

    @BeforeEach
    void setUp() {
        sender = User.builder().loginId("sender").build();
        receiver = User.builder().loginId("receiver").build();
    }

    @Test
    @DisplayName("쪽지 발송 성공")
    void sendMessage_success() {
        // given
        Long senderId = 1L;
        Long receiverId = 2L;
        String content = "Hello!";
        when(userRepository.findById(senderId)).thenReturn(Optional.of(sender));
        when(userRepository.findById(receiverId)).thenReturn(Optional.of(receiver));
        when(userBlockService.isBlocked(receiverId, senderId)).thenReturn(false);
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Message message = messageService.sendMessage(senderId, receiverId, content);

        // then
        assertThat(message.getContent()).isEqualTo(content);
        assertThat(message.getSender()).isEqualTo(sender);
        assertThat(message.getReceiver()).isEqualTo(receiver);
        verify(messageRepository).save(any(Message.class));
    }
}
