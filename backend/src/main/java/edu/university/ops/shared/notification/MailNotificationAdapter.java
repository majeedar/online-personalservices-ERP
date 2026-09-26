package edu.university.ops.shared.notification;

import edu.university.ops.shared.configuration.OpsProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Sends notifications as plain-text mail; MailHog receives them in the prototype. */
@Component
@ConditionalOnProperty(name = "ops.mail.enabled", havingValue = "true")
class MailNotificationAdapter implements NotificationGateway {

    private final JavaMailSender mailSender;
    private final OpsProperties properties;

    MailNotificationAdapter(JavaMailSender mailSender, OpsProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(NotificationMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(properties.mail().from());
        mail.setTo(message.recipientEmail());
        mail.setSubject("[Online Personalservices] " + message.subject());
        mail.setText("Hello " + message.recipientName() + ",\n\n" + message.body()
                + "\n\nPlease sign in to Online Personalservices for details.\n");
        mailSender.send(mail);
    }
}
