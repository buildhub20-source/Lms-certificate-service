package com.lms.certificate.mail;

import com.lms.certificate.certificate.entity.Certificate;
import com.lms.certificate.client.LmsInternalClient;
import com.lms.certificate.config.MailConfig;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;

/**
 * Sends certificate-related emails to students.
 * All sends are {@link Async} (fire-and-forget).
 */
@Service
public class CertificateMailService {

    private static final Logger log = LoggerFactory.getLogger(CertificateMailService.class);

    private final JavaMailSender mailSender;
    private final MailConfig mailConfig;
    private final LmsInternalClient lmsClient;

    public CertificateMailService(JavaMailSender mailSender,
                                  MailConfig mailConfig,
                                  LmsInternalClient lmsClient) {
        this.mailSender = mailSender;
        this.mailConfig = mailConfig;
        this.lmsClient = lmsClient;
    }

    /**
     * Sends a certificate-issued notification with the PDF attached.
     *
     * @param cert     the newly issued certificate
     * @param pdfBytes the rendered PDF bytes to attach
     */
    @Async
    public void sendCertificateIssued(Certificate cert, byte[] pdfBytes, String tenantSlug) {
        if (!mailConfig.isEnabled()) {
            log.debug("Mail disabled — skipping certificate issued email for {}", cert.getSerialNumber());
            return;
        }
        try {
            LmsInternalClient.UserDto   user   = lmsClient.getUser(cert.getStudentId(), tenantSlug);
            LmsInternalClient.CourseDto course = lmsClient.getCourse(cert.getCourseId(), tenantSlug);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailConfig.getFrom(), mailConfig.getFromName());
            helper.setTo(user.email());
            helper.setSubject("Your Certificate — " + course.title());

            String body = """
                    <html><body style="font-family:sans-serif;color:#1e293b">
                    <h2 style="color:#3B82F6">Congratulations, %s! 🎉</h2>
                    <p>You have successfully completed <strong>%s</strong>.</p>
                    <p>Your certificate of completion is attached to this email.</p>
                    <p style="color:#64748b;font-size:12px">Certificate ID: %s</p>
                    </body></html>
                    """.formatted(user.name(), course.title(), cert.getSerialNumber());

            helper.setText(body, true);
            byte[] pdf = pdfBytes;
            helper.addAttachment(cert.getSerialNumber() + ".pdf",
                    () -> new ByteArrayInputStream(pdf), "application/pdf");

            mailSender.send(message);
            log.info("Certificate email sent to {} for {}", user.email(), cert.getSerialNumber());

        } catch (Exception ex) {
            log.warn("Failed to send certificate email for {}: {}", cert.getSerialNumber(), ex.getMessage());
        }
    }
}
