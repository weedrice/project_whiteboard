package com.weedrice.whiteboard.global.email;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SesEmailService implements EmailService {

    private final SesClient sesClient;

    @Value("${cloud.aws.ses.sender}")
    private String sender;

    @Override
    public void sendEmail(String to, String subject, String body) {
        try {
            SendEmailRequest request = SendEmailRequest.builder()
                    .destination(Destination.builder().toAddresses(to).build())
                    .message(Message.builder()
                            .subject(Content.builder().data(subject).build())
                            .body(Body.builder()
                                    .html(Content.builder().data(body).build())
                                    .build())
                            .build())
                    .source(sender)
                    .build();

            sesClient.sendEmail(request);
            log.info("Email sent to: {}", to);
        } catch (SesException e) {
            log.error("Failed to send email to: {}", to, e);
            // TODO: ErrorCode.EMAIL_SEND_FAILED 추가 필요
            throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED, "Email send failed: " + e.getMessage());
        }
    }
}
