package com.society.module.owner.service;

import com.society.enums.OwnerStatus;
import com.society.module.owner.dto.RecipientScope;
import com.society.module.owner.dto.SendOwnerEmailRequest;
import com.society.module.owner.dto.SendReportDTO;
import com.society.module.owner.entity.Owner;
import com.society.module.owner.repository.OwnerRepository;
import com.society.module.settings.entity.SocietySettings;
import com.society.module.settings.service.SocietySettingsService;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration/wiring test for {@link OwnerEmailServiceImpl} that inspects the MIME structure
 * of the message actually handed to the transport.
 *
 * <p>GreenMail is not a backend dependency, so this test wires a mocked {@link JavaMailSender}
 * whose {@code createMimeMessage()} produces real {@link MimeMessage} instances (via a
 * {@link JavaMailSenderImpl}). The service assembles messages with {@code MimeMessageHelper}
 * exactly as in production while an {@link ArgumentCaptor} captures the message so the assembled
 * MIME tree can be walked and asserted.</p>
 *
 * <p>Because the send switched from {@code helper.setText(body, true)} (single-part HTML) to
 * {@code helper.setText(plainText, assembledHtml)}, the assembled message must be
 * {@code multipart/alternative} carrying both a {@code text/plain} part and a {@code text/html}
 * part (Req 2.4, 7.1). When attachments are present, JavaMail nests that
 * {@code multipart/alternative} inside an outer {@code multipart/mixed} that also carries the
 * attachments &mdash; both readable-in-any-client and attachments must survive.</p>
 *
 * <p>Validates: Requirements 2.4, 7.1
 */
@ExtendWith(MockitoExtension.class)
class OwnerEmailMultipartAlternativeWiringTest {

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
        // Real pure components for a true end-to-end assembly path
        // (settings -> sanitize -> builder/renderer -> transport).
        service = new OwnerEmailServiceImpl(ownerRepository, societySettingsService,
                new OwnerEmailTemplateBuilder(), new OwnerEmailSanitizer(),
                new OwnerEmailPlainTextRenderer());
        ReflectionTestUtils.setField(service, "mailSender", mailSender);
        ReflectionTestUtils.setField(service, "fromEmail", FROM_EMAIL);

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

    private SendOwnerEmailRequest request() {
        SendOwnerEmailRequest req = new SendOwnerEmailRequest();
        req.setRecipientScope(RecipientScope.ALL);
        req.setSubject("Quarterly Notice");
        // Rich-text body with allowed formatting so both HTML and plain-text parts are meaningful.
        req.setBody("<p>Please attend the general body meeting on <strong>Sunday</strong>.</p>");
        return req;
    }

    // ---- Req 2.4, 7.1: without attachments, the message itself is multipart/alternative ----

    @Test
    void sentMessageIsMultipartAlternativeCarryingPlainTextAndHtmlParts() throws Exception {
        when(societySettingsService.getSettings()).thenReturn(validSettings());
        when(ownerRepository.findByStatusOrderByFullNameAsc(OwnerStatus.ACTIVE))
                .thenReturn(List.of(owner(1L, "Alice Chair", "alice@example.com")));
        doNothing().when(mailSender).send(any(MimeMessage.class));

        SendReportDTO report = service.sendOwnerEmail(request());
        assertThat(report.getSentCount()).isEqualTo(1);

        MimeMessage sent = captureSentMessage();

        // MimeMessageHelper(message, true) builds a multipart root so attachments can be added;
        // the multipart/alternative (plain + HTML) is nested within it. Locate the alternative
        // wherever it sits in the tree.
        Object content = sent.getContent();
        assertThat(content).isInstanceOf(MimeMultipart.class);
        MimeMultipart root = (MimeMultipart) content;

        MimeMultipart alternative = root.getContentType() != null
                && root.getContentType().toLowerCase().contains("multipart/alternative")
                ? root
                : findNestedMultipartOfType(root, "multipart/alternative");
        assertThat(alternative).as("multipart/alternative container").isNotNull();
        assertThat(alternative.getContentType()).containsIgnoringCase("multipart/alternative");

        // The alternative must carry a text/plain part and a text/html part.
        List<Part> leaves = new ArrayList<>();
        collectLeafParts(alternative, leaves);

        Part plain = findLeafWithMimeType(leaves, "text/plain");
        Part html = findLeafWithMimeType(leaves, "text/html");
        assertThat(plain).as("text/plain alternative part").isNotNull();
        assertThat(html).as("text/html alternative part").isNotNull();

        // The HTML part preserves the applied formatting and the templated structure.
        String htmlContent = partAsString(html);
        assertThat(htmlContent)
                .contains("Green Meadows Society")   // header
                .contains("<strong>Sunday</strong>") // preserved bold formatting from the body
                .contains("Alice Chair");             // footer signatory (chairman)

        // The plain-text alternative conveys the same content without markup.
        String plainContent = partAsString(plain);
        assertThat(plainContent).contains("Sunday");
        assertThat(plainContent).doesNotContain("<strong>");
    }

    // ---- Req 2.4, 7.1: with attachments, alternative nests under multipart/mixed ----

    @Test
    void withAttachmentsAlternativeNestsUnderMultipartMixed() throws Exception {
        when(societySettingsService.getSettings()).thenReturn(validSettings());
        when(ownerRepository.findByStatusOrderByFullNameAsc(OwnerStatus.ACTIVE))
                .thenReturn(List.of(owner(1L, "Alice Chair", "alice@example.com")));
        doNothing().when(mailSender).send(any(MimeMessage.class));

        List<MultipartFile> attachments = List.of(
                new MockMultipartFile("attachments", "notice.pdf", "application/pdf",
                        "PDF-A".getBytes(StandardCharsets.UTF_8)),
                new MockMultipartFile("attachments", "agenda.pdf", "application/pdf",
                        "PDF-B".getBytes(StandardCharsets.UTF_8)));

        SendReportDTO report = service.sendOwnerEmail(request(), attachments);
        assertThat(report.getSentCount()).isEqualTo(1);

        MimeMessage sent = captureSentMessage();

        // Top-level content type is multipart/mixed (attachments present).
        assertThat(sent.getContentType()).containsIgnoringCase("multipart/mixed");

        Object content = sent.getContent();
        assertThat(content).isInstanceOf(MimeMultipart.class);
        MimeMultipart mixed = (MimeMultipart) content;
        assertThat(mixed.getContentType()).containsIgnoringCase("multipart/mixed");

        // The mixed container must nest a multipart/alternative body part somewhere within it.
        MimeMultipart alternative = findNestedMultipartOfType(mixed, "multipart/alternative");
        assertThat(alternative).as("nested multipart/alternative under multipart/mixed").isNotNull();

        // The alternative still carries both a text/plain and a text/html part.
        List<Part> altLeaves = new ArrayList<>();
        collectLeafParts(alternative, altLeaves);
        assertThat(findLeafWithMimeType(altLeaves, "text/plain")).as("text/plain part").isNotNull();
        assertThat(findLeafWithMimeType(altLeaves, "text/html")).as("text/html part").isNotNull();

        // Both attachments survive as leaf parts named after the uploaded files.
        List<Part> allLeaves = new ArrayList<>();
        collectLeafParts(mixed, allLeaves);
        List<String> attachmentNames = new ArrayList<>();
        for (Part p : allLeaves) {
            if (Part.ATTACHMENT.equalsIgnoreCase(p.getDisposition()) || p.getFileName() != null) {
                attachmentNames.add(p.getFileName());
            }
        }
        assertThat(attachmentNames).contains("notice.pdf", "agenda.pdf");
    }

    // ---- helpers ----

    private MimeMessage captureSentMessage() throws Exception {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage message = captor.getValue();
        // The transport is mocked, so the real send() never called saveChanges(). Invoke it
        // here so the Content-Type headers are updated from the assembled content tree
        // (mirroring what a real transport does before writing the message).
        message.saveChanges();
        return message;
    }

    /** Recursively collects every leaf (non-multipart) part of a multipart tree. */
    private void collectLeafParts(Multipart multipart, List<Part> out) throws Exception {
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            Object partContent = part.getContent();
            if (partContent instanceof Multipart nested) {
                collectLeafParts(nested, out);
            } else {
                out.add(part);
            }
        }
    }

    /** Finds the first leaf part whose MIME type matches (ignoring parameters like charset). */
    private Part findLeafWithMimeType(List<Part> leaves, String mimeType) throws Exception {
        for (Part p : leaves) {
            if (p.isMimeType(mimeType)) {
                return p;
            }
        }
        return null;
    }

    /** Depth-first search for a nested multipart whose content type matches the given subtype. */
    private MimeMultipart findNestedMultipartOfType(Multipart multipart, String type) throws Exception {
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            Object partContent = part.getContent();
            if (partContent instanceof MimeMultipart nested) {
                if (nested.getContentType() != null
                        && nested.getContentType().toLowerCase().contains(type.toLowerCase())) {
                    return nested;
                }
                MimeMultipart deeper = findNestedMultipartOfType(nested, type);
                if (deeper != null) {
                    return deeper;
                }
            }
        }
        return null;
    }

    private String partAsString(Part part) throws Exception {
        Object partContent = part.getContent();
        if (partContent instanceof String s) {
            return s;
        }
        return String.valueOf(partContent);
    }
}
