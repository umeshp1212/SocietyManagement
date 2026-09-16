package com.society.module.owner.service;

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
import org.mockito.Mockito;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property test for {@link OwnerEmailServiceImpl#sendOwnerEmail}.
 *
 * <p><b>Feature: owner-email, Property 6: Send report counts reconcile with attempted
 * recipients.</b> For any resolved recipient set, the returned {@link SendReportDTO}
 * satisfies {@code sentCount + notEmailedCount == totalAttempted}, where
 * {@code totalAttempted} equals the number of resolved recipients, and
 * {@code notEmailed.size() == notEmailedCount}.</p>
 *
 * <p><b>Validates: Requirements 5.4, 7.1, 8.4</b></p>
 */
class OwnerEmailSendReportReconciliationPropertyTest {

    // Feature: owner-email, Property 6: Send report counts reconcile with attempted recipients
    @Property(tries = 100)
    void sendReportCountsReconcileWithAttemptedRecipients(
            @ForAll("recipientSets") List<Owner> recipients) {

        // --- Arrange collaborators (mocked JavaMailSender per instructions) ---
        JavaMailSender mailSender = mock(JavaMailSender.class);
        // A stub MimeMessage is enough for the helper; the send itself is a no-op.
        when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));

        OwnerRepository ownerRepository = mock(OwnerRepository.class);
        // The recipient set is resolved through the ALL scope active-owner query.
        when(ownerRepository.findByStatusOrderByFullNameAsc(any())).thenReturn(recipients);

        SocietySettingsService settingsService = mock(SocietySettingsService.class);
        when(settingsService.getSettings()).thenReturn(new SocietySettings());

        // Stub the pure template builder so template concerns do not affect this property.
        OwnerEmailTemplateBuilder templateBuilder = mock(OwnerEmailTemplateBuilder.class);
        when(templateBuilder.buildSubject(any())).thenReturn("Subject");
        when(templateBuilder.buildBody(any(), any(), any())).thenReturn("Body");

        OwnerEmailServiceImpl service =
                new OwnerEmailServiceImpl(ownerRepository, settingsService, templateBuilder);
        // mailSender is @Autowired(required=false) and fromEmail is @Value-injected.
        ReflectionTestUtils.setField(service, "mailSender", mailSender);
        ReflectionTestUtils.setField(service, "fromEmail", "noreply@society.com");

        SendOwnerEmailRequest request = new SendOwnerEmailRequest();
        request.setRecipientScope(RecipientScope.ALL);
        request.setSubject("Hello owners");
        request.setBody("This is the message body");

        // --- Act ---
        SendReportDTO report = service.sendOwnerEmail(request);

        // --- Assert: the count invariant (Req 5.4, 7.1, 8.4) ---
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

        // Counts are non-negative.
        assertThat(report.getSentCount()).as("sentCount is non-negative").isGreaterThanOrEqualTo(0);
        assertThat(report.getNotEmailedCount()).as("notEmailedCount is non-negative")
                .isGreaterThanOrEqualTo(0);
    }

    /**
     * Generates recipient sets (0..30 owners) mixing valid (non-blank email) and invalid
     * (null / blank / whitespace email) owners, exercising both the send-success and the
     * MISSING_EMAIL classification paths so the reconciliation invariant is tested broadly.
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
