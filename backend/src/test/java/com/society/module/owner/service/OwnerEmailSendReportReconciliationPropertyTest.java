package com.society.module.owner.service;

import com.society.module.owner.dto.NotEmailedEntry;
import com.society.module.owner.dto.NotEmailedReason;
import com.society.module.owner.dto.RecipientScope;
import com.society.module.owner.dto.SendOwnerEmailRequest;
import com.society.module.owner.dto.SendReportDTO;
import com.society.module.owner.entity.Owner;
import com.society.module.owner.repository.OwnerRepository;
import com.society.module.settings.entity.SocietySettings;
import com.society.module.settings.service.SocietySettingsService;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property test for {@link OwnerEmailServiceImpl#sendOwnerEmail}.
 *
 * <p><b>Feature: owner-email-rich-text, Property 11: Send report counts reconcile.</b>
 * The rich-text change (sanitize-once-per-request plus multipart/alternative send) must not
 * disturb the reconciliation of the returned {@link SendReportDTO}. For any resolved recipient
 * set with mixed outcomes &mdash; valid recipients that send successfully, valid recipients
 * whose send throws (recorded {@link NotEmailedReason#SEND_FAILURE}), and invalid/missing-email
 * recipients (recorded {@link NotEmailedReason#MISSING_EMAIL}) &mdash; the report always
 * satisfies {@code sentCount + notEmailedCount == totalAttempted}, where {@code totalAttempted}
 * equals the number of resolved recipients, and {@code notEmailed.size() == notEmailedCount}.</p>
 *
 * <p>Every valid owner is given a distinct, valid email so a failing subset is unambiguous; a
 * real {@link MimeMessage} is used so the "To" header the service writes is readable back, letting
 * the mocked {@link JavaMailSender} decide failure per recipient. Invalid owners carry a
 * null/blank/whitespace email so they are classified {@code MISSING_EMAIL} and never reach the
 * sender. The sanitizer and plain-text renderer are the real (pure) components and the request
 * body carries HTML, so the flow behaves exactly as in production.</p>
 *
 * <p><b>Validates: Requirements 5.6</b></p>
 */
class OwnerEmailSendReportReconciliationPropertyTest {

    // Feature: owner-email-rich-text, Property 11: Send report counts reconcile
    @Property(tries = 100)
    void sendReportCountsReconcileAcrossMixedOutcomes(
            @ForAll("mixedOutcomeScenarios") Scenario scenario) {

        List<Owner> recipients = scenario.recipients;
        Set<String> failingEmails = scenario.failingEmails;

        // --- Arrange collaborators (mocked JavaMailSender per instructions) ---
        JavaMailSender mailSender = mock(JavaMailSender.class);
        // Use a real MimeMessage so the service-written "To" header is readable in the stub.
        when(mailSender.createMimeMessage())
                .thenAnswer(invocation -> new MimeMessage((jakarta.mail.Session) null));

        // Only valid recipients in the failing subset throw; the rest "succeed".
        doAnswer(invocation -> {
            MimeMessage message = invocation.getArgument(0);
            String to = extractRecipient(message);
            if (failingEmails.contains(to)) {
                throw new MailSendException("simulated transport failure for " + to);
            }
            return null;
        }).when(mailSender).send(any(MimeMessage.class));

        OwnerRepository ownerRepository = mock(OwnerRepository.class);
        // The recipient set is resolved through the ALL scope active-owner query.
        when(ownerRepository.findByStatusOrderByFullNameAsc(any())).thenReturn(recipients);

        SocietySettingsService settingsService = mock(SocietySettingsService.class);
        when(settingsService.getSettings()).thenReturn(new SocietySettings());

        // Stub the pure template builder so template concerns do not affect this property.
        OwnerEmailTemplateBuilder templateBuilder = mock(OwnerEmailTemplateBuilder.class);
        when(templateBuilder.buildSubject(any())).thenReturn("Subject");
        when(templateBuilder.buildHtmlBody(any(), any(), any())).thenReturn("<p>Body</p>");

        // The sanitizer and plain-text renderer are pure components; the real ones pass the
        // plain-text body through unchanged so the send flow behaves exactly as before.
        OwnerEmailServiceImpl service =
                new OwnerEmailServiceImpl(ownerRepository, settingsService, templateBuilder,
                        new OwnerEmailSanitizer(), new OwnerEmailPlainTextRenderer());
        // mailSender is @Autowired(required=false) and fromEmail is @Value-injected.
        ReflectionTestUtils.setField(service, "mailSender", mailSender);
        ReflectionTestUtils.setField(service, "fromEmail", "noreply@society.com");

        SendOwnerEmailRequest request = new SendOwnerEmailRequest();
        request.setRecipientScope(RecipientScope.ALL);
        request.setSubject("Hello owners");
        request.setBody("<p>This is the message body</p>");

        // --- Act ---
        SendReportDTO report = service.sendOwnerEmail(request);

        // --- Assert: the two reconciliation invariants (Req 5.6) ---
        assertThat(report.getTotalAttempted())
                .as("totalAttempted equals the number of resolved recipients")
                .isEqualTo(recipients.size());

        assertThat(report.getSentCount() + report.getNotEmailedCount())
                .as("sentCount + notEmailedCount == totalAttempted")
                .isEqualTo(report.getTotalAttempted());

        assertThat(report.getNotEmailed())
                .as("notEmailed list is never null")
                .isNotNull();
        assertThat(report.getNotEmailed().size())
                .as("notEmailed.size() == notEmailedCount")
                .isEqualTo(report.getNotEmailedCount());

        // --- Corroborate the mixed outcomes actually occurred as generated ---
        int expectedMissing = (int) recipients.stream()
                .filter(o -> o.getEmail() == null || o.getEmail().trim().isEmpty())
                .count();
        int expectedSendFailures = failingEmails.size();
        int expectedNotEmailed = expectedMissing + expectedSendFailures;
        int expectedSent = recipients.size() - expectedNotEmailed;

        assertThat(report.getSentCount())
                .as("successful sends are the valid, non-failing recipients")
                .isEqualTo(expectedSent);
        assertThat(report.getNotEmailedCount())
                .as("not-emailed count is the missing-email plus send-failure recipients")
                .isEqualTo(expectedNotEmailed);

        long recordedMissing = report.getNotEmailed().stream()
                .filter(e -> e.getReason() == NotEmailedReason.MISSING_EMAIL)
                .count();
        long recordedFailures = report.getNotEmailed().stream()
                .filter(e -> e.getReason() == NotEmailedReason.SEND_FAILURE)
                .count();
        assertThat(recordedMissing)
                .as("every invalid-email recipient is recorded MISSING_EMAIL")
                .isEqualTo(expectedMissing);
        assertThat(recordedFailures)
                .as("every failing valid recipient is recorded SEND_FAILURE")
                .isEqualTo(expectedSendFailures);

        // Counts are non-negative.
        assertThat(report.getSentCount()).as("sentCount is non-negative").isGreaterThanOrEqualTo(0);
        assertThat(report.getNotEmailedCount()).as("notEmailedCount is non-negative")
                .isGreaterThanOrEqualTo(0);
    }

    /** Reads back the single "To" recipient the service set on the outgoing message. */
    private static String extractRecipient(MimeMessage message) throws Exception {
        Address[] addresses = message.getRecipients(Message.RecipientType.TO);
        assertThat(addresses).as("outgoing message has exactly one recipient").hasSize(1);
        return ((InternetAddress) addresses[0]).getAddress();
    }

    /** A generated recipient set plus the subset of valid emails whose send should throw. */
    private static final class Scenario {
        final List<Owner> recipients;
        final Set<String> failingEmails;

        Scenario(List<Owner> recipients, Set<String> failingEmails) {
            this.recipients = recipients;
            this.failingEmails = failingEmails;
        }
    }

    /**
     * Generates 0..25 owners each assigned one of three outcomes &mdash; valid+success,
     * valid+send-failure, or invalid (missing email) &mdash; then materialises the failing
     * subset. Distinct emails on valid owners make the failing subset unambiguous and let the
     * stub decide failure per recipient. Across the 100 tries the mix ranges over all-success,
     * all-failure, all-missing, empty, and every blend in between.
     */
    @Provide
    Arbitrary<Scenario> mixedOutcomeScenarios() {
        Arbitrary<Integer> sizes = Arbitraries.integers().between(0, 25);
        return sizes.flatMap(size -> {
            // Per-owner outcome: 0 = valid+success, 1 = valid+send-failure, 2 = invalid email.
            Arbitrary<List<Integer>> outcomes =
                    Arbitraries.integers().between(0, 2).list().ofSize(size);
            return outcomes.map(codes -> {
                List<Owner> recipients = new ArrayList<>(size);
                Set<String> failingEmails = new HashSet<>();
                // Rotate over the invalid-email variants for variety.
                String[] invalidEmails = {null, "", "   "};
                int invalidIdx = 0;
                for (int i = 0; i < size; i++) {
                    int code = codes.get(i);
                    Owner.OwnerBuilder builder = Owner.builder()
                            .ownerId((long) (i + 1))
                            .fullName("Owner " + i);
                    if (code == 2) {
                        // Invalid/missing email -> MISSING_EMAIL, never reaches the sender.
                        builder.email(invalidEmails[invalidIdx % invalidEmails.length]);
                        invalidIdx++;
                    } else {
                        // Distinct, valid email.
                        String email = "owner" + i + "@example.com";
                        builder.email(email);
                        if (code == 1) {
                            // Valid but its send throws -> SEND_FAILURE.
                            failingEmails.add(email);
                        }
                    }
                    recipients.add(builder.build());
                }
                return new Scenario(recipients, failingEmails);
            });
        });
    }
}
