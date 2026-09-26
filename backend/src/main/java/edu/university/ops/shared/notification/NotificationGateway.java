package edu.university.ops.shared.notification;

/**
 * Outbound delivery channel (AGENT.md §19). The prototype has a mail adapter
 * (MailHog); the in-app channel is the notification table itself.
 */
public interface NotificationGateway {

    void send(NotificationMessage message);

    record NotificationMessage(String recipientEmail, String recipientName, String subject, String body) {
    }
}
