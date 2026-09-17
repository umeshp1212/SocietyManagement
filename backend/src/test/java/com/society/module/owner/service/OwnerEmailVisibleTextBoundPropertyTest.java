package com.society.module.owner.service;

import com.society.exception.BusinessException;
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
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Property test for {@link OwnerEmailServiceImpl#sendOwnerEmail}.
 *
 * <p><b>Feature: owner-email-rich-text, Property 5: Server visible-text-length invariant.</b>
 * The service enforces the visible-text bound on the {@code Sanitized_Body} — the exact
 * content that would be sent — before any send loop runs:
 * <ul>
 *   <li>a body whose sanitized visible text is empty (length {@code 0}) is rejected with a
 *       {@link BusinessException} and no email is sent (Req 4.4),</li>
 *   <li>a body whose sanitized visible text exceeds {@code 10_000} characters is rejected with a
 *       {@link BusinessException} and no email is sent (Req 4.5), and</li>
 *   <li>a body whose sanitized visible text is in range ({@code 1..10_000}) is accepted and the
 *       mail sender is invoked once per valid recipient.</li>
 * </ul>
 * On every rejection the mail transport is never touched — {@code mailSender.send(...)} and
 * {@code mailSender.createMimeMessage()} are both never invoked — so no partial send record can
 * exist (Req 4.4, 4.5). The visible-text bound is measured on the sanitized body via the real
 * {@link OwnerEmailSanitizer}, so the expected outcome is derived from what will actually be
 * sent rather than the raw HTML.</p>
 *
 * <p><b>Validates: Requirements 4.4, 4.5</b></p>
 */
class OwnerEmailVisibleTextBoundPropertyTest {

    /** Real sanitizer, used both by the service under test and to derive the expected outcome. */
    private final OwnerEmailSanitizer sanitizer = new OwnerEmailSanitizer();

    // Feature: owner-email-rich-text, Property 5: Server visible-text-length invariant
    @Property(tries = 100)
    void visibleTextBoundIsEnforcedOnTheSanitizedBodyBeforeAnySend(
            @ForAll("bodyCase") BodyCase bodyCase) {

        // --- Arrange collaborators (mocked JavaMailSender per instructions) ---
        JavaMailSender mailSender = mock(JavaMailSender.class);
        // A stub MimeMessage suffices for the helper; the send itself is a no-op.
        when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));

        // A single valid recipient so an accepted request produces exactly one send.
        Owner recipient = Owner.builder()
                .ownerId(1L)
                .fullName("Owner One")
                .email("owner1@example.com")
                .build();

        OwnerRepository ownerRepository = mock(OwnerRepository.class);
        when(ownerRepository.findByStatusOrderByFullNameAsc(any())).thenReturn(List.of(recipient));

        SocietySettingsService settingsService = mock(SocietySettingsService.class);
        when(settingsService.getSettings()).thenReturn(new SocietySettings());

        // Stub the pure template builder so template concerns do not affect this property.
        OwnerEmailTemplateBuilder templateBuilder = mock(OwnerEmailTemplateBuilder.class);
        when(templateBuilder.buildSubject(any())).thenReturn("Subject");
        when(templateBuilder.buildHtmlBody(any(), any(), any())).thenReturn("<p>Body</p>");

        // The service uses the SAME real sanitizer used to derive the expected outcome.
        OwnerEmailServiceImpl service =
                new OwnerEmailServiceImpl(ownerRepository, settingsService, templateBuilder,
                        sanitizer, new OwnerEmailPlainTextRenderer());
        ReflectionTestUtils.setField(service, "mailSender", mailSender);
        ReflectionTestUtils.setField(service, "fromEmail", "noreply@society.com");

        SendOwnerEmailRequest request = new SendOwnerEmailRequest();
        request.setRecipientScope(RecipientScope.ALL);
        request.setSubject("Hello owners");
        request.setBody(bodyCase.html);

        // Derive the authoritative expectation from the sanitized visible text — exactly what
        // the service measures — so this property is robust to how sanitization trims/collapses.
        int visibleLength = sanitizer.visibleText(sanitizer.sanitize(bodyCase.html)).length();
        boolean shouldReject = visibleLength == 0 || visibleLength > 10_000;

        if (shouldReject) {
            // --- Reject with no send: the BusinessException propagates before the send loop ---
            assertThatThrownBy(() -> service.sendOwnerEmail(request))
                    .as("empty (0) or over-length (>10000) visible text is rejected for body <%s>",
                            bodyCase.label)
                    .isInstanceOf(BusinessException.class);

            // The mail transport is never touched: no message is created and nothing is sent,
            // so there is no partial send record (Req 4.4, 4.5).
            verify(mailSender, never()).send(any(MimeMessage.class));
            verify(mailSender, never()).createMimeMessage();
        } else {
            // --- Accept in-range: the single valid recipient is sent to exactly once ---
            SendReportDTO report = service.sendOwnerEmail(request);

            verify(mailSender, times(1)).send(any(MimeMessage.class));
            assertThat(report.getSentCount())
                    .as("in-range visible text (%d chars) is accepted and sent for body <%s>",
                            visibleLength, bodyCase.label)
                    .isEqualTo(1);
            assertThat(report.getTotalAttempted())
                    .as("exactly one recipient was attempted")
                    .isEqualTo(1);
        }
    }

    /** A generated body paired with a short human-readable label for diagnostics. */
    private static final class BodyCase {
        final String html;
        final String label;

        BodyCase(String html, String label) {
            this.html = html;
            this.label = label;
        }
    }

    /**
     * Generates bodies covering the three visible-text categories, so every property run
     * exercises at least one of the reject/accept boundaries:
     * <ul>
     *   <li><b>empty</b> — null, blank, or markup-only HTML that sanitizes to zero visible text,</li>
     *   <li><b>in range</b> — allowed-formatting HTML whose visible text is {@code 1..10_000}, and</li>
     *   <li><b>over length</b> — allowed-formatting HTML whose visible text exceeds {@code 10_000}.</li>
     * </ul>
     */
    @Provide
    Arbitrary<BodyCase> bodyCase() {
        return Arbitraries.oneOf(emptyVisible(), inRangeVisible(), overLengthVisible());
    }

    /** Bodies whose sanitized visible text is empty: null, whitespace-only, or markup that carries no text. */
    private Arbitrary<BodyCase> emptyVisible() {
        return Arbitraries.of(
                new BodyCase(null, "null"),
                new BodyCase("", "empty-string"),
                new BodyCase("   ", "whitespace"),
                new BodyCase("<p></p>", "empty-paragraph"),
                new BodyCase("<p>   </p>", "whitespace-paragraph"),
                new BodyCase("<br><br>", "line-breaks-only"),
                new BodyCase("<ul><li></li></ul>", "empty-list-item"),
                // Disallowed markup is stripped entirely, leaving no visible text.
                new BodyCase("<script>alert('x')</script>", "script-only"),
                new BodyCase("<style>.a{color:red}</style>", "style-only"));
    }

    /**
     * Bodies whose sanitized visible text is within {@code 1..10_000} characters. The visible
     * text is a run of {@code n} non-space letters wrapped in an allowed element, so trimming
     * and whitespace collapse cannot change its length.
     */
    private Arbitrary<BodyCase> inRangeVisible() {
        Arbitrary<Integer> lengths = Arbitraries.oneOf(
                Arbitraries.integers().between(1, 200),      // small bodies + the lower boundary
                Arbitraries.integers().between(9_990, 10_000)); // the upper boundary neighbourhood
        return lengths.map(n -> {
            String text = "a".repeat(n);
            return new BodyCase("<p>" + text + "</p>", "in-range:" + n);
        });
    }

    /**
     * Bodies whose sanitized visible text exceeds {@code 10_000} characters, including the
     * just-over boundary ({@code 10_001}).
     */
    private Arbitrary<BodyCase> overLengthVisible() {
        return Arbitraries.integers().between(10_001, 12_000).map(n -> {
            String text = "a".repeat(n);
            return new BodyCase("<p>" + text + "</p>", "over-length:" + n);
        });
    }
}
