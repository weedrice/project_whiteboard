package com.weedrice.whiteboard.domain.mqueue.scheduler;

import com.weedrice.whiteboard.domain.mqueue.MessageQueuePolicy;
import com.weedrice.whiteboard.domain.mqueue.entity.MessageQueue;
import com.weedrice.whiteboard.domain.mqueue.repository.MessageQueueRepository;
import com.weedrice.whiteboard.domain.mqueue.service.MqueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
        LocalDateTime now = LocalDateTime.now();
        int recovered = messageQueueRepository.recoverStaleProcessingMessages(
                now.minusMinutes(MessageQueuePolicy.PROCESSING_LEASE_MINUTES),
                MessageQueuePolicy.MAX_RETRY_COUNT,
                now);
        if (recovered > 0) {
            log.warn("Recovered {} stale processing message(s)", recovered);
        }
        PageRequest pendingPageRequest = PageRequest.of(
                0,
                50,
                Sort.by(Sort.Order.asc("requestedAt"), Sort.Order.asc("queueId")));
        List<MessageQueue> pendingMessages = messageQueueRepository.findByStatusAndRetryCountLessThanAndDeliveryMethod(
                "PENDING", MessageQueuePolicy.MAX_RETRY_COUNT, "EMAIL", pendingPageRequest);

        for (MessageQueue message : pendingMessages) {
            LocalDateTime claimedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
            int claimed = messageQueueRepository.claimForProcessing(
                    message.getQueueId(),
                    MessageQueuePolicy.MAX_RETRY_COUNT,
                    claimedAt);
            if (claimed == 1) {
                try {
                    mqueueService.sendEmail(message.getQueueId(), claimedAt);
                } catch (TaskRejectedException ex) {
                    log.error("Email dispatch rejected: queueId={}", message.getQueueId(), ex);
                    mqueueService.recoverRejectedDispatch(message.getQueueId(), claimedAt);
                }
            }
        }
        log.info("Message queue scheduler finished: attempted {}", pendingMessages.size());
    }
}
