package com.society.module.owner.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.society.module.owner.dto.RecipientScope;
import com.society.module.owner.dto.SendOwnerEmailRequest;
import com.society.module.owner.dto.SendReportDTO;
import com.society.module.owner.service.OwnerEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration tests for {@link OwnerEmailController} on
 * {@code POST /owners/email/send} — Task 4 of the {@code owner-email-attachment-multipart-fix}
 * bugfix, "Integration tests" slice of the design Testing Strategy.
 *
 * <p>Unlike the standalone-MockMvc unit/property tests, these exercise the FULL request
 * pipeline ({@code @SpringBootTest} + {@link MockMvc}) so multipart resolution, argument
 * binding, the production {@code GlobalExceptionHandler}, and the method-security guard
 * ({@code @PreAuthorize}) all run exactly as in production. The multipart resolver is what
 * classifies a typed {@code request} part as a {@code MultipartFile}, so only a full-context
 * test proves the fix end-to-end.</p>
 *
 * <p>{@link OwnerEmailService} is replaced with a {@link MockBean} so the tests assert on
 * dispatch delegation (including the exact attachment list) rather than a real send, while
 * every other bean — security, Jackson, validation — is the real thing.</p>
 *
 * <p>Cases covered:</p>
 * <ul>
 *   <li>full multipart send WITH attachments returns {@code 200} with a {@link SendReportDTO}
 *       and delegates to the service with the attachments (2.2);</li>
 *   <li>full multipart send with NO attachments behaves identically to the JSON endpoint
 *       (3.2, 3.4);</li>
 *   <li>an unauthorised caller is rejected across BOTH content types (3.3).</li>
 * </ul>
 *
 * <p><b>Validates: Requirements 2.2, 3.2, 3.3, 3.4</b></p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        // Isolated in-memory H2 (MySQL-compatible); Hibernate creates the schema so the full
        // application context and the real security pipeline load without an external DB.
        "spring.datasource.url=jdbc:h2:mem:owner_email_multipart_fix_it;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.sql.init.mode=never",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=OFF"
})
class OwnerEmailMultipartFixIntegrationTest {

    private static final String SEND_PATH = "/owners/email/send";

    private static final String OWNER_EMAIL_SEND = "OWNER_EMAIL_SEND";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** Replace the real send service so we assert on delegation without a live mail transport. */
    @MockBean
    private OwnerEmailService ownerEmailService;

    @BeforeEach
    void resetService() {
        reset(ownerEmailService);
        when(ownerEmailService.sendOwnerEmail(any(SendOwnerEmailRequest.class)))
                .thenReturn(stubReport());
        when(ownerEmailService.sendOwnerEmail(any(SendOwnerEmailRequest.class), any()))
                .thenReturn(stubReport());
    }

    // ------------------------------------------------------------------
    // 2.2 — full multipart send WITH attachments end-to-end -> 200 + report, delegated.
    // ------------------------------------------------------------------

    // Feature: owner-email-attachment-multipart-fix, Property 1: multipart send with attachments dispatches end-to-end
    @Test
    @WithMockUser(username = "sender_mpfix_it", authorities = {OWNER_EMAIL_SEND})
    @SuppressWarnings("unchecked")
    void multipartSendWithAttachmentsReturns200AndDelegatesWithAttachments() throws Exception {
        byte[] json = objectMapper.writeValueAsString(validPayload()).getBytes(StandardCharsets.UTF_8);
        MockMultipartFile requestPart =
                new MockMultipartFile("request", "request.json", "application/json", json);
        MockMultipartFile fileA =
                new MockMultipartFile("attachments", "notice.pdf", "application/pdf", "PDF-A".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile fileB =
                new MockMultipartFile("attachments", "agenda.pdf", "application/pdf", "PDF-B".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart(SEND_PATH).file(requestPart).file(fileA).file(fileB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                // The SendReportDTO envelope is returned.
                .andExpect(jsonPath("$.data.totalAttempted", is(0)))
                .andExpect(jsonPath("$.data.mailConfigured", is(false)));

        // Delegation carries the two bound attachments to the two-arg service overload.
        org.mockito.ArgumentCaptor<List<MultipartFile>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(ownerEmailService, times(1)).sendOwnerEmail(any(SendOwnerEmailRequest.class), captor.capture());
        assertThat(captor.getValue()).extracting(MultipartFile::getOriginalFilename)
                .containsExactly("notice.pdf", "agenda.pdf");
    }

    // ------------------------------------------------------------------
    // 3.2, 3.4 — multipart send with NO attachments behaves like the JSON endpoint.
    // ------------------------------------------------------------------

    // Feature: owner-email-attachment-multipart-fix, Property 2: Preservation - no-attachment multipart matches the JSON path
    @Test
    @WithMockUser(username = "sender_mpfix_it", authorities = {OWNER_EMAIL_SEND})
    void multipartSendWithoutAttachmentsBehavesLikeJsonEndpoint() throws Exception {
        byte[] json = objectMapper.writeValueAsString(validPayload()).getBytes(StandardCharsets.UTF_8);

        // JSON endpoint baseline.
        mockMvc.perform(post(SEND_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalAttempted", is(0)));
        verify(ownerEmailService, times(1)).sendOwnerEmail(any(SendOwnerEmailRequest.class));

        // Multipart with no attachments must produce the same 200 + report envelope.
        reset(ownerEmailService);
        when(ownerEmailService.sendOwnerEmail(any(SendOwnerEmailRequest.class), any()))
                .thenReturn(stubReport());

        MockMultipartFile requestPart =
                new MockMultipartFile("request", "request.json", "application/json", json);
        mockMvc.perform(multipart(SEND_PATH).file(requestPart))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalAttempted", is(0)));

        // Dispatch occurred through the two-arg overload with a null/empty attachment list.
        verify(ownerEmailService, times(1)).sendOwnerEmail(any(SendOwnerEmailRequest.class), any());
    }

    // ------------------------------------------------------------------
    // 3.3 — unauthorised caller rejected across BOTH content types.
    // ------------------------------------------------------------------

    // Feature: owner-email-attachment-multipart-fix, Property 2: Preservation - anonymous caller rejected on both content types
    @Test
    @WithAnonymousUser
    void anonymousCallerIsRejectedAcrossBothContentTypes() throws Exception {
        byte[] json = objectMapper.writeValueAsString(validPayload()).getBytes(StandardCharsets.UTF_8);

        // JSON representation.
        mockMvc.perform(post(SEND_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnauthorized());

        // Multipart representation (typed request part) — rejected before any binding runs.
        mockMvc.perform(multipart(SEND_PATH)
                        .file(new MockMultipartFile("request", "request.json", "application/json", json)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(ownerEmailService);
    }

    // Feature: owner-email-attachment-multipart-fix, Property 2: Preservation - caller without authority rejected on both content types
    @Test
    @WithMockUser(username = "no_access_mpfix_it", authorities = {"ROLE_PLAIN"})
    void authenticatedCallerWithoutAuthorityIsRejectedAcrossBothContentTypes() throws Exception {
        byte[] json = objectMapper.writeValueAsString(validPayload()).getBytes(StandardCharsets.UTF_8);

        // JSON representation with a valid payload reaches the @PreAuthorize guard -> 403.
        mockMvc.perform(post(SEND_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden());

        // Multipart typed-part representation now binds after the fix, then the method-security
        // guard rejects the authenticated-but-unauthorised caller with 403 before any send.
        mockMvc.perform(multipart(SEND_PATH)
                        .file(new MockMultipartFile("request", "request.json", "application/json", json)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(ownerEmailService);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private SendOwnerEmailRequest validPayload() {
        SendOwnerEmailRequest request = new SendOwnerEmailRequest();
        request.setRecipientScope(RecipientScope.ALL);
        request.setSubject("Society Notice");
        request.setBody("Please note the upcoming general body meeting this weekend.");
        return request;
    }

    private SendReportDTO stubReport() {
        return SendReportDTO.builder()
                .totalAttempted(0)
                .sentCount(0)
                .notEmailedCount(0)
                .mailConfigured(false)
                .notEmailed(List.of())
                .build();
    }
}
