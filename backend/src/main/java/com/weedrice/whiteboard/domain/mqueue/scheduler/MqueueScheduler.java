package com.weedrice.whiteboard.domain.mqueue.scheduler;

import com.weedrice.whiteboard.domain.mqueue.MessageQueuePolicy;
import com.weedrice.whiteboard.domain.mqueue.repository.MessageQueueRepository;
import com.weedrice.whiteboard.domain.mqueue.repository.MessageQueueRepository.EmailDispatchProjection;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        List<Long> pendingQueueIds = messageQueueRepository.findPendingQueueIdsByStatusAndRetryCountLessThanAndDeliveryMethod(
                "PENDING", MessageQueuePolicy.MAX_RETRY_COUNT, "EMAIL", pendingPageRequest);

        Map<Long, LocalDateTime> claimedQueueIds = new LinkedHashMap<>();
        for (Long queueId : pendingQueueIds) {
            LocalDateTime claimedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
            int claimed = messageQueueRepository.claimForProcessing(
                    queueId,
                    MessageQueuePolicy.MAX_RETRY_COUNT,
                    claimedAt);
            if (claimed == 1) {
                claimedQueueIds.put(queueId, claimedAt);
            }
        }

        if (!claimedQueueIds.isEmpty()) {
            Map<Long, EmailDispatchProjection> dispatchesByQueueId = messageQueueRepository
                    .findEmailDispatchesByQueueIdIn(claimedQueueIds.keySet().stream().toList())
                    .stream()
                    .collect(Collectors.toMap(EmailDispatchProjection::getQueueId, Function.identity()));
            for (Map.Entry<Long, LocalDateTime> claimedQueue : claimedQueueIds.entrySet()) {
                Long queueId = claimedQueue.getKey();
                LocalDateTime claimedAt = claimedQueue.getValue();
                EmailDispatchProjection dispatch = dispatchesByQueueId.get(queueId);
                if (dispatch == null) {
                    log.warn("Recovered email dispatch without projection: queueId={}", queueId);
                    mqueueService.recoverRejectedDispatch(queueId, claimedAt);
                    continue;
                }
                dispatchEmail(dispatch, claimedAt);
            }
        }
        log.info("Message queue scheduler finished: attempted {}", pendingQueueIds.size());
    }

    private void dispatchEmail(EmailDispatchProjection dispatch, LocalDateTime claimedAt) {
        Long queueId = dispatch.getQueueId();
        try {
            mqueueService.sendEmail(dispatch, claimedAt);
        } catch (TaskRejectedException ex) {
            log.error("Email dispatch rejected: queueId={}", queueId, ex);
            mqueueService.recoverRejectedDispatch(queueId, claimedAt);
        }
    }
}
