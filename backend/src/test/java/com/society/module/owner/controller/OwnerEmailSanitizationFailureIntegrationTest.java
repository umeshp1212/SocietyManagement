package com.society.module.owner.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.society.enums.OwnerStatus;
import com.society.module.owner.dto.RecipientScope;
import com.society.module.owner.dto.SendOwnerEmailRequest;
import com.society.module.owner.entity.Owner;
import com.society.module.owner.repository.OwnerRepository;
import com.society.module.owner.service.OwnerEmailPlainTextRenderer;
import com.society.module.owner.service.OwnerEmailSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc integration test for the sanitization/derivation failure path (Subtask 7.5).
 *
 * <p>Exercises the full request pipeline ({@code @SpringBootTest} + {@link MockMvc})
 * against a seeded in-memory H2 database. It asserts Requirement 3.7: if sanitization
 * (or the plain-text derivation that operates on the sanitized body) cannot be completed,
 * the {@code Owner_Email_Controller} rejects the send request with a client-facing error
 * and initiates <b>no</b> email send operation.</p>
 *
 * <p>The failure is simulated by replacing the real {@link OwnerEmailSanitizer} /
 * {@link OwnerEmailPlainTextRenderer} with a {@link MockBean} whose relevant method throws.
 * Because the sanitize-and-derive step runs once per request <b>before</b> the send loop,
 * the thrown exception must propagate out of the service and be mapped to a client-facing
 * error by the {@code GlobalExceptionHandler}, and the injected {@link JavaMailSender}
 * mock must never be touched (no {@code createMimeMessage}, no {@code send}).</p>
 *
 * <p>An authenticated caller holding {@code OWNER_EMAIL_SEND} is used so the request passes
 * the {@code @PreAuthorize} guard and actually reaches the service — otherwise a 401/403
 * would mask the sanitization-failure behaviour under test. A single active owner with a
 * valid email is seeded so a recipient <em>would</em> be resolved and sent to were the
 * sanitization step to succeed; the assertion that the transport is never invoked is
 * therefore meaningful.</p>
 *
 * <p>Validates: Requirements 3.7</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        // Isolated in-memory H2 (MySQL-compatible); Hibernate creates the schema so the
        // full application context and the real security pipeline load without a real DB.
        "spring.datasource.url=jdbc:h2:mem:owner_email_sanitize_fail_it;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        // The production db/data.sql is MySQL-specific; skip SQL script init for this test.
        "spring.sql.init.mode=never",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=OFF"
})
class OwnerEmailSanitizationFailureIntegrationTest {

    private static final String OWNER_EMAIL_SEND = "OWNER_EMAIL_SEND";

    /**
     * Controller path as seen by {@link MockMvc}. The production
     * {@code server.servlet.context-path=/api} is a servlet-container setting MockMvc
     * does not apply, so requests target the controller mapping directly.
     */
    private static final String SEND_PATH = "/owners/email/send";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OwnerRepository ownerRepository;

    /**
     * The authoritative sanitizer, replaced by a mock so its {@code sanitize(...)} can be
     * made to fail (simulating a sanitization that cannot be completed, Req 3.7).
     */
    @MockBean
    private OwnerEmailSanitizer sanitizer;

    /**
     * The plain-text renderer that derives the plain-text alternative from the sanitized
     * body, replaced by a mock so the derivation failure path can be simulated too.
     */
    @MockBean
    private OwnerEmailPlainTextRenderer plainTextRenderer;

    /**
     * The mail transport, replaced by a mock so we can assert it is never touched when
     * sanitization/derivation fails (no send is initiated, Req 3.7).
     */
    @MockBean
    private JavaMailSender mailSender;

    @BeforeEach
    void seed() {
        ownerRepository.deleteAll();

        // One active owner with a valid email so a recipient WOULD be resolved and sent to
        // if sanitization succeeded — making "the transport is never invoked" meaningful.
        ownerRepository.save(Owner.builder()
                .fullName("Active Owner OE")
                .contactNumber("9000000020")
                .email("active.owner.oe@example.com")
                .status(OwnerStatus.ACTIVE)
                .build());
    }

    private SendOwnerEmailRequest validAllScopeRequest() {
        SendOwnerEmailRequest request = new SendOwnerEmailRequest();
        request.setRecipientScope(RecipientScope.ALL);
        request.setSubject("Society Notice");
        request.setBody("<p>Please note the upcoming general body meeting this weekend.</p>");
        return request;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    // ------------------------------------------------------------------
    // 3.7 Sanitization cannot be completed -> client-facing error, no send
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(username = "sender_sanitize_fail_it", authorities = {OWNER_EMAIL_SEND})
    void sanitizationFailure_returnsClientFacingError_andSendsNothing() throws Exception {
        // Simulate a sanitization step that cannot be completed.
        when(sanitizer.sanitize(any()))
                .thenThrow(new RuntimeException("simulated sanitization failure"));

        mockMvc.perform(post(SEND_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validAllScopeRequest())))
                // A client-facing error is returned (not a 200 with a Send Report).
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.success", is(false)));

        // No email send is initiated: the transport is never touched.
        verify(mailSender, never()).createMimeMessage();
        verify(mailSender, never()).send(any(jakarta.mail.internet.MimeMessage.class));
    }

    // ------------------------------------------------------------------
    // 3.7 Derivation (plain-text rendering from the sanitized body) cannot be
    //     completed -> client-facing error, no send
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(username = "sender_sanitize_fail_it", authorities = {OWNER_EMAIL_SEND})
    void derivationFailure_returnsClientFacingError_andSendsNothing() throws Exception {
        // Sanitization succeeds and yields a non-empty, in-range visible body...
        when(sanitizer.sanitize(any())).thenReturn("<p>Valid sanitized body</p>");
        when(sanitizer.visibleText(any())).thenReturn("Valid sanitized body");
        // ...but the plain-text derivation from the sanitized body cannot be completed.
        when(plainTextRenderer.render(any(), any(), any()))
                .thenThrow(new RuntimeException("simulated derivation failure"));

        mockMvc.perform(post(SEND_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validAllScopeRequest())))
                // A client-facing error is returned rather than a partial/failed send report.
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.success", is(false)));

        // The derivation runs before the per-recipient send loop, so nothing is sent.
        verify(mailSender, never()).createMimeMessage();
        verify(mailSender, never()).send(any(jakarta.mail.internet.MimeMessage.class));
    }
}
