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
 * <p><b>Feature: owner-email, Property 7: Only valid recipients are sent to; invalid
 * recipients are excluded and reported.</b> For any recipient set containing a mix of
 * valid (non-blank email) and invalid (null/blank email) owners, the service invokes the
 * mail sender only for valid recipients, never sends to any invalid recipient, and records
 * every invalid recipient in the not-emailed list with a {@code MISSING_EMAIL} reason.</p>
 *
 * <p><b>Validates: Requirements 5.1, 5.2, 5.3</b></p>
 */
class OwnerEmailValidOnlySendingPropertyTest {

    // Feature: owner-email, Property 7: Only valid recipients are sent to; invalid recipients are excluded and reported
    @Property(tries = 100)
    void onlyValidRecipientsAreSentToAndInvalidRecipientsAreReported(
            @ForAll("mixedRecipientSets") List<Owner> recipients) throws Exception {

        // --- Arrange collaborators ---
        // Use a real JavaMailSenderImpl (spied) so createMimeMessage() yields a real
        // MimeMessage whose recipients we can read back after MimeMessageHelper writes them.
        // send(MimeMessage) is stubbed to a no-op so the transport is never actually used.
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

        // The sanitizer and plain-text renderer are pure components; the real ones pass the
        // plain-text body through unchanged so the send flow behaves exactly as before.
        OwnerEmailServiceImpl service =
                new OwnerEmailServiceImpl(ownerRepository, settingsService, templateBuilder,
                        new OwnerEmailSanitizer(), new OwnerEmailPlainTextRenderer());
        ReflectionTestUtils.setField(service, "mailSender", mailSender);
        ReflectionTestUtils.setField(service, "fromEmail", "noreply@society.com");

        SendOwnerEmailRequest request = new SendOwnerEmailRequest();
        request.setRecipientScope(RecipientScope.ALL);
        request.setSubject("Hello owners");
        request.setBody("This is the message body");

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

        // --- Assert: the mail sender is invoked exactly once per valid recipient (Req 5.1) ---
        verify(mailSender, times((int) validCount)).send(any(MimeMessage.class));

        // --- Assert: every message dispatched targeted a valid (non-blank) email, and never
        // an invalid recipient (Req 5.1, 5.2) ---
        ArgumentCaptor<MimeMessage> sentCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times((int) validCount)).send(sentCaptor.capture());

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
                    .as("mail sender is only invoked for valid (non-blank) recipient emails")
                    .contains(recipient);
            assertThat(invalidEmailValues)
                    .as("mail sender is never invoked for an invalid recipient's email value")
                    .doesNotContain(recipient);
        }

        // --- Assert: each invalid recipient is recorded MISSING_EMAIL (Req 5.2, 5.3) ---
        List<NotEmailedEntry> missingEmailEntries = report.getNotEmailed().stream()
                .filter(e -> e.getReason() == NotEmailedReason.MISSING_EMAIL)
                .collect(Collectors.toList());

        assertThat(missingEmailEntries)
                .as("one MISSING_EMAIL entry per invalid recipient")
                .hasSize(invalidOwners.size());

        // No invalid recipient is ever counted as sent; sentCount equals the valid count.
        assertThat(report.getSentCount())
                .as("sentCount equals the number of valid recipients")
                .isEqualTo((int) validCount);

        // Each invalid owner appears in the MISSING_EMAIL entries by its id + name (Req 5.3).
        Set<Long> missingIds = missingEmailEntries.stream()
                .map(NotEmailedEntry::getOwnerId)
                .collect(Collectors.toSet());
        for (Owner invalid : invalidOwners) {
            assertThat(missingIds)
                    .as("invalid recipient recorded in not-emailed list with MISSING_EMAIL")
                    .contains(invalid.getOwnerId());
        }
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
        // Email variants: a valid unique address, or an invalid one (null / empty / whitespace).
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
