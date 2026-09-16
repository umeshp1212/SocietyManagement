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
import jakarta.mail.internet.MimeMessage;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property test for {@link OwnerEmailServiceImpl#sendOwnerEmail}.
 *
 * <p><b>Feature: owner-email, Property 8: Every not-emailed recipient carries an identity
 * and a categorized reason.</b> For any send outcome, each entry in the not-emailed list
 * contains the owner's identifier ({@code ownerId}, non-null), the owner's name
 * ({@code ownerName}, non-null/non-blank), and a categorized failure reason
 * ({@code MISSING_EMAIL}, {@code SEND_FAILURE}, or {@code MAIL_NOT_CONFIGURED}).</p>
 *
 * <p>The generator deliberately drives all three not-emailed categories:</p>
 * <ul>
 *     <li>{@code MISSING_EMAIL} — owners with null/blank email in a configured-mail run.</li>
 *     <li>{@code SEND_FAILURE} — a configured mail sender that throws on every send.</li>
 *     <li>{@code MAIL_NOT_CONFIGURED} — a null mail sender so nothing is sent.</li>
 * </ul>
 *
 * <p><b>Validates: Requirements 5.3, 7.2</b></p>
 */
class OwnerEmailNotEmailedCompletenessPropertyTest {

    /** The way the (single, best-effort) send should behave for a given try. */
    private enum SendBehavior {
        /** Mail configured; sends succeed. Only MISSING_EMAIL entries appear. */
        MAIL_OK,
        /** Mail configured; every send throws. Valid owners become SEND_FAILURE. */
        MAIL_THROWS,
        /** Mail not configured (null sender). Every attempted owner is MAIL_NOT_CONFIGURED. */
        MAIL_ABSENT
    }

    // Feature: owner-email, Property 8: Every not-emailed recipient carries an identity and a categorized reason
    @Property(tries = 100)
    void everyNotEmailedEntryHasIdentityAndCategorizedReason(
            @ForAll("recipientSets") List<Owner> recipients,
            @ForAll("sendBehaviors") SendBehavior behavior) {

        // --- Arrange collaborators (mocked JavaMailSender per instructions) ---
        JavaMailSender mailSender = null;
        if (behavior != SendBehavior.MAIL_ABSENT) {
            mailSender = mock(JavaMailSender.class);
            when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));
            if (behavior == SendBehavior.MAIL_THROWS) {
                // Every send fails, so each valid recipient is recorded SEND_FAILURE.
                doThrow(new MailSendException("simulated transport failure"))
                        .when(mailSender).send(any(MimeMessage.class));
            }
        }

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
        // mailSender is @Autowired(required=false); may be null for the MAIL_ABSENT case.
        ReflectionTestUtils.setField(service, "mailSender", mailSender);
        ReflectionTestUtils.setField(service, "fromEmail", "noreply@society.com");

        SendOwnerEmailRequest request = new SendOwnerEmailRequest();
        request.setRecipientScope(RecipientScope.ALL);
        request.setSubject("Hello owners");
        request.setBody("This is the message body");

        // --- Act ---
        SendReportDTO report = service.sendOwnerEmail(request);

        // --- Assert: the not-emailed list is present and every entry is complete (Req 5.3, 7.2) ---
        List<NotEmailedEntry> notEmailed = report.getNotEmailed();
        assertThat(notEmailed).as("notEmailed list is never null").isNotNull();

        for (NotEmailedEntry entry : notEmailed) {
            assertThat(entry).as("not-emailed entry is never null").isNotNull();

            assertThat(entry.getOwnerId())
                    .as("every not-emailed entry carries the owner's identifier")
                    .isNotNull();

            assertThat(entry.getOwnerName())
                    .as("every not-emailed entry carries the owner's name (non-null, non-blank)")
                    .isNotNull()
                    .isNotBlank();

            assertThat(entry.getReason())
                    .as("every not-emailed entry carries a categorized reason")
                    .isNotNull()
                    .isIn(NotEmailedReason.MISSING_EMAIL,
                            NotEmailedReason.SEND_FAILURE,
                            NotEmailedReason.MAIL_NOT_CONFIGURED);
        }
    }

    /**
     * Generates recipient sets (0..30 owners) mixing valid (non-blank email) and invalid
     * (null / empty / whitespace email) owners. Names are always non-blank so the
     * "identity is present" assertion reflects real not-emailed entries. Combined with the
     * send-behavior generator this exercises all three reason categories.
     */
    @Provide
    Arbitrary<List<Owner>> recipientSets() {
        return owner().list().ofMinSize(0).ofMaxSize(30);
    }

    @Provide
    Arbitrary<SendBehavior> sendBehaviors() {
        return Arbitraries.of(SendBehavior.class);
    }

    private Arbitrary<Owner> owner() {
        Arbitrary<Long> ids = Arbitraries.longs().between(1L, 1_000_000L);
        // Names are non-blank (min length 1, alphanumeric) so a not-emailed entry always
        // carries a meaningful identity, matching how active owners are persisted.
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
