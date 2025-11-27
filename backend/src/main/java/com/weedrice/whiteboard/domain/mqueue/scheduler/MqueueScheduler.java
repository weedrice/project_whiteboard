package com.weedrice.whiteboard.domain.mqueue.scheduler;

import com.weedrice.whiteboard.domain.mqueue.entity.MessageQueue;
import com.weedrice.whiteboard.domain.mqueue.repository.MessageQueueRepository;
import com.weedrice.whiteboard.domain.mqueue.service.MqueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MqueueScheduler {

    private final MessageQueueRepository messageQueueRepository;
    private final MqueueService mqueueService;

    // 1분마다 실행
    @Scheduled(cron = "0 * * * * ?")
    public void processMessageQueue() {
        log.info("메시지 큐 처리 스케줄러 시작");
        List<MessageQueue> pendingMessages = messageQueueRepository.findByStatusAndRetryCountLessThan("PENDING", 5);
        for (MessageQueue message : pendingMessages) {
            if ("EMAIL".equals(message.getDeliveryMethod())) {
                mqueueService.sendEmail(message);
            }
            // TODO: PUSH, SMS 등 다른 발송 방법 처리
        }
        log.info("메시지 큐 처리 스케줄러 완료");
    }
}
