package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.global.email.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class AuthMailDeliveryOrchestrationService {

    private final EmailService emailService;

    void send(MailDeliveryCommand command) {
        Long artifactId = command.createPendingArtifact().get();
        sendPrepared(new PreparedMailDeliveryCommand(
                command.recipientEmail(),
                command.subject(),
                command.body(),
                command.markDeliveryFailed(),
                command.promoteAfterDelivery(),
                command.recoverAfterPromotionFailure()), artifactId);
    }

    void sendPrepared(PreparedMailDeliveryCommand command, Long artifactId) {
        try {
            emailService.sendEmail(command.recipientEmail(), command.subject(), command.body());
        } catch (RuntimeException e) {
            try {
                command.markDeliveryFailed().accept(artifactId);
            } catch (RuntimeException statusUpdateException) {
                e.addSuppressed(statusUpdateException);
            }
            throw e;
        }

        try {
            command.promoteAfterDelivery().accept(artifactId);
        } catch (RuntimeException e) {
            command.recoverAfterPromotionFailure().accept(artifactId, e);
        }
    }

    record MailDeliveryCommand(
            String recipientEmail,
            String subject,
            String body,
            Supplier<Long> createPendingArtifact,
            Consumer<Long> markDeliveryFailed,
            Consumer<Long> promoteAfterDelivery,
            BiConsumer<Long, RuntimeException> recoverAfterPromotionFailure) {
    }

    record PreparedMailDeliveryCommand(
            String recipientEmail,
            String subject,
            String body,
            Consumer<Long> markDeliveryFailed,
            Consumer<Long> promoteAfterDelivery,
            BiConsumer<Long, RuntimeException> recoverAfterPromotionFailure) {
    }
}
