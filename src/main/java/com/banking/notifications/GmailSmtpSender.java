package com.banking.notifications;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;

/** Port 587 with mandatory STARTTLS. Never use ordinary Gmail login passwords in source. */
public final class GmailSmtpSender implements EmailSender {
    private final GmailConfig config;
    public GmailSmtpSender(GmailConfig config) { this.config = java.util.Objects.requireNonNull(config); }

    @Override public void send(String recipient, EmailTemplate.Message content) throws Exception {
        InternetAddress to = new InternetAddress(recipient, true);
        to.validate();
        Properties props = new Properties();
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "15000");
        props.put("mail.smtp.writetimeout", "15000");
        Session session = Session.getInstance(props, new Authenticator() {
            @Override protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(config.username(), config.appPassword());
            }
        });
        session.setDebug(false);
        MimeMessage mail = new MimeMessage(session);
        mail.setFrom(new InternetAddress(config.username()));
        mail.setRecipient(Message.RecipientType.TO, to);
        mail.setSubject(content.subject(), "UTF-8");
        mail.setText(content.body(), "UTF-8");
        Transport.send(mail);
    }
}
