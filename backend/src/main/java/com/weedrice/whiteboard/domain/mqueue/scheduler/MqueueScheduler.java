package com.weedrice.whiteboard.domain.mqueue.scheduler;

import com.weedrice.whiteboard.domain.mqueue.entity.MessageQueue;
import com.weedrice.whiteboard.domain.mqueue.repository.MessageQueueRepository;
import com.weedrice.whiteboard.domain.mqueue.service.MqueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MqueueScheduler {
    private static final int MAX_RETRY_COUNT = 5;

    private final MessageQueueRepository messageQueueRepository;
    private final MqueueService mqueueService;

    @Scheduled(cron = "0 * * * * ?")
    public void processMessageQueue() {
        log.info("Message queue scheduler started");
        List<MessageQueue> pendingMessages = messageQueueRepository.findByStatusAndRetryCountLessThan(
                "PENDING", MAX_RETRY_COUNT, PageRequest.of(0, 50));

        for (MessageQueue message : pendingMessages) {
            if (!"EMAIL".equals(message.getDeliveryMethod())) {
                continue;
            }

            int claimed = messageQueueRepository.claimForProcessing(message.getQueueId(), MAX_RETRY_COUNT);
            if (claimed == 1) {
                mqueueService.sendEmail(message.getQueueId());
            }
        }
        log.info("Message queue scheduler finished: attempted {}", pendingMessages.size());
    }
}
