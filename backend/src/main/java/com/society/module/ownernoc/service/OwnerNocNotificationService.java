package com.society.module.ownernoc.service;

import com.society.module.owner.entity.Owner;
import com.society.module.ownernoc.entity.OwnerNocRequest;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Emails owner NOC outcomes: the generated certificate PDF on approval, and a
 * reason notice on rejection. Best-effort: failures are logged, never thrown, so
 * a mail outage does not roll back the approval/rejection transaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OwnerNocNotificationService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@society.com}")
    private String fromEmail;

    private final OwnerNocPdfService ownerNocPdfService;

    /** Email the approved certificate PDF to the owner. Returns true if sent. */
    public boolean sendApproved(OwnerNocRequest request) {
        Owner owner = request.getOwner();
        String ownerEmail = owner != null ? owner.getEmail() : null;
        String typeName = request.getNocType() != null ? request.getNocType().getName() : "NOC";

        if (ownerEmail == null || ownerEmail.isBlank()) {
            log.warn("No owner email; approved NOC certificate not emailed for request {}.", request.getRequestId());
            return false;
        }
        if (mailSender == null) {
            log.warn("Mail sender not configured. NOC certificate for request {} not emailed.", request.getRequestId());
            return false;
        }

        try {
            byte[] pdf = ownerNocPdfService.generate(request);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromEmail);
            helper.setTo(ownerEmail);
            helper.setSubject("No Objection Certificate - " + typeName);
            helper.setText(
                    "Dear " + (owner.getFullName() != null ? owner.getFullName() : "Owner") + ",\n\n" +
                    "Your request for a No Objection Certificate (" + typeName + ") has been approved.\n\n" +
                    "Please find the certificate attached.\n\n" +
                    "Regards,\nSociety Management");
            helper.addAttachment("NOC-" + typeName.replaceAll("\\s+", "-") + ".pdf", new ByteArrayResource(pdf));

            mailSender.send(message);
            log.info("Owner NOC certificate emailed to {} (request {}).", ownerEmail, request.getRequestId());
            return true;
        } catch (Exception e) {
            log.error("Failed to email NOC certificate for request {}: {}", request.getRequestId(), e.getMessage());
            return false;
        }
    }

    /** Email the owner that their NOC request was rejected, with the reason. */
    public boolean sendRejected(OwnerNocRequest request) {
        Owner owner = request.getOwner();
        String ownerEmail = owner != null ? owner.getEmail() : null;
        String typeName = request.getNocType() != null ? request.getNocType().getName() : "NOC";

        if (ownerEmail == null || ownerEmail.isBlank() || mailSender == null) {
            log.warn("NOC rejection notice not emailed for request {} (no email or mail not configured).",
                    request.getRequestId());
            return false;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(ownerEmail);
            message.setSubject("No Objection Certificate Request Rejected - " + typeName);
            message.setText(
                    "Dear " + (owner.getFullName() != null ? owner.getFullName() : "Owner") + ",\n\n" +
                    "Your request for a No Objection Certificate (" + typeName + ") has been rejected.\n\n" +
                    "Reason: " + (request.getRejectionReason() != null ? request.getRejectionReason() : "Not specified") +
                    "\n\nPlease contact the society office for details.\n\n" +
                    "Regards,\nSociety Management");
            mailSender.send(message);
            log.info("Owner NOC rejection notice emailed to {} (request {}).", ownerEmail, request.getRequestId());
            return true;
        } catch (Exception e) {
            log.error("Failed to email NOC rejection for request {}: {}", request.getRequestId(), e.getMessage());
            return false;
        }
    }
}
