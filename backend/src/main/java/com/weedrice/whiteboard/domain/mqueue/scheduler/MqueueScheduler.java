package com.weedrice.whiteboard.domain.mqueue.scheduler;

import com.weedrice.whiteboard.domain.mqueue.MessageQueuePolicy;
import com.weedrice.whiteboard.domain.mqueue.entity.MessageQueue;
import com.weedrice.whiteboard.domain.mqueue.repository.MessageQueueRepository;
import com.weedrice.whiteboard.domain.mqueue.service.MqueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MqueueScheduler {
    private final MessageQueueRepository messageQueueRepository;
    private final MqueueService mqueueService;

    @Scheduled(cron = "0 * * * * ?")
    public void processMessageQueue() {
        log.info("Message queue scheduler started");
        int recovered = messageQueueRepository.recoverStaleProcessingMessages(
                LocalDateTime.now().minusMinutes(MessageQueuePolicy.PROCESSING_LEASE_MINUTES),
                MessageQueuePolicy.MAX_RETRY_COUNT);
        if (recovered > 0) {
            log.warn("Recovered {} stale processing message(s)", recovered);
        }
        List<MessageQueue> pendingMessages = messageQueueRepository.findByStatusAndRetryCountLessThan(
                "PENDING", MessageQueuePolicy.MAX_RETRY_COUNT, PageRequest.of(0, 50));

        for (MessageQueue message : pendingMessages) {
            if (!"EMAIL".equals(message.getDeliveryMethod())) {
                continue;
            }

            int claimed = messageQueueRepository.claimForProcessing(
                    message.getQueueId(),
                    MessageQueuePolicy.MAX_RETRY_COUNT,
                    LocalDateTime.now());
            if (claimed == 1) {
                try {
                    mqueueService.sendEmail(message.getQueueId());
                } catch (TaskRejectedException ex) {
                    log.error("Email dispatch rejected: queueId={}", message.getQueueId(), ex);
                    mqueueService.recoverRejectedDispatch(message.getQueueId());
                }
            }
        }
        log.info("Message queue scheduler finished: attempted {}", pendingMessages.size());
    }
}
