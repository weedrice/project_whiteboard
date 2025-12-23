package com.weedrice.whiteboard.domain.mqueue.service;

import com.weedrice.whiteboard.domain.mqueue.entity.MessageQueue;
import com.weedrice.whiteboard.domain.mqueue.repository.MessageQueueRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MqueueService {

    private final MessageQueueRepository messageQueueRepository;

    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @Transactional
    public void queueEmail(User user, String content) {
        MessageQueue message = MessageQueue.builder()
                .targetUser(user)
                .deliveryMethod("EMAIL")
                .content(content)
                .build();
        messageQueueRepository.save(message);
    }

    @Async("taskExecutor") // Use the defined executor
    public void sendEmail(MessageQueue message) {
        try {
            // TODO: 실제 이메일 발송 로직 구현 (e.g., JavaMailSender)
            log.info("이메일 발송 시도: " + message.getTargetUser().getEmail() + " - " + message.getContent());
            Thread.sleep(1000); // 발송에 시간이 걸리는 것을 시뮬레이션
            message.sent();
            log.info("이메일 발송 성공");
        } catch (Exception e) {
            log.error("이메일 발송 실패: " + e.getMessage());
            message.failed();
        }
        
        transactionTemplate.executeWithoutResult(status -> {
            messageQueueRepository.save(message);
        });
    }
}
