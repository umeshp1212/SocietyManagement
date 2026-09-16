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
 * <p><b>Feature: owner-email, Property 10: A per-recipient send failure does not halt
 * processing of the others.</b> For any recipient set where an arbitrary subset of sends
 * throws, the service still attempts delivery to every remaining valid recipient, records
 * each failed recipient with a {@link NotEmailedReason#SEND_FAILURE} reason, and preserves
 * the count invariant ({@code sentCount + notEmailedCount == totalAttempted}).</p>
 *
 * <p>Every generated owner is given a distinct, valid email so the only not-emailed reason
 * possible is {@code SEND_FAILURE}. A generated subset of those owners is chosen to fail;
 * the mocked {@link JavaMailSender} throws when (and only when) the outgoing message's
 * recipient address matches a failing owner. Because a real {@link jakarta.mail.internet.MimeMessage}
 * is used, the "To" header written by the service is readable back, letting the stub decide
 * failure per recipient and letting the test verify that every valid recipient was attempted.</p>
 *
 * <p><b>Validates: Requirements 8.3, 8.5</b></p>
 */
class OwnerEmailFailureIsolationPropertyTest {

    // Feature: owner-email, Property 10: A per-recipient send failure does not halt processing of the others
    @Property(tries = 100)
    void perRecipientSendFailureDoesNotHaltTheOthers(
            @ForAll("failureScenarios") FailureScenario scenario) {

        List<Owner> recipients = scenario.recipients;
        Set<String> failingEmails = scenario.failingEmails;

        // --- Arrange collaborators (mocked JavaMailSender per instructions) ---
        JavaMailSender mailSender = mock(JavaMailSender.class);
        // Use a real MimeMessage so the service-written "To" header is readable in the stub.
        when(mailSender.createMimeMessage())
                .thenAnswer(invocation -> new MimeMessage((jakarta.mail.Session) null));

        // Track every recipient the service actually attempted to send to (Req 8.5).
        List<String> attemptedRecipients = new ArrayList<>();
        doAnswer(invocation -> {
            MimeMessage message = invocation.getArgument(0);
            String to = extractRecipient(message);
            attemptedRecipients.add(to);
            // Only the chosen subset throws; the rest "succeed" (Req 8.3).
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
        request.setBody("This is the message body");

        // --- Act ---
        SendReportDTO report = service.sendOwnerEmail(request);

        // --- Assert: every valid recipient was still attempted, even past failures (Req 8.5) ---
        List<String> expectedAttempts = recipients.stream()
                .map(o -> o.getEmail().trim())
                .toList();
        assertThat(attemptedRecipients)
                .as("every valid recipient is attempted regardless of intervening failures")
                .containsExactlyInAnyOrderElementsOf(expectedAttempts);

        // --- Assert: each failed recipient is recorded with SEND_FAILURE (Req 8.3) ---
        List<NotEmailedEntry> notEmailed = report.getNotEmailed();
        assertThat(notEmailed).as("notEmailed list is never null").isNotNull();

        assertThat(notEmailed)
                .as("every not-emailed entry here is a SEND_FAILURE (all emails are valid)")
                .allSatisfy(entry ->
                        assertThat(entry.getReason()).isEqualTo(NotEmailedReason.SEND_FAILURE));

        Set<Long> failedOwnerIds = recipients.stream()
                .filter(o -> failingEmails.contains(o.getEmail().trim()))
                .map(Owner::getOwnerId)
                .collect(java.util.stream.Collectors.toSet());
        Set<Long> recordedFailedIds = notEmailed.stream()
                .map(NotEmailedEntry::getOwnerId)
                .collect(java.util.stream.Collectors.toSet());
        assertThat(recordedFailedIds)
                .as("exactly the failing recipients are recorded as SEND_FAILURE")
                .isEqualTo(failedOwnerIds);

        // --- Assert: the count invariant is preserved (Req 8.4 supporting 8.3/8.5) ---
        int expectedFailures = failedOwnerIds.size();
        int expectedSent = recipients.size() - expectedFailures;

        assertThat(report.getTotalAttempted())
                .as("totalAttempted equals the number of resolved recipients")
                .isEqualTo(recipients.size());
        assertThat(report.getSentCount())
                .as("successful sends are exactly the non-failing recipients")
                .isEqualTo(expectedSent);
        assertThat(report.getNotEmailedCount())
                .as("not-emailed count equals the number of failing recipients")
                .isEqualTo(expectedFailures);
        assertThat(report.getSentCount() + report.getNotEmailedCount())
                .as("sentCount + notEmailedCount == totalAttempted")
                .isEqualTo(report.getTotalAttempted());
        assertThat(notEmailed.size())
                .as("notEmailed.size() == notEmailedCount")
                .isEqualTo(report.getNotEmailedCount());
    }

    /** Reads back the single "To" recipient the service set on the outgoing message. */
    private static String extractRecipient(MimeMessage message) throws Exception {
        Address[] addresses = message.getRecipients(Message.RecipientType.TO);
        assertThat(addresses).as("outgoing message has exactly one recipient").hasSize(1);
        return ((InternetAddress) addresses[0]).getAddress();
    }

    /** A generated recipient set plus the subset of their emails whose send should throw. */
    private static final class FailureScenario {
        final List<Owner> recipients;
        final Set<String> failingEmails;

        FailureScenario(List<Owner> recipients, Set<String> failingEmails) {
            this.recipients = recipients;
            this.failingEmails = failingEmails;
        }
    }

    /**
     * Generates 0..25 owners, each with a distinct valid email, then picks an arbitrary
     * subset (via per-owner coin flips) whose send should throw. Distinct emails make the
     * failing subset unambiguous and let the stub decide failure per recipient. The subset
     * ranges over none, some, and all failing across the 100 tries.
     */
    @Provide
    Arbitrary<FailureScenario> failureScenarios() {
        Arbitrary<Integer> sizes = Arbitraries.integers().between(0, 25);
        return sizes.flatMap(size -> {
            // A boolean per owner: true means its send should throw.
            Arbitrary<List<Boolean>> failFlags =
                    Arbitraries.of(true, false).list().ofSize(size);
            return failFlags.map(flags -> {
                List<Owner> recipients = new ArrayList<>(size);
                Set<String> failingEmails = new HashSet<>();
                for (int i = 0; i < size; i++) {
                    // Distinct, valid email per owner.
                    String email = "owner" + i + "@example.com";
                    recipients.add(Owner.builder()
                            .ownerId((long) (i + 1))
                            .fullName("Owner " + i)
                            .email(email)
                            .build());
                    if (flags.get(i)) {
                        failingEmails.add(email);
                    }
                }
                return new FailureScenario(recipients, failingEmails);
            });
        });
    }
}
