package com.weedrice.whiteboard.global.email;

public interface EmailService {
    void sendEmail(String to, String subject, String body);
}
