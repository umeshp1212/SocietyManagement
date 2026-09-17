package com.society.module.owner.service;

import com.society.module.owner.dto.RecipientScope;
import com.society.module.owner.dto.SendOwnerEmailRequest;
import com.society.module.owner.dto.SendReportDTO;
import com.society.module.owner.entity.Owner;
import com.society.module.owner.repository.OwnerRepository;
import com.society.module.settings.entity.SocietySettings;
import com.society.module.settings.service.SocietySettingsService;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration test for {@link OwnerEmailServiceImpl#sendOwnerEmail} proving the
 * authoritative server-side sanitization removes dangerous markup <i>before</i> the
 * message is sent, so a script payload can never reach a recipient.
 *
 * <p>The whole send path is exercised with the <b>real</b> {@link OwnerEmailSanitizer},
 * {@link OwnerEmailPlainTextRenderer}, and {@link OwnerEmailTemplateBuilder}; only the
 * transport ({@link org.springframework.mail.javamail.JavaMailSender}) is spied so the
 * assembled {@link MimeMessage} can be captured without dispatching it. A body carrying
 * a {@code <script>} element and {@code onerror}/{@code onclick} event handlers is
 * submitted; the captured message's rendered HTML part is asserted to contain none of
 * them &mdash; the sanitizer stripped them before the template was assembled and sent
 * (Req 3.1, 3.6).</p>
 *
 * <p><b>Validates: Requirements 3.1, 3.6</b></p>
 */
class OwnerEmailScriptPayloadNeverSentIntegrationTest {

    private JavaMailSenderImpl mailSender;
    private OwnerRepository ownerRepository;
    private OwnerEmailServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        // A spied JavaMailSenderImpl produces a real MimeMessage from createMimeMessage(),
        // so the fully assembled message written by MimeMessageHelper can be read back.
        // The transport send(MimeMessage) is stubbed to a no-op so nothing is dispatched.
        mailSender = spy(new JavaMailSenderImpl());
        doNothing().when(mailSender).send(any(MimeMessage.class));

        ownerRepository = mock(OwnerRepository.class);

        SocietySettingsService settingsService = mock(SocietySettingsService.class);
        when(settingsService.getSettings()).thenReturn(fullSettings());

        // Real collaborators: authoritative sanitization + HTML template assembly + plain text.
        service = new OwnerEmailServiceImpl(ownerRepository, settingsService,
                new OwnerEmailTemplateBuilder(),
                new OwnerEmailSanitizer(), new OwnerEmailPlainTextRenderer());
        ReflectionTestUtils.setField(service, "mailSender", mailSender);
        ReflectionTestUtils.setField(service, "fromEmail", "noreply@society.com");
    }

    @Test
    void scriptAndEventHandlersAreStrippedFromTheSentHtmlPart() throws Exception {
        when(ownerRepository.findByStatusOrderByFullNameAsc(any())).thenReturn(List.of(
                Owner.builder().ownerId(1L).fullName("Owner One").email("one@example.com").build()));

        // A malicious rich-text body: a script element, an img with an onerror handler,
        // and an onclick handler on otherwise-allowed markup, wrapped around real content.
        String maliciousBody = "<p>Hello <b>owners</b></p>"
                + "<script>alert('xss')</script>"
                + "<img src=x onerror=\"alert('boom')\">"
                + "<p onclick=\"steal()\">click me</p>"
                + "<a href=\"javascript:alert(1)\">link</a>";

        SendOwnerEmailRequest request = new SendOwnerEmailRequest();
        request.setRecipientScope(RecipientScope.ALL);
        request.setSubject("Monthly notice");
        request.setBody(maliciousBody);

        SendReportDTO report = service.sendOwnerEmail(request);

        assertThat(report.getSentCount()).as("the single valid recipient is sent to").isEqualTo(1);

        // Capture the assembled message and extract its rendered HTML part.
        ArgumentCaptor<MimeMessage> sentCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(1)).send(sentCaptor.capture());
        MimeMessage sent = sentCaptor.getValue();
        // Persist the in-memory content into the MIME structure so it can be read back.
        sent.saveChanges();
        String htmlPart = extractHtmlPart(sent);

        assertThat(htmlPart).as("an HTML part is present in the sent message").isNotBlank();

        // The dangerous markup was removed by authoritative sanitization before send (Req 3.1, 3.6).
        String lower = htmlPart.toLowerCase();
        assertThat(lower)
                .as("no script element survives in the sent HTML part")
                .doesNotContain("<script")
                .doesNotContain("alert('xss')")
                .doesNotContain("alert(&#39;xss&#39;)");
        assertThat(lower)
                .as("no event-handler attributes survive in the sent HTML part")
                .doesNotContain("onerror")
                .doesNotContain("onclick");
        assertThat(lower)
                .as("no javascript: scheme survives in the sent HTML part")
                .doesNotContain("javascript:");

        // The legitimate content and formatting are still present (sanitized, not lost).
        assertThat(htmlPart)
                .as("allowed formatting and visible text of the body are preserved")
                .contains("Hello")
                .contains("<b>owners</b>")
                .contains("click me")
                .contains("link");
    }

    /**
     * Extracts the {@code text/html} part from the assembled MIME message. The service sends
     * a {@code multipart/alternative} (plain-text + HTML); when there are no attachments that
     * multipart is the message content directly. This walks the MIME tree to find the HTML part.
     */
    private String extractHtmlPart(Part part) throws Exception {
        Object content = part.getContent();
        if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                String html = extractHtmlPart(multipart.getBodyPart(i));
                if (html != null) {
                    return html;
                }
            }
            return null;
        }
        if (part.isMimeType("text/html")) {
            return partToString(part);
        }
        return null;
    }

    private String partToString(Part part) throws Exception {
        Object content = part.getContent();
        if (content instanceof String s) {
            return s;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        part.writeTo(out);
        return out.toString();
    }

    private SocietySettings fullSettings() {
        SocietySettings settings = new SocietySettings();
        settings.setSocietyName("Green Meadows Society");
        settings.setAddressLine1("12 Park Lane");
        settings.setAddressLine2("Sector 5");
        settings.setCity("Metropolis");
        settings.setState("Stateville");
        settings.setPincode("123456");
        settings.setRegistrationNumber("REG-001");
        settings.setPhone("9990001111");
        settings.setEmail("contact@greenmeadows.example.com");
        settings.setChairmanName("Alice Chair");
        settings.setSecretaryName("Bob Secretary");
        settings.setTreasurerName("Carol Treasurer");
        return settings;
    }
}
