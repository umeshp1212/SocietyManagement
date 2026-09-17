package com.society.module.owner.service;

import com.society.enums.OwnerStatus;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration test for the plain-text structure of the sent owner email (task 7.3).
 *
 * <p>Wires {@link OwnerEmailServiceImpl} against a mocked {@link JavaMailSender} whose
 * {@code createMimeMessage()} yields real {@link MimeMessage} instances (via a
 * {@link JavaMailSenderImpl}), so the service assembles a real
 * {@code multipart/alternative} message with {@code MimeMessageHelper} exactly as in
 * production. The {@code text/plain} alternative part is extracted from the captured
 * message and asserted to reflect the sanitized body's structure:
 * <ul>
 *   <li>ordered-list items render as {@code "1. "}, {@code "2. "}, ... (numbered),</li>
 *   <li>unordered-list items render as {@code "- "}, and</li>
 *   <li>paragraph breaks render as blank lines separating the paragraphs.</li>
 * </ul>
 *
 * <p>Validates: Requirements 7.3
 */
@ExtendWith(MockitoExtension.class)
class OwnerEmailPlainTextStructureIntegrationTest {

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
        // Use the real pure components so the full assembly path runs end-to-end:
        // settings -> sanitize -> plain-text renderer -> transport.
        service = new OwnerEmailServiceImpl(ownerRepository, societySettingsService,
                new OwnerEmailTemplateBuilder(), new OwnerEmailSanitizer(),
                new OwnerEmailPlainTextRenderer());
        ReflectionTestUtils.setField(service, "mailSender", mailSender);
        ReflectionTestUtils.setField(service, "fromEmail", FROM_EMAIL);

        when(mailSender.createMimeMessage())
                .thenAnswer(inv -> mimeMessageFactory.createMimeMessage());
    }

    @Test
    void plainTextPartRendersListsAndParagraphStructure() throws Exception {
        when(societySettingsService.getSettings()).thenReturn(validSettings());
        when(ownerRepository.findByStatusOrderByFullNameAsc(OwnerStatus.ACTIVE))
                .thenReturn(List.of(owner(1L, "Alice Chair", "alice@example.com")));
        doNothing().when(mailSender).send(any(MimeMessage.class));

        // A body exercising: two paragraphs, an ordered list, and an unordered list.
        String body = "<p>FirstParagraph</p>"
                + "<p>SecondParagraph</p>"
                + "<ol><li>AlphaItem</li><li>BetaItem</li><li>GammaItem</li></ol>"
                + "<ul><li>RedBullet</li><li>BlueBullet</li></ul>";

        SendReportDTO report = service.sendOwnerEmail(request(body));
        assertThat(report.getSentCount()).isEqualTo(1);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());

        String plainText = extractPlainTextPart(captor.getValue());
        String normalised = plainText.replace("\r\n", "\n").replace('\r', '\n');

        // Ordered lists render as numbered "1. ", "2. ", "3. " markers.
        assertThat(normalised)
                .contains("1. AlphaItem")
                .contains("2. BetaItem")
                .contains("3. GammaItem");

        // Unordered lists render as "- " markers.
        assertThat(normalised)
                .contains("- RedBullet")
                .contains("- BlueBullet");

        // Paragraph breaks render as a blank line separating the two paragraphs
        // (i.e. the paragraphs are joined by two consecutive newlines).
        assertThat(normalised).contains("FirstParagraph\n\nSecondParagraph");

        // Sanity: the plain-text part carries no HTML tag markup.
        assertThat(normalised).doesNotContainPattern("</?[a-zA-Z][^>]*>");
    }

    // ----- helpers -----

    /**
     * Extracts the {@code text/plain} alternative from the (nested) multipart message the
     * service assembled via {@code helper.setText(plainText, htmlText)}.
     */
    private String extractPlainTextPart(Part part) throws Exception {
        Object content = part.getContent();
        if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                String found = extractPlainTextPart(multipart.getBodyPart(i));
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        if (part.isMimeType("text/plain") && content instanceof String s) {
            return s;
        }
        return null;
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

    private SendOwnerEmailRequest request(String body) {
        SendOwnerEmailRequest req = new SendOwnerEmailRequest();
        req.setRecipientScope(RecipientScope.ALL);
        req.setSubject("Structure Notice");
        req.setBody(body);
        return req;
    }
}
