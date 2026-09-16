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
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Conventional unit tests (JUnit + Mockito) for {@link OwnerEmailServiceImpl#sendOwnerEmail}
 * covering the service's edge cases that the property tests do not pin down as concrete
 * examples.
 *
 * <ul>
 *     <li>No recipients &rarr; an all-zero report (Req 5.6).</li>
 *     <li>All-invalid recipients &rarr; {@code sentCount == 0},
 *     {@code notEmailedCount == totalAttempted}, every entry {@code MISSING_EMAIL},
 *     and the transport is never invoked (Req 5.5).</li>
 *     <li>Zero successful sends (every send throws) &rarr; {@code sentCount == 0},
 *     {@code notEmailedCount == totalAttempted}, every entry {@code SEND_FAILURE} (Req 7.3).</li>
 *     <li>The outgoing message's from-address is the configured {@code fromEmail}
 *     (from {@code spring.mail.username}, default {@code noreply@society.com}).</li>
 * </ul>
 *
 * <p>Collaborators are mocked following the conventions of the existing service property
 * tests: the {@link OwnerEmailTemplateBuilder} is stubbed so template concerns do not affect
 * these edge cases, recipients are resolved through the {@code ALL}-scope active-owner query,
 * and {@code mailSender} / {@code fromEmail} are injected via {@link ReflectionTestUtils}.</p>
 *
 * <p><b>Validates: Requirements 5.5, 5.6, 7.3</b></p>
 */
class OwnerEmailServiceImplEdgeCaseTest {

    private JavaMailSender mailSender;
    private OwnerRepository ownerRepository;
    private OwnerEmailTemplateBuilder templateBuilder;
    private OwnerEmailServiceImpl service;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);

        ownerRepository = mock(OwnerRepository.class);

        SocietySettingsService settingsService = mock(SocietySettingsService.class);
        when(settingsService.getSettings()).thenReturn(new SocietySettings());

        // Stub the pure template builder so template concerns do not affect these edge cases.
        templateBuilder = mock(OwnerEmailTemplateBuilder.class);
        when(templateBuilder.buildSubject(any())).thenReturn("Assembled Subject");
        when(templateBuilder.buildBody(any(), any(), any())).thenReturn("Assembled Body");

        service = new OwnerEmailServiceImpl(ownerRepository, settingsService, templateBuilder);
        // mailSender is @Autowired(required=false) and fromEmail is @Value-injected.
        ReflectionTestUtils.setField(service, "mailSender", mailSender);
        ReflectionTestUtils.setField(service, "fromEmail", "noreply@society.com");
    }

    private SendOwnerEmailRequest allScopeRequest() {
        SendOwnerEmailRequest request = new SendOwnerEmailRequest();
        request.setRecipientScope(RecipientScope.ALL);
        request.setSubject("Hello owners");
        request.setBody("This is the message body");
        return request;
    }

    // --- Edge case 1: No recipients -> all-zero report (Req 5.6) ---

    @Test
    void noRecipientsYieldsAllZeroReport() {
        // SELECTED scope with a null owner-id list resolves to zero recipients.
        SendOwnerEmailRequest request = new SendOwnerEmailRequest();
        request.setRecipientScope(RecipientScope.SELECTED);
        request.setOwnerIds(null);
        request.setSubject("Hello owners");
        request.setBody("This is the message body");

        SendReportDTO report = service.sendOwnerEmail(request);

        assertThat(report.getTotalAttempted()).as("totalAttempted == 0 with no recipients").isZero();
        assertThat(report.getSentCount()).as("sentCount == 0 with no recipients").isZero();
        assertThat(report.getNotEmailedCount()).as("notEmailedCount == 0 with no recipients").isZero();
        assertThat(report.getNotEmailed())
                .as("notEmailed list is empty with no recipients")
                .isNotNull()
                .isEmpty();
        assertThat(report.isMailConfigured())
                .as("mail is configured (sender present) even when there are no recipients")
                .isTrue();

        // Nothing is ever dispatched when there are no recipients.
        verify(mailSender, never()).createMimeMessage();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void allScopeWithZeroActiveOwnersYieldsAllZeroReport() {
        // ALL scope with an empty active-owner set is the other no-recipients path.
        when(ownerRepository.findByStatusOrderByFullNameAsc(any()))
                .thenReturn(Collections.emptyList());

        SendReportDTO report = service.sendOwnerEmail(allScopeRequest());

        assertThat(report.getTotalAttempted()).isZero();
        assertThat(report.getSentCount()).isZero();
        assertThat(report.getNotEmailedCount()).isZero();
        assertThat(report.getNotEmailed()).isNotNull().isEmpty();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    // --- Edge case 2: All-invalid recipients (Req 5.5) ---

    @Test
    void allInvalidRecipientsAreReportedAndNothingIsSent() {
        List<Owner> recipients = List.of(
                Owner.builder().ownerId(1L).fullName("No Email").email(null).build(),
                Owner.builder().ownerId(2L).fullName("Empty Email").email("").build(),
                Owner.builder().ownerId(3L).fullName("Blank Email").email("   ").build());
        when(ownerRepository.findByStatusOrderByFullNameAsc(any())).thenReturn(recipients);

        SendReportDTO report = service.sendOwnerEmail(allScopeRequest());

        assertThat(report.getTotalAttempted())
                .as("every selected recipient is attempted")
                .isEqualTo(3);
        assertThat(report.getSentCount())
                .as("no valid recipients means sentCount == 0 (Req 5.5)")
                .isZero();
        assertThat(report.getNotEmailedCount())
                .as("notEmailedCount == totalAttempted when all recipients are invalid (Req 5.5)")
                .isEqualTo(report.getTotalAttempted());
        assertThat(report.getNotEmailed())
                .as("each invalid recipient is recorded MISSING_EMAIL (Req 5.3)")
                .hasSize(3)
                .allSatisfy(entry ->
                        assertThat(entry.getReason()).isEqualTo(NotEmailedReason.MISSING_EMAIL));
        assertThat(report.getNotEmailed())
                .extracting(NotEmailedEntry::getOwnerId)
                .containsExactlyInAnyOrder(1L, 2L, 3L);

        // The transport is never invoked when every recipient is invalid.
        verify(mailSender, never()).createMimeMessage();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    // --- Edge case 3: Zero successful sends (Req 7.3) ---

    @Test
    void everySendFailingYieldsZeroSuccessfulSends() {
        List<Owner> recipients = List.of(
                Owner.builder().ownerId(1L).fullName("Owner One").email("one@example.com").build(),
                Owner.builder().ownerId(2L).fullName("Owner Two").email("two@example.com").build());
        when(ownerRepository.findByStatusOrderByFullNameAsc(any())).thenReturn(recipients);
        when(mailSender.createMimeMessage())
                .thenAnswer(invocation -> new MimeMessage((jakarta.mail.Session) null));
        // Every transport send throws, so no recipient can succeed.
        doThrow(new MailSendException("simulated transport failure"))
                .when(mailSender).send(any(MimeMessage.class));

        SendReportDTO report = service.sendOwnerEmail(allScopeRequest());

        assertThat(report.getTotalAttempted()).isEqualTo(2);
        assertThat(report.getSentCount())
                .as("zero successful sends means sentCount == 0 (Req 7.3)")
                .isZero();
        assertThat(report.getNotEmailedCount())
                .as("notEmailedCount == totalAttempted when every send fails")
                .isEqualTo(report.getTotalAttempted());
        assertThat(report.getNotEmailed())
                .as("each failed recipient is recorded SEND_FAILURE (Req 7.2)")
                .hasSize(2)
                .allSatisfy(entry ->
                        assertThat(entry.getReason()).isEqualTo(NotEmailedReason.SEND_FAILURE));

        // Every valid recipient was still attempted, even though all sends failed.
        verify(mailSender, times(2)).send(any(MimeMessage.class));
    }

    // --- Edge case 4: MimeMessageHelper uses the configured fromEmail ---

    @Test
    void outgoingMessageUsesConfiguredFromEmail() throws Exception {
        when(ownerRepository.findByStatusOrderByFullNameAsc(any())).thenReturn(List.of(
                Owner.builder().ownerId(1L).fullName("Owner One").email("one@example.com").build()));
        // A real, session-less MimeMessage lets the from-address written by the helper be read back.
        when(mailSender.createMimeMessage())
                .thenAnswer(invocation -> new MimeMessage((jakarta.mail.Session) null));

        SendReportDTO report = service.sendOwnerEmail(allScopeRequest());

        assertThat(report.getSentCount()).as("the single valid recipient is sent to").isEqualTo(1);

        ArgumentCaptor<MimeMessage> sentCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(1)).send(sentCaptor.capture());

        Address[] from = sentCaptor.getValue().getFrom();
        assertThat(from).as("outgoing message carries a from-address").hasSize(1);
        assertThat(((InternetAddress) from[0]).getAddress())
                .as("from-address is the configured fromEmail (spring.mail.username default)")
                .isEqualTo("noreply@society.com");
    }
}
