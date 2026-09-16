package com.society.module.owner.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.society.module.owner.dto.RecipientScope;
import com.society.module.owner.dto.SendOwnerEmailRequest;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security-guard preservation property test for {@link OwnerEmailController} on
 * {@code POST /owners/email/send} — Task 2 of the {@code owner-email-attachment-multipart-fix}
 * bugfix.
 *
 * <p><b>Feature: owner-email-attachment-multipart-fix, Property 2: Preservation —
 * Non-Buggy Inputs Behave Identically (security guard slice).</b> An unauthorised caller
 * is a non-buggy input ({@code isBugCondition = false}) because the method-security guard
 * {@code @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('OWNER_EMAIL_SEND')")}
 * rejects the request before any argument binding runs. The rejection must be preserved
 * across the fix, regardless of the request payload or content type.</p>
 *
 * <p><b>Why a {@code @SpringBootTest}.</b> {@code @PreAuthorize} is applied by Spring's
 * method-security interceptor, which is only active in the full application context — a
 * standalone {@code MockMvc} does not enforce it. This mirrors the existing
 * {@link OwnerEmailControllerSecurityIntegrationTest} setup (isolated in-memory H2, real
 * security pipeline).</p>
 *
 * <p><b>Observed baseline (unfixed code):</b></p>
 * <ul>
 *   <li>3.3 — an <em>unauthenticated</em> caller is rejected with {@code 401 Unauthorized}
 *       on both the JSON and multipart representations, regardless of payload.</li>
 *   <li>3.3 — an <em>authenticated</em> caller lacking {@code SUPER_ADMIN}/
 *       {@code OWNER_EMAIL_SEND} is rejected with {@code 403 Forbidden} on the JSON path
 *       when the payload is valid.</li>
 * </ul>
 * <p><b>Interceptor ordering (why the two cases differ).</b> Anonymous rejection happens at
 * the security <em>filter chain</em> before any MVC processing, so it holds regardless of
 * payload/content type ({@code 401}). Authenticated-but-unauthorised rejection is enforced by
 * the {@code @PreAuthorize} <em>method-security interceptor</em>, which runs AFTER Spring MVC
 * argument resolution and {@code @Valid}. So a valid JSON payload reaches the guard and yields
 * {@code 403}, an invalid JSON payload short-circuits at {@code @Valid} with {@code 400}, and a
 * multipart typed-{@code request}-part request satisfies this spec's bug condition and
 * {@code 500}s before the guard. This class therefore drives <em>valid</em> payloads through
 * the JSON representation to demonstrate the {@code 403} guard preservation; the multipart
 * typed-part case is bug-condition input covered by the Task 3.2 fix-checking tests. Task 3.3
 * re-runs this class against the fixed controller to confirm no regression.</p>
 *
 * <p><b>Validates: Requirements 3.3</b></p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        // Isolated in-memory H2 (MySQL-compatible); Hibernate creates the schema so the full
        // application context and the real security pipeline load without an external DB.
        "spring.datasource.url=jdbc:h2:mem:owner_email_multipart_fix_sec;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.sql.init.mode=never",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=OFF"
})
class OwnerEmailMultipartFixSecurityPreservationTest {

    private static final int TRIES = 60;

    private static final String SEND_PATH = "/owners/email/send";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ------------------------------------------------------------------
    // 3.3 — Unauthenticated callers are rejected (401) regardless of payload/content type.
    // ------------------------------------------------------------------

    // Feature: owner-email-attachment-multipart-fix, Property 2: Preservation - security guard rejects unauthorised callers
    @Test
    @WithAnonymousUser
    void anonymousCallerIsRejectedRegardlessOfPayload() throws Exception {
        java.util.Iterator<SendOwnerEmailRequest> payloads = anyPayloads().sampleStream().iterator();

        for (int i = 0; i < TRIES; i++) {
            SendOwnerEmailRequest payload = payloads.next();
            byte[] json = objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);

            // JSON representation -> guard rejects before @Valid/binding.
            mockMvc.perform(post(SEND_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isUnauthorized());

            // Multipart representation with a typed request part (the bug-condition shape) ->
            // guard still rejects before any binding, so the typed-part defect never surfaces.
            mockMvc.perform(multipart(SEND_PATH)
                            .file(new MockMultipartFile("request", "request.json", "application/json", json)))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ------------------------------------------------------------------
    // 3.3 — Authenticated callers lacking authority are rejected (403) on the JSON path.
    // ------------------------------------------------------------------

    // Feature: owner-email-attachment-multipart-fix, Property 2: Preservation - security guard rejects callers without authority
    @Test
    @WithMockUser(username = "no_access_mpfix", authorities = {"ROLE_PLAIN"})
    void authenticatedCallerWithoutAuthorityIsRejectedRegardlessOfPayload() throws Exception {
        // Observation-first: unlike the anonymous case (rejected at the filter chain before
        // anything else), an *authenticated-but-unauthorised* caller is rejected by the
        // @PreAuthorize METHOD-security interceptor, which runs AFTER Spring MVC argument
        // resolution and @Valid. So the observed baseline on the unfixed code is:
        //   - JSON + VALID payload    -> 403 (guard fires; service not dispatched)
        //   - JSON + INVALID payload  -> 400 (@Valid fires before the guard)
        //   - multipart typed part    -> 500 (this spec's bug: binding fails before the guard)
        // To faithfully demonstrate the guard-preservation for 3.3 we therefore drive VALID
        // payloads so the guard is actually reached, and assert 403 on the JSON representation.
        // The multipart typed-`request`-part representation satisfies the bug condition on the
        // unfixed controller (500 before the guard); its correct post-fix behavior is covered
        // by the Task 3.2 fix-checking tests, so it is intentionally NOT asserted here.
        java.util.Iterator<SendOwnerEmailRequest> payloads = validPayloads().sampleStream().iterator();

        for (int i = 0; i < TRIES; i++) {
            SendOwnerEmailRequest payload = payloads.next();
            byte[] json = objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);

            // JSON representation with a VALID payload -> the guard is reached and rejects with
            // 403 Forbidden before the service is dispatched. This cleanly demonstrates the
            // security-guard preservation for 3.3 on a non-buggy (JSON, valid) input.
            mockMvc.perform(post(SEND_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isForbidden());
        }
    }

    // ------------------------------------------------------------------
    // Generator
    // ------------------------------------------------------------------

    /**
     * Generates fully valid {@link SendOwnerEmailRequest} payloads across both recipient
     * scopes with subject/body inside their {@code @Size} bounds — mirroring the
     * {@code validPayloads()} generator in {@link OwnerEmailMultipartFixPreservationPropertyTest}.
     * A valid payload is required so the request passes {@code @Valid} and actually reaches the
     * {@code @PreAuthorize} method-security guard (which runs after argument resolution), letting
     * the test observe the guard's {@code 403} rejection rather than a {@code 400} from validation.
     */
    private Arbitrary<SendOwnerEmailRequest> validPayloads() {
        Arbitrary<RecipientScope> scopes = Arbitraries.of(RecipientScope.ALL, RecipientScope.SELECTED);
        Arbitrary<String> subjects = Arbitraries.strings().alpha().numeric().withChars(' ')
                .ofMinLength(1).ofMaxLength(200).filter(s -> !s.isBlank());
        Arbitrary<String> bodies = Arbitraries.strings().alpha().numeric().withChars(' ', '\n')
                .ofMinLength(1).ofMaxLength(400).filter(s -> !s.isBlank());
        Arbitrary<List<Long>> ownerIds = Arbitraries.longs().between(1, 10_000)
                .list().ofMinSize(1).ofMaxSize(5);

        return Combinators.combine(scopes, subjects, bodies, ownerIds)
                .as((scope, subject, body, ids) -> {
                    SendOwnerEmailRequest request = new SendOwnerEmailRequest();
                    request.setRecipientScope(scope);
                    request.setSubject(subject);
                    request.setBody(body);
                    // Supply owner ids for SELECTED; leave null for ALL (both are valid payloads).
                    request.setOwnerIds(scope == RecipientScope.SELECTED ? ids : null);
                    return request;
                });
    }

    /**
     * Generates arbitrary payloads — valid and invalid alike — since the security guard must
     * reject unauthorised callers <em>regardless</em> of the payload content. Mixes recipient
     * scopes and occasionally blanks subject/body to prove rejection precedes validation.
     */
    private Arbitrary<SendOwnerEmailRequest> anyPayloads() {
        Arbitrary<RecipientScope> scopes = Arbitraries.of(RecipientScope.ALL, RecipientScope.SELECTED);
        Arbitrary<String> subjects = Arbitraries.oneOf(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(60),
                Arbitraries.of("", "   "));
        Arbitrary<String> bodies = Arbitraries.oneOf(
                Arbitraries.strings().alpha().withChars(' ').ofMinLength(1).ofMaxLength(120),
                Arbitraries.of("", "   "));
        Arbitrary<List<Long>> ownerIds = Arbitraries.longs().between(1, 10_000)
                .list().ofMinSize(0).ofMaxSize(4);

        return Combinators.combine(scopes, subjects, bodies, ownerIds)
                .as((scope, subject, body, ids) -> {
                    SendOwnerEmailRequest request = new SendOwnerEmailRequest();
                    request.setRecipientScope(scope);
                    request.setSubject(subject);
                    request.setBody(body);
                    request.setOwnerIds(ids);
                    return request;
                });
    }
}
