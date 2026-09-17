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
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property test for {@link OwnerEmailServiceImpl#sendOwnerEmail}.
 *
 * <p><b>Feature: owner-email-rich-text, Property 9: Mail-absent yields a graceful, zero-sent
 * report.</b> The rich-text change (sanitize-once-per-request plus multipart/alternative send)
 * must not alter the mail-not-configured behavior. When the
 * {@link org.springframework.mail.javamail.JavaMailSender} bean is absent (mail not configured),
 * for any resolved recipient set the service sends nothing, returns normally (no error
 * propagated to the caller), and produces a {@link SendReportDTO} with {@code sentCount == 0},
 * {@code mailConfigured == false}, and {@code notEmailedCount == totalAttempted}.</p>
 *
 * <p>The {@code mailSender} field is left unset (null) to simulate the
 * {@code @Autowired(required = false)} bean being absent. The recipient set mixes valid and
 * invalid emails to confirm that, when mail is absent, every attempted recipient is uniformly
 * reported regardless of email validity. The sanitizer and plain-text renderer are the real
 * (pure) components and the request body carries HTML, so the flow behaves exactly as in
 * production.</p>
 *
 * <p><b>Validates: Requirements 5.3</b></p>
 */
class OwnerEmailMailAbsentPropertyTest {

    // Feature: owner-email-rich-text, Property 9: Mail-absent yields a graceful zero-sent report
    @Property(tries = 100)
    void mailAbsentYieldsGracefulZeroSentReportWithNoError(
            @ForAll("recipientSets") List<Owner> recipients) {

        // --- Arrange collaborators; mailSender is deliberately left null (mail absent). ---
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
        // mailSender is @Autowired(required=false); leaving it unset simulates the absent bean.
        ReflectionTestUtils.setField(service, "fromEmail", "noreply@society.com");

        SendOwnerEmailRequest request = new SendOwnerEmailRequest();
        request.setRecipientScope(RecipientScope.ALL);
        request.setSubject("Hello owners");
        request.setBody("<p>This is the message body</p>");

        // --- Act & Assert: the call returns normally, propagating no error (Req 5.3). ---
        SendReportDTO[] captured = new SendReportDTO[1];
        assertThatCode(() -> captured[0] = service.sendOwnerEmail(request))
                .as("mail-absent send completes without raising an error to the caller")
                .doesNotThrowAnyException();

        SendReportDTO report = captured[0];
        assertThat(report).as("report is returned in a completed state").isNotNull();

        // --- Assert: zero-sent, mail-not-configured report (Req 5.3). ---
        assertThat(report.getSentCount())
                .as("sentCount == 0 when mail is not configured")
                .isEqualTo(0);
        assertThat(report.isMailConfigured())
                .as("mailConfigured == false when mail is not configured")
                .isFalse();
        assertThat(report.getTotalAttempted())
                .as("totalAttempted equals the number of resolved recipients")
                .isEqualTo(recipients.size());
        assertThat(report.getNotEmailedCount())
                .as("notEmailedCount == totalAttempted when nothing is sent")
                .isEqualTo(report.getTotalAttempted());

        // --- Assert: every attempted recipient is reported as MAIL_NOT_CONFIGURED. ---
        assertThat(report.getNotEmailed()).as("notEmailed list is never null").isNotNull();
        assertThat(report.getNotEmailed().size())
                .as("notEmailed.size() == notEmailedCount")
                .isEqualTo(report.getNotEmailedCount());
        assertThat(report.getNotEmailed())
                .as("every attempted recipient is recorded MAIL_NOT_CONFIGURED when mail is absent")
                .allSatisfy(entry ->
                        assertThat(entry.getReason()).isEqualTo(NotEmailedReason.MAIL_NOT_CONFIGURED));
    }

    /**
     * Generates recipient sets (0..30 owners) mixing valid (non-blank email) and invalid
     * (null / blank / whitespace email) owners. When mail is absent, email validity is
     * irrelevant, so this broad mix confirms the uniform mail-not-configured outcome.
     */
    @Provide
    Arbitrary<List<Owner>> recipientSets() {
        return owner().list().ofMinSize(0).ofMaxSize(30);
    }

    private Arbitrary<Owner> owner() {
        Arbitrary<Long> ids = Arbitraries.longs().between(1L, 100_000L);
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
