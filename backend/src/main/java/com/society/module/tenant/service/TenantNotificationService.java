package com.society.module.tenant.service;

import com.society.module.owner.entity.Owner;
import com.society.module.tenant.entity.Tenant;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Sends tenant-related notifications. Currently: emails the No Objection Certificate
 * PDF to the flat owner (CC the tenant when a tenant email is available) upon approval.
 *
 * Mail is best-effort: failures are logged and never propagated, so an email outage
 * does not roll back the approval transaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantNotificationService {

    // Nullable: when mail is not configured the bean is absent and we degrade gracefully.
    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@society.com}")
    private String fromEmail;

    private final NocCertificatePdfService nocCertificatePdfService;

    /**
     * Generate the NOC certificate and email it to the owner (CC tenant if email present).
     *
     * @return true if the email was sent, false if it was skipped or failed.
     */
    public boolean sendNocCertificate(Tenant tenant) {
        Owner owner = tenant.getUnit().getPrimaryOwner();
        String ownerEmail = owner != null ? owner.getEmail() : null;

        if (ownerEmail == null || ownerEmail.isBlank()) {
            log.warn("No owner email for unit {}; NOC certificate email skipped for tenant {}.",
                    tenant.getUnit().getUnitNumber(), tenant.getTenantId());
            return false;
        }

        if (mailSender == null) {
            log.warn("Mail sender not configured. NOC certificate for tenant {} (unit {}) not emailed.",
                    tenant.getTenantId(), tenant.getUnit().getUnitNumber());
            return false;
        }

        try {
            byte[] pdf = nocCertificatePdfService.generateNocCertificate(tenant);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true); // multipart = true
            helper.setFrom(fromEmail);
            helper.setTo(ownerEmail);

            String tenantEmail = tenant.getEmail();
            if (tenantEmail != null && !tenantEmail.isBlank() && !tenantEmail.equalsIgnoreCase(ownerEmail)) {
                helper.setCc(tenantEmail);
            }

            String ownerName = owner != null && owner.getFullName() != null ? owner.getFullName() : "Owner";
            helper.setSubject("No Objection Certificate - Unit " + tenant.getUnit().getUnitNumber());
            helper.setText(
                    "Dear " + ownerName + ",\n\n" +
                    "The tenant registration for " + tenant.getTenantName() +
                    " (Unit " + tenant.getUnit().getUnitNumber() + ") has been approved by the society.\n\n" +
                    "Please find attached the No Objection Certificate permitting the tenant to shift into the society.\n\n" +
                    "Regards,\n" +
                    "Society Management");

            helper.addAttachment(
                    "NOC-Unit-" + tenant.getUnit().getUnitNumber() + ".pdf",
                    new ByteArrayResource(pdf));

            mailSender.send(message);
            log.info("NOC certificate emailed to owner {} (tenant {}, unit {}).",
                    ownerEmail, tenant.getTenantId(), tenant.getUnit().getUnitNumber());
            return true;

        } catch (Exception e) {
            log.error("Failed to email NOC certificate for tenant {} (unit {}): {}",
                    tenant.getTenantId(), tenant.getUnit().getUnitNumber(), e.getMessage());
            return false;
        }
    }
}
