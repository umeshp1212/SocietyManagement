package com.society.module.owner.service;

import com.society.enums.OwnerStatus;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end wiring test for {@link OwnerEmailServiceImpl} against a mocked mail transport.
 *
 * <p>GreenMail is not a backend dependency, so this test wires a mocked
 * {@link JavaMailSender} whose {@code createMimeMessage()} produces real
 * {@link MimeMessage} instances (via a {@link JavaMailSenderImpl}). This lets the
 * service assemble messages with {@code MimeMessageHelper} exactly as in production
 * while an {@link ArgumentCaptor} captures the messages handed to the transport, so
 * we can assert the assembled content reached it.</p>
 *
 * <p>Covers:
 * <ul>
 *   <li>Requirement 4.1 — the templated content that reaches the transport preserves
 *       the fixed header -> body -> footer ordering.</li>
 *   <li>Requirements 7.1, 8.3 — a partial-failure send (a mix of successes, a
 *       transport failure, and a missing-email recipient) yields a
 *       {@link SendReportDTO} whose counts reconcile
 *       ({@code sentCount + notEmailedCount == totalAttempted}) with categorized
 *       not-emailed reasons.</li>
 * </ul>
 *
 * <p>Validates: Requirements 4.1, 7.1, 8.3
 */
@ExtendWith(MockitoExtension.class)
class OwnerEmailWiringTest {

    @Mock
    private OwnerRepository ownerRepository;

    @Mock
    private SocietySettingsService societySettingsService;

    @Mock
    private JavaMailSender mailSender;

    private OwnerEmailServiceImpl service;

    /** Produces real MimeMessage instances so MimeMessageHelper works against the mock. */
    private final JavaMailSenderImpl mimeMessageFactory = new JavaMailSenderImpl();

    private static final String FROM_EMAIL = "noreply@society.com";

    @BeforeEach
    void setUp() {
        // The template builder, sanitizer, and plain-text renderer are pure components;
        // use the real ones for a true end-to-end assembly path
        // (settings -> sanitize -> builder/renderer -> transport).
        service = new OwnerEmailServiceImpl(ownerRepository, societySettingsService,
                new OwnerEmailTemplateBuilder(), new OwnerEmailSanitizer(),
                new OwnerEmailPlainTextRenderer());
        // JavaMailSender is @Autowired(required=false) and fromEmail is @Value-injected;
        // set both directly since this is a plain Mockito unit test (no Spring context).
        ReflectionTestUtils.setField(service, "mailSender", mailSender);
        ReflectionTestUtils.setField(service, "fromEmail", FROM_EMAIL);

        // Every createMimeMessage() call yields a fresh, real MimeMessage.
        when(mailSender.createMimeMessage()).thenAnswer(inv -> mimeMessageFactory.createMimeMessage());
    }

    private SocietySettings validSettings() {
        return SocietySettings.builder()
                .societyName("Green Meadows Society")
                .addressLine1("12 Palm Avenue")
                .city("Pune")
                .state("Maharashtra")
                .pincode("411001")
                .registrationNumber("REG-2024-001")
                .phone("020-11112222")
                .email("office@greenmeadows.example")
                .chairmanName("Alice Chair")
                .secretaryName("Bob Secretary")
                .treasurerName("Carol Treasurer")
                .build();
    }

    private Owner owner(Long id, String name, String email) {
        return Owner.builder()
                .ownerId(id)
                .fullName(name)
                .email(email)
                .status(OwnerStatus.ACTIVE)
                .build();
    }

    private SendOwnerEmailRequest request(RecipientScope scope) {
        SendOwnerEmailRequest req = new SendOwnerEmailRequest();
        req.setRecipientScope(scope);
        req.setSubject("Quarterly Notice");
        req.setBody("Please attend the general body meeting on Sunday.");
        return req;
    }

    // ---- Req 4.1: assembled content (header -> body -> footer) reaches the transport ----

    @Test
    void assembledContentReachesTransportWithHeaderBodyFooterOrdering() throws Exception {
        SocietySettings settings = validSettings();
        when(societySettingsService.getSettings()).thenReturn(settings);
        when(ownerRepository.findByStatusOrderByFullNameAsc(OwnerStatus.ACTIVE))
                .thenReturn(List.of(owner(1L, "Alice Chair", "alice@example.com")));
        doNothing().when(mailSender).send(any(MimeMessage.class));

        SendReportDTO report = service.sendOwnerEmail(request(RecipientScope.ALL));

        assertThat(report.getSentCount()).isEqualTo(1);

        // Capture what was actually handed to the transport and read its content back.
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();

        assertThat(sent.getSubject()).isEqualTo("Quarterly Notice");
        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("alice@example.com");

        String content = extractContent(sent);

        // Header identity, body content, and footer signatories are all present...
        assertThat(content)
                .contains("Green Meadows Society")   // header
                .contains("Reg. No: REG-2024-001")   // header
                .contains("Quarterly Notice")        // body (subject line)
                .contains("Please attend the general body meeting on Sunday.") // body
                .contains("Chairman: Alice Chair")   // footer
                .contains("Treasurer: Carol Treasurer"); // footer

        // ...and appear in the fixed order header -> body -> footer (Req 4.1).
        int headerIdx = content.indexOf("Green Meadows Society");
        int bodyIdx = content.indexOf("Please attend the general body meeting");
        int footerIdx = content.indexOf("Chairman: Alice Chair");
        assertThat(headerIdx).isGreaterThanOrEqualTo(0);
        assertThat(headerIdx).isLessThan(bodyIdx);
        assertThat(bodyIdx).isLessThan(footerIdx);
    }

    // ---- Req 7.1, 8.3: partial-failure produces the expected SendReport shape ----

    @Test
    void partialFailureProducesReconciledReportWithCategorizedReasons() {
        SocietySettings settings = validSettings();
        when(societySettingsService.getSettings()).thenReturn(settings);

        // Four attempted recipients: two valid emails, one that fails at the transport,
        // and one with a missing email (never sent to).
        Owner ok1 = owner(1L, "Alice Chair", "alice@example.com");
        Owner failing = owner(2L, "Dan Delivery", "dan@example.com");
        Owner ok2 = owner(3L, "Eve Everyone", "eve@example.com");
        Owner missingEmail = owner(4L, "Frank NoEmail", "   ");
        when(ownerRepository.findByStatusOrderByFullNameAsc(OwnerStatus.ACTIVE))
                .thenReturn(List.of(ok1, failing, ok2, missingEmail));

        // The transport throws only for the failing recipient; succeeds for the others.
        doAnswer(inv -> {
            MimeMessage msg = inv.getArgument(0);
            String to = msg.getAllRecipients()[0].toString();
            if ("dan@example.com".equals(to)) {
                throw new MailSendException("simulated transport failure");
            }
            return null;
        }).when(mailSender).send(any(MimeMessage.class));

        SendReportDTO report = service.sendOwnerEmail(request(RecipientScope.ALL));

        // Count invariant: sent + notEmailed == totalAttempted (Req 7.1).
        assertThat(report.getTotalAttempted()).isEqualTo(4);
        assertThat(report.getSentCount()).isEqualTo(2);
        assertThat(report.getNotEmailedCount()).isEqualTo(2);
        assertThat(report.getSentCount() + report.getNotEmailedCount())
                .isEqualTo(report.getTotalAttempted());
        assertThat(report.getNotEmailed()).hasSize(report.getNotEmailedCount());
        assertThat(report.isMailConfigured()).isTrue();

        // The transport is invoked once per valid recipient (3), never for the missing-email one.
        verify(mailSender, times(3)).send(any(MimeMessage.class));

        // Not-emailed entries carry identity + categorized reason (Req 8.3 categorization).
        NotEmailedEntry sendFailure = report.getNotEmailed().stream()
                .filter(e -> e.getReason() == NotEmailedReason.SEND_FAILURE)
                .findFirst().orElseThrow();
        assertThat(sendFailure.getOwnerId()).isEqualTo(2L);
        assertThat(sendFailure.getOwnerName()).isEqualTo("Dan Delivery");

        NotEmailedEntry missing = report.getNotEmailed().stream()
                .filter(e -> e.getReason() == NotEmailedReason.MISSING_EMAIL)
                .findFirst().orElseThrow();
        assertThat(missing.getOwnerId()).isEqualTo(4L);
        assertThat(missing.getOwnerName()).isEqualTo("Frank NoEmail");
    }

    // ---- Req 8.3: a per-recipient failure never halts delivery to the rest ----

    @Test
    void failureForOneRecipientDoesNotStopDeliveryToOthers() {
        when(societySettingsService.getSettings()).thenReturn(validSettings());
        Owner first = owner(1L, "First Owner", "first@example.com");
        Owner second = owner(2L, "Second Owner", "second@example.com");
        Owner third = owner(3L, "Third Owner", "third@example.com");
        when(ownerRepository.findByStatusOrderByFullNameAsc(OwnerStatus.ACTIVE))
                .thenReturn(List.of(first, second, third));

        // The very first recipient fails; the remaining two must still be attempted.
        doThrow(new MailSendException("boom"))
                .doNothing()
                .doNothing()
                .when(mailSender).send(any(MimeMessage.class));

        SendReportDTO report = service.sendOwnerEmail(request(RecipientScope.ALL));

        verify(mailSender, times(3)).send(any(MimeMessage.class));
        assertThat(report.getSentCount()).isEqualTo(2);
        assertThat(report.getNotEmailedCount()).isEqualTo(1);
        assertThat(report.getNotEmailed().get(0).getReason())
                .isEqualTo(NotEmailedReason.SEND_FAILURE);
        assertThat(report.getSentCount() + report.getNotEmailedCount())
                .isEqualTo(report.getTotalAttempted());
    }

    /** Reads the (possibly multipart) content of a MimeMessage back to a String. */
    private String extractContent(MimeMessage message) throws Exception {
        Object content = message.getContent();
        if (content instanceof String s) {
            return s;
        }
        // MimeMessageHelper(message, true) can produce a multipart; fall back to raw bytes.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        message.writeTo(out);
        return out.toString("UTF-8");
    }
}
