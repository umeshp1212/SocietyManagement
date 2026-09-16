package com.society.module.owner.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.society.exception.GlobalExceptionHandler;
import com.society.module.owner.dto.RecipientScope;
import com.society.module.owner.dto.SendOwnerEmailRequest;
import com.society.module.owner.service.OwnerEmailService;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Property test for the {@link OwnerEmailController} {@code @Valid} request-body
 * validation boundary on {@code POST /owners/email/send} (Subtask 4.2).
 *
 * <p><b>Feature: owner-email, Property 12: Server-side validation rejects invalid
 * subject/body without side effects.</b> For any subject that is empty/whitespace or
 * exceeds 200 characters, or any body that is empty/whitespace or exceeds 10,000
 * characters, the controller SHALL reject the request with a validation error and SHALL
 * NOT initiate any send (no partial record) — i.e. {@link OwnerEmailService} is never
 * invoked.</p>
 *
 * <p><b>Validates: Requirements 3.5, 3.6</b></p>
 *
 * <p><b>Approach.</b> Per the design Testing Strategy ("Property 12: controller/
 * validation boundary via generated invalid subjects/bodies, asserting the service is
 * never invoked"), the real {@link OwnerEmailController} is mounted on a standalone
 * {@link MockMvc} together with the application's {@link GlobalExceptionHandler}. Bean
 * validation on the {@code @Valid @RequestBody SendOwnerEmailRequest} therefore runs
 * exactly as in production: a violating request raises
 * {@code MethodArgumentNotValidException}, which the advice maps to a {@code 400 Bad
 * Request} with the shared {@code ApiResponse} envelope ({@code success == false}). The
 * {@link OwnerEmailService} collaborator is a mock; the test asserts it is never touched
 * for any invalid request, proving no send is initiated and no partial record is
 * created. The method-level {@code @PreAuthorize} guard is a separate concern covered by
 * the security integration test and is intentionally not exercised here so this property
 * isolates the validation boundary.</p>
 *
 * <p>Generation is constrained to the invalid input space and covers every rejection
 * path required by the task: empty and whitespace-only subjects, subjects longer than
 * 200 characters, empty and whitespace-only bodies, and bodies longer than 10,000
 * characters, in combinations where at least one of subject/body is invalid while the
 * other is either valid or also invalid. As a companion soundness check, each trial also
 * confirms that a fully valid subject/body pair does reach the service, so the property
 * isolates the invalid case rather than rejecting every request. The generation loop
 * runs inside a single JUnit {@code @Test} via jqwik's {@code sampleStream()}
 * (&gt;= 100 tries), matching the project's other jqwik-based property tests.</p>
 */
class OwnerEmailValidationBoundaryPropertyTest {

    private static final int TRIES = 200;

    private static final String SEND_PATH = "/owners/email/send";

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Feature: owner-email, Property 12: Server-side validation rejects invalid subject/body without side effects
    @Test
    void invalidSubjectOrBodyIsRejectedAndServiceIsNeverInvoked() throws Exception {
        // sampleStream() draws values outside jqwik's own @Property lifecycle so the
        // generation loop runs inside a single JUnit fixture, matching the sibling tests.
        java.util.Iterator<Scenario> scenarios = scenarios().sampleStream().iterator();

        // The whole point of the property: this collaborator must never be reached when
        // the request body is invalid (no send initiated, no partial record).
        OwnerEmailService ownerEmailService = mock(OwnerEmailService.class);
        // The controller also collaborates with an ObjectMapper and a bean Validator for
        // the multipart send path. That path is not exercised here (this property isolates
        // the @Valid @RequestBody JSON boundary), so real, self-contained instances suffice.
        jakarta.validation.Validator beanValidator;
        try (jakarta.validation.ValidatorFactory validatorFactory =
                     jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            beanValidator = validatorFactory.getValidator();
        }
        OwnerEmailController controller =
                new OwnerEmailController(ownerEmailService, objectMapper, beanValidator);

        // Standalone MockMvc with the production validation advice. This drives the real
        // @Valid @RequestBody handling and the GlobalExceptionHandler mapping, without the
        // method-security guard (a separate concern covered by the security IT).
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // Coverage guards: ensure every generated rejection path was actually exercised.
        boolean sawEmptySubject = false;
        boolean sawBlankSubject = false;
        boolean sawOverLengthSubject = false;
        boolean sawEmptyBody = false;
        boolean sawBlankBody = false;
        boolean sawOverLengthBody = false;

        for (int i = 0; i < TRIES; i++) {
            Scenario scenario = scenarios.next();

            switch (scenario.subjectKind()) {
                case EMPTY -> sawEmptySubject = true;
                case BLANK -> sawBlankSubject = true;
                case OVER_LENGTH -> sawOverLengthSubject = true;
                case VALID -> { /* valid subject; body carries the violation */ }
            }
            switch (scenario.bodyKind()) {
                case EMPTY -> sawEmptyBody = true;
                case BLANK -> sawBlankBody = true;
                case OVER_LENGTH -> sawOverLengthBody = true;
                case VALID -> { /* valid body; subject carries the violation */ }
            }

            // Generator invariant: at least one of subject/body is invalid.
            assertThat(scenario.subjectKind() != FieldKind.VALID
                    || scenario.bodyKind() != FieldKind.VALID)
                    .as("trial %d: generated scenario must carry at least one violation", i)
                    .isTrue();

            SendOwnerEmailRequest request = new SendOwnerEmailRequest();
            request.setRecipientScope(RecipientScope.ALL);
            request.setSubject(scenario.subject());
            request.setBody(scenario.body());

            reset(ownerEmailService);

            // --- The property: an invalid subject/body must be rejected with a validation error ---
            mockMvc.perform(post(SEND_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));

            // --- ...and no send is initiated, so no partial record is created (Req 3.5, 3.6) ---
            verifyNoInteractions(ownerEmailService);
        }

        // --- Soundness: a fully valid subject/body pair must reach the service ---
        // This proves the boundary isolates the invalid case rather than rejecting every
        // request. Uses the same standalone pipeline; the service returns a stub report.
        reset(ownerEmailService);
        org.mockito.Mockito.when(ownerEmailService.sendOwnerEmail(org.mockito.ArgumentMatchers.any()))
                .thenReturn(com.society.module.owner.dto.SendReportDTO.builder()
                        .totalAttempted(0)
                        .sentCount(0)
                        .notEmailedCount(0)
                        .mailConfigured(false)
                        .notEmailed(List.of())
                        .build());

        SendOwnerEmailRequest valid = new SendOwnerEmailRequest();
        valid.setRecipientScope(RecipientScope.ALL);
        valid.setSubject("Society Notice");
        valid.setBody("Please note the upcoming general body meeting this weekend.");
        mockMvc.perform(post(SEND_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(valid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        org.mockito.Mockito.verify(ownerEmailService).sendOwnerEmail(org.mockito.ArgumentMatchers.any());

        // Coverage assertions: every rejection path in the task was exercised.
        assertThat(sawEmptySubject).as("generator should exercise an empty subject").isTrue();
        assertThat(sawBlankSubject).as("generator should exercise a whitespace-only subject").isTrue();
        assertThat(sawOverLengthSubject).as("generator should exercise a subject over 200 chars").isTrue();
        assertThat(sawEmptyBody).as("generator should exercise an empty body").isTrue();
        assertThat(sawBlankBody).as("generator should exercise a whitespace-only body").isTrue();
        assertThat(sawOverLengthBody).as("generator should exercise a body over 10000 chars").isTrue();
    }

    // ------------------------------------------------------------------
    // Generators
    // ------------------------------------------------------------------

    /** How a single field (subject or body) is generated for a trial. */
    private enum FieldKind { VALID, EMPTY, BLANK, OVER_LENGTH }

    /**
     * A generated trial: the concrete subject/body strings plus the kind of each, where at
     * least one field is invalid so the {@code @Valid} boundary must reject the request.
     */
    private record Scenario(String subject, FieldKind subjectKind, String body, FieldKind bodyKind) {
    }

    /**
     * Generates scenarios in the invalid input space. Each of subject and body is drawn as
     * VALID, EMPTY (""), BLANK (whitespace-only) or OVER_LENGTH (> the {@code @Size} max),
     * then combinations are filtered so at least one field is invalid — the precondition of
     * the property. Subject bounds: {@code @NotBlank @Size(max=200)}; body bounds:
     * {@code @NotBlank @Size(max=10000)}.
     */
    private Arbitrary<Scenario> scenarios() {
        Arbitrary<Object[]> subjects = fieldOf(200);
        Arbitrary<Object[]> bodies = fieldOf(10_000);

        return Combinators.combine(subjects, bodies)
                .as((s, b) -> new Scenario(
                        (String) s[0], (FieldKind) s[1],
                        (String) b[0], (FieldKind) b[1]))
                // Keep only scenarios where at least one field is invalid.
                .filter(sc -> sc.subjectKind() != FieldKind.VALID || sc.bodyKind() != FieldKind.VALID);
    }

    /**
     * Generates a single field as one of the four kinds for a {@code @NotBlank
     * @Size(max=maxLen)} constraint, returning {@code [value, FieldKind]}.
     */
    private Arbitrary<Object[]> fieldOf(int maxLen) {
        // A valid, non-blank value within the length bound.
        Arbitrary<Object[]> valid = Arbitraries.strings().alpha().numeric().withChars(' ')
                .ofMinLength(1).ofMaxLength(Math.min(maxLen, 40))
                .filter(s -> !s.isBlank())
                .map(s -> new Object[]{s, FieldKind.VALID});

        // Empty string -> violates @NotBlank.
        Arbitrary<Object[]> empty = Arbitraries.just(new Object[]{"", FieldKind.EMPTY});

        // Whitespace-only -> violates @NotBlank.
        Arbitrary<Object[]> blank = Arbitraries.of(" ", "   ", "\t", "\n", " \t \n ")
                .map(s -> new Object[]{s, FieldKind.BLANK});

        // Longer than maxLen -> violates @Size(max=maxLen).
        Arbitrary<Object[]> overLength = Arbitraries.integers().between(maxLen + 1, maxLen + 50)
                .map(len -> new Object[]{"A".repeat(len), FieldKind.OVER_LENGTH});

        return Arbitraries.oneOf(valid, empty, blank, overLength);
    }
}
