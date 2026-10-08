package com.example.chat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Push notification service — stubbed per README.
 * "Third party service is push notification which can be stubbed for Android FCM and iOS APN"
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    public void sendPushNotification(Long recipientUserId, String senderUsername, String preview) {
        log.info("[PUSH NOTIFICATION STUB] → userId={} | from='{}' | preview='{}'",
                recipientUserId, senderUsername,
                preview.length() > 100 ? preview.substring(0, 100) + "…" : preview);
    }

    public void sendGroupPushNotification(Long recipientUserId, Long groupId,
                                          String senderUsername, String preview) {
        log.info("[PUSH NOTIFICATION STUB] → userId={} | group={} | from='{}' | preview='{}'",
                recipientUserId, groupId, senderUsername,
                preview.length() > 100 ? preview.substring(0, 100) + "…" : preview);
    }
}
