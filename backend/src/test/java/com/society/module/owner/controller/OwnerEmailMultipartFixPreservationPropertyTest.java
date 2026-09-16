package com.society.module.owner.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.society.exception.GlobalExceptionHandler;
import com.society.module.owner.dto.RecipientScope;
import com.society.module.owner.dto.SendOwnerEmailRequest;
import com.society.module.owner.dto.SendReportDTO;
import com.society.module.owner.service.OwnerEmailService;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Preservation property tests for {@link OwnerEmailController} on
 * {@code POST /owners/email/send} — Task 2 of the {@code owner-email-attachment-multipart-fix}
 * bugfix.
 *
 * <p><b>Feature: owner-email-attachment-multipart-fix, Property 2: Preservation —
 * Non-Buggy Inputs Behave Identically.</b> For any input where the bug condition does NOT
 * hold ({@code isBugCondition(input) = false} — the {@code application/json} path,
 * unauthorised callers, no-attachment multipart requests, and payloads that fail
 * deserialisation or bean-validation), the fixed handler MUST produce the same result as
 * the original. This class captures the JSON-path dispatch and the validation-messaging
 * baselines that must be preserved; the security-guard rejection preservation lives in
 * {@link OwnerEmailMultipartFixSecurityPreservationTest} (it needs the full method-security
 * context) and the multipart no-attachment path is covered by the Task 3 fix-checking
 * tests (on unfixed code it still satisfies the bug condition and 500s).</p>
 *
 * <p><b>Observation-first methodology.</b> These properties were observed on the UNFIXED
 * controller and PASS there, establishing the baseline behaviour to preserve. Task 3.3
 * re-runs the exact same class against the fixed controller to prove no regression.</p>
 *
 * <p><b>Observed baseline (unfixed code):</b></p>
 * <ul>
 *   <li>3.1 — a valid {@code application/json} payload deserialises, {@code @Valid}-validates,
 *       and dispatches via {@link OwnerEmailService#sendOwnerEmail(SendOwnerEmailRequest)},
 *       returning {@code 200 OK} with {@code {"success": true}} wrapping the
 *       {@link SendReportDTO}.</li>
 *   <li>3.5 — an invalid subject/body/recipient-scope payload is rejected on the JSON path
 *       with {@code 400 Bad Request}; the {@code MethodArgumentNotValidException} advice
 *       renders {@code {"success": false, "message": "Validation failed",
 *       "data": {"&lt;field&gt;": "&lt;first-violation message&gt;"}}}. The exact per-field
 *       messages are: {@code recipientScope -> "Recipient scope is required"},
 *       {@code subject -> "Subject is required"} / {@code "Subject must not exceed 200
 *       characters"}, {@code body -> "Body is required"} / {@code "Body must not exceed
 *       50000 characters"}. The service is never invoked for an invalid payload.</li>
 * </ul>
 *
 * <p><b>Approach.</b> Mirrors the sibling {@link OwnerEmailValidationBoundaryPropertyTest}:
 * the real {@link OwnerEmailController} is mounted on a standalone {@link MockMvc} with the
 * production {@link GlobalExceptionHandler}. {@link OwnerEmailService} is a mock so the
 * properties assert on deserialisation, validation and dispatch delegation rather than a
 * real send. jqwik generators drive many payloads through {@code sampleStream()} inside a
 * single JUnit fixture, matching the project's other jqwik-based property tests. The JSON
 * path is not touched by the fix, so these properties are true baselines: they hold before
 * and after Task 3.1.</p>
 *
 * <p><b>Validates: Requirements 3.1, 3.2, 3.4, 3.5</b> (3.3 in the sibling security test).</p>
 */
class OwnerEmailMultipartFixPreservationPropertyTest {

    private static final int TRIES = 200;

    private static final String SEND_PATH = "/owners/email/send";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Builds a standalone MockMvc over the real controller with real ObjectMapper/Validator
     * collaborators and the production exception advice, mirroring the sibling PBT setup.
     */
    private MockMvc standaloneMockMvc(OwnerEmailService ownerEmailService) {
        jakarta.validation.Validator beanValidator;
        try (jakarta.validation.ValidatorFactory validatorFactory =
                     jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            beanValidator = validatorFactory.getValidator();
        }
        OwnerEmailController controller =
                new OwnerEmailController(ownerEmailService, objectMapper, beanValidator);
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ------------------------------------------------------------------
    // 3.1 — JSON-path preservation: valid payloads deserialise, validate and dispatch.
    // ------------------------------------------------------------------

    /**
     * For every generated valid {@link SendOwnerEmailRequest} sent as an
     * {@code application/json} body, the JSON endpoint deserialises it, passes
     * {@code @Valid} validation, and dispatches via
     * {@link OwnerEmailService#sendOwnerEmail(SendOwnerEmailRequest)}, returning {@code 200}
     * with {@code success == true}. This is the JSON no-attachment path baseline (3.1, 3.2).
     */
    // Feature: owner-email-attachment-multipart-fix, Property 2: Preservation - JSON path deserialises, validates and dispatches unchanged
    @Test
    void validJsonPayloadIsDeserialisedValidatedAndDispatched() throws Exception {
        OwnerEmailService ownerEmailService = mock(OwnerEmailService.class);
        MockMvc mockMvc = standaloneMockMvc(ownerEmailService);

        java.util.Iterator<SendOwnerEmailRequest> payloads = validPayloads().sampleStream().iterator();

        boolean sawAllScope = false;
        boolean sawSelectedScope = false;

        for (int i = 0; i < TRIES; i++) {
            SendOwnerEmailRequest payload = payloads.next();

            if (payload.getRecipientScope() == RecipientScope.ALL) {
                sawAllScope = true;
            } else {
                sawSelectedScope = true;
            }

            reset(ownerEmailService);
            when(ownerEmailService.sendOwnerEmail(any(SendOwnerEmailRequest.class)))
                    .thenReturn(stubReport());

            // --- Property (3.1): a valid JSON payload is accepted and dispatched via the
            //     single-arg service overload, returning 200 with the report envelope. ---
            mockMvc.perform(post(SEND_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            // Dispatch must go through the JSON (single-argument) service method exactly once.
            verify(ownerEmailService, times(1)).sendOwnerEmail(any(SendOwnerEmailRequest.class));
        }

        assertThat(sawAllScope).as("generator should exercise the ALL recipient scope").isTrue();
        assertThat(sawSelectedScope).as("generator should exercise the SELECTED recipient scope").isTrue();
    }

    // ------------------------------------------------------------------
    // 3.5 — Validation-messaging preservation: the first-violation message surfaces
    //       identically on the JSON path and the service is never invoked.
    // ------------------------------------------------------------------

    /**
     * For every generated payload carrying exactly one invalid field, the JSON endpoint
     * rejects the request with {@code 400}, {@code success == false}, and the exact
     * constraint-violation message for that field surfaces under {@code $.data.<field>}
     * (the {@code MethodArgumentNotValidException} advice shape). The service is never
     * invoked. This pins the validation-messaging baseline to preserve (3.5).
     */
    // Feature: owner-email-attachment-multipart-fix, Property 2: Preservation - first constraint-violation message is unchanged
    @Test
    void invalidPayloadSurfacesTheSameFirstViolationMessageAndNeverDispatches() throws Exception {
        OwnerEmailService ownerEmailService = mock(OwnerEmailService.class);
        MockMvc mockMvc = standaloneMockMvc(ownerEmailService);

        java.util.Iterator<Violation> violations = violations().sampleStream().iterator();

        boolean sawMissingScope = false;
        boolean sawMissingSubject = false;
        boolean sawOverLengthSubject = false;
        boolean sawMissingBody = false;
        boolean sawOverLengthBody = false;

        for (int i = 0; i < TRIES; i++) {
            Violation violation = violations.next();

            switch (violation.kind()) {
                case MISSING_SCOPE -> sawMissingScope = true;
                case MISSING_SUBJECT -> sawMissingSubject = true;
                case OVER_LENGTH_SUBJECT -> sawOverLengthSubject = true;
                case MISSING_BODY -> sawMissingBody = true;
                case OVER_LENGTH_BODY -> sawOverLengthBody = true;
            }

            reset(ownerEmailService);

            // --- Property (3.5): the invalid payload is rejected with the exact per-field
            //     first-violation message, and no send is initiated. ---
            mockMvc.perform(post(SEND_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(violation.body())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Validation failed"))
                    .andExpect(jsonPath("$.data." + violation.field()).value(violation.message()));

            verifyNoInteractions(ownerEmailService);
        }

        assertThat(sawMissingScope).as("generator should exercise a missing recipient scope").isTrue();
        assertThat(sawMissingSubject).as("generator should exercise a missing subject").isTrue();
        assertThat(sawOverLengthSubject).as("generator should exercise an over-length subject").isTrue();
        assertThat(sawMissingBody).as("generator should exercise a missing body").isTrue();
        assertThat(sawOverLengthBody).as("generator should exercise an over-length body").isTrue();
    }

    // ------------------------------------------------------------------
    // Generators
    // ------------------------------------------------------------------

    /**
     * Generates fully valid {@link SendOwnerEmailRequest} payloads across both recipient
     * scopes with subject/body inside their {@code @Size} bounds. These are the non-buggy
     * inputs whose JSON-path dispatch must be preserved.
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

    /** The single-field violation exercised by a trial and its expected message/field. */
    private enum ViolationKind {
        MISSING_SCOPE, MISSING_SUBJECT, OVER_LENGTH_SUBJECT, MISSING_BODY, OVER_LENGTH_BODY
    }

    /**
     * A generated invalid payload with exactly one violated field, together with the field
     * name and exact message the {@code @Valid} boundary is expected to surface.
     */
    private record Violation(SendOwnerEmailRequest body, ViolationKind kind, String field, String message) {
    }

    /**
     * Generates payloads that violate exactly one constraint, so the expected first-violation
     * message is deterministic per trial. Every other field is valid. The messages mirror the
     * constraints declared on {@link SendOwnerEmailRequest}.
     */
    private Arbitrary<Violation> violations() {
        Arbitrary<String> validSubject = Arbitraries.strings().alpha().numeric().withChars(' ')
                .ofMinLength(1).ofMaxLength(200).filter(s -> !s.isBlank());
        Arbitrary<String> validBody = Arbitraries.strings().alpha().numeric().withChars(' ')
                .ofMinLength(1).ofMaxLength(400).filter(s -> !s.isBlank());
        Arbitrary<String> blankSubjectOrBody = Arbitraries.of("", " ", "   ", "\t", "\n");

        Arbitrary<ViolationKind> kinds = Arbitraries.of(ViolationKind.values());

        return Combinators.combine(kinds, validSubject, validBody, blankSubjectOrBody)
                .as((kind, subject, body, blank) -> {
                    SendOwnerEmailRequest request = new SendOwnerEmailRequest();
                    // Start from a fully valid request, then break exactly one field.
                    request.setRecipientScope(RecipientScope.ALL);
                    request.setSubject(subject);
                    request.setBody(body);

                    return switch (kind) {
                        case MISSING_SCOPE -> {
                            request.setRecipientScope(null);
                            yield new Violation(request, kind, "recipientScope", "Recipient scope is required");
                        }
                        case MISSING_SUBJECT -> {
                            request.setSubject(blank);
                            yield new Violation(request, kind, "subject", "Subject is required");
                        }
                        case OVER_LENGTH_SUBJECT -> {
                            request.setSubject("A".repeat(201));
                            yield new Violation(request, kind, "subject", "Subject must not exceed 200 characters");
                        }
                        case MISSING_BODY -> {
                            request.setBody(blank);
                            yield new Violation(request, kind, "body", "Body is required");
                        }
                        case OVER_LENGTH_BODY -> {
                            request.setBody("A".repeat(50_001));
                            yield new Violation(request, kind, "body", "Body must not exceed 50000 characters");
                        }
                    };
                });
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private SendReportDTO stubReport() {
        return SendReportDTO.builder()
                .totalAttempted(0)
                .sentCount(0)
                .notEmailedCount(0)
                .mailConfigured(false)
                .notEmailed(new ArrayList<>())
                .build();
    }
}
