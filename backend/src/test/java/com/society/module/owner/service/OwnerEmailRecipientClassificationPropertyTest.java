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
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Property test for {@link OwnerEmailServiceImpl#sendOwnerEmail}.
 *
 * <p><b>Feature: owner-email-rich-text, Property 8: Recipient classification is preserved.</b>
 * The rich-text change (sanitize-once-per-request plus multipart/alternative send) must not
 * alter how recipients are classified. For any recipient set mixing valid (non-blank email)
 * and invalid (null / empty / whitespace email) owners, the service:
 * <ul>
 *   <li>invokes the {@link org.springframework.mail.javamail.JavaMailSender} exactly once per
 *       valid recipient and never for an invalid recipient (Req 5.2),</li>
 *   <li>dispatches every message to a valid (non-blank) email address only, never to an invalid
 *       recipient's email value (Req 5.2), and</li>
 *   <li>records every invalid recipient in the {@code Send_Report} not-emailed list with a
 *       {@link NotEmailedReason#MISSING_EMAIL} reason, keyed by the owner's identity (Req 5.2).</li>
 * </ul>
 * A body with real visible text is used so the send loop is reached; the sanitizer and
 * plain-text renderer are the real (pure) components so the flow behaves exactly as in
 * production.</p>
 *
 * <p><b>Validates: Requirements 5.2</b></p>
 */
class OwnerEmailRecipientClassificationPropertyTest {

    // Feature: owner-email-rich-text, Property 8: Recipient classification is preserved
    @Property(tries = 100)
    void sendInvokedOnlyForValidRecipientsAndEachInvalidRecordedMissingEmail(
            @ForAll("mixedRecipientSets") List<Owner> recipients) throws Exception {

        // --- Arrange collaborators ---
        // A spied JavaMailSenderImpl yields a real MimeMessage from createMimeMessage(), so the
        // recipient addresses MimeMessageHelper writes can be read back and asserted. The actual
        // transport send(MimeMessage) is stubbed to a no-op so nothing is ever dispatched.
        JavaMailSenderImpl mailSender = spy(new JavaMailSenderImpl());
        doNothing().when(mailSender).send(any(MimeMessage.class));

        OwnerRepository ownerRepository = mock(OwnerRepository.class);
        when(ownerRepository.findByStatusOrderByFullNameAsc(any())).thenReturn(recipients);

        SocietySettingsService settingsService = mock(SocietySettingsService.class);
        when(settingsService.getSettings()).thenReturn(new SocietySettings());

        // Stub the pure template builder so template concerns do not affect this property.
        OwnerEmailTemplateBuilder templateBuilder = mock(OwnerEmailTemplateBuilder.class);
        when(templateBuilder.buildSubject(any())).thenReturn("Subject");
        when(templateBuilder.buildHtmlBody(any(), any(), any())).thenReturn("<p>Body</p>");

        // Real sanitizer + renderer: the body sanitizes to non-empty visible text so the
        // send loop is reached and classification is exercised (Req 5.2).
        OwnerEmailServiceImpl service =
                new OwnerEmailServiceImpl(ownerRepository, settingsService, templateBuilder,
                        new OwnerEmailSanitizer(), new OwnerEmailPlainTextRenderer());
        ReflectionTestUtils.setField(service, "mailSender", mailSender);
        ReflectionTestUtils.setField(service, "fromEmail", "noreply@society.com");

        SendOwnerEmailRequest request = new SendOwnerEmailRequest();
        request.setRecipientScope(RecipientScope.ALL);
        request.setSubject("Hello owners");
        request.setBody("<p>This is the message body</p>");

        // Partition the generated owners into valid (sendable) and invalid (missing email).
        Set<String> validEmails = recipients.stream()
                .map(Owner::getEmail)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toSet());
        List<Owner> invalidOwners = recipients.stream()
                .filter(o -> !StringUtils.hasText(o.getEmail()))
                .collect(Collectors.toList());
        long validCount = recipients.stream()
                .filter(o -> StringUtils.hasText(o.getEmail()))
                .count();

        // --- Act ---
        SendReportDTO report = service.sendOwnerEmail(request);

        // --- Assert: send invoked exactly once per valid recipient, never for invalid (Req 5.2) ---
        ArgumentCaptor<MimeMessage> sentCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times((int) validCount)).send(sentCaptor.capture());

        // The literal email values of invalid recipients (e.g. "" or "   "), used to prove none
        // of them was ever dispatched to.
        Set<String> invalidEmailValues = new HashSet<>();
        for (Owner invalid : invalidOwners) {
            if (invalid.getEmail() != null) {
                invalidEmailValues.add(invalid.getEmail());
            }
        }

        for (MimeMessage sent : sentCaptor.getAllValues()) {
            Message.RecipientType to = Message.RecipientType.TO;
            assertThat(sent.getRecipients(to))
                    .as("every dispatched message has exactly one recipient")
                    .hasSize(1);
            String recipient = sent.getRecipients(to)[0].toString();
            assertThat(validEmails)
                    .as("send is only invoked for valid (non-blank) recipient emails")
                    .contains(recipient);
            assertThat(invalidEmailValues)
                    .as("send is never invoked for an invalid recipient's email value")
                    .doesNotContain(recipient);
        }

        // --- Assert: each invalid recipient recorded MISSING_EMAIL, keyed by identity (Req 5.2) ---
        List<NotEmailedEntry> missingEmailEntries = report.getNotEmailed().stream()
                .filter(e -> e.getReason() == NotEmailedReason.MISSING_EMAIL)
                .collect(Collectors.toList());

        assertThat(missingEmailEntries)
                .as("exactly one MISSING_EMAIL entry per invalid recipient")
                .hasSize(invalidOwners.size());

        assertThat(report.getSentCount())
                .as("sentCount equals the number of valid recipients")
                .isEqualTo((int) validCount);

        Set<Long> missingIds = missingEmailEntries.stream()
                .map(NotEmailedEntry::getOwnerId)
                .collect(Collectors.toSet());
        for (Owner invalid : invalidOwners) {
            assertThat(missingIds)
                    .as("invalid recipient recorded in not-emailed list with MISSING_EMAIL")
                    .contains(invalid.getOwnerId());
        }

        // No invalid recipient is classified as sent, and no valid recipient is classified
        // as missing-email: the two classifications are disjoint and total.
        assertThat(report.getSentCount() + missingEmailEntries.size())
                .as("every recipient is classified exactly once (sent XOR missing-email)")
                .isEqualTo(recipients.size());
    }

    /**
     * Generates recipient sets (0..30 owners) that deliberately mix valid (non-blank email)
     * and invalid (null / empty / whitespace email) owners, ensuring both classification
     * paths are exercised. Owner ids are unique so not-emailed entries can be matched by id.
     */
    @Provide
    Arbitrary<List<Owner>> mixedRecipientSets() {
        return owner().list().ofMinSize(0).ofMaxSize(30).uniqueElements(Owner::getOwnerId);
    }

    private Arbitrary<Owner> owner() {
        Arbitrary<Long> ids = Arbitraries.longs().between(1L, 1_000_000L);
        Arbitrary<String> names = Arbitraries.strings().alpha().numeric()
                .ofMinLength(1).ofMaxLength(30);
        // Email variants: a valid address, or an invalid one (null / empty / whitespace).
        Arbitrary<String> emails = Arbitraries.oneOf(
                Arbitraries.strings().alpha().numeric().ofMinLength(3).ofMaxLength(15)
                        .map(local -> local + "@example.com"),
                Arbitraries.just(null),
                Arbitraries.just(""),
                Arbitraries.just("   "));

        return Combinators.combine(ids, names, emails).as((id, name, email) ->
                Owner.builder()
                        .ownerId(id)
                        .fullName(name)
                        .email(email)
                        .build());
    }
}
