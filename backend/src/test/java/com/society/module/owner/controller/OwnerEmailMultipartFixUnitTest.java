package com.society.module.owner.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.society.exception.GlobalExceptionHandler;
import com.society.module.owner.dto.RecipientScope;
import com.society.module.owner.dto.SendOwnerEmailRequest;
import com.society.module.owner.dto.SendReportDTO;
import com.society.module.owner.service.OwnerEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Supporting example-based unit tests for {@link OwnerEmailController} on
 * {@code POST /owners/email/send} — Task 4 of the {@code owner-email-attachment-multipart-fix}
 * bugfix, "Unit tests" slice of the design Testing Strategy.
 *
 * <p>These complement (and do not duplicate) the exploration test
 * ({@link OwnerEmailMultipartBindingBugConditionPropertyTest}) and the preservation
 * property tests ({@link OwnerEmailMultipartFixPreservationPropertyTest},
 * {@link OwnerEmailMultipartFixSecurityPreservationTest}). Here we pin the concrete
 * example cases enumerated in the design:</p>
 * <ul>
 *   <li>multipart binding accepts a {@code request} part carrying
 *       {@code Content-Type: application/json} (the frontend {@code Blob} shape) and
 *       reaches {@code parseAndValidate}, dispatching the send (2.1, 2.2);</li>
 *   <li>multipart binding accepts a {@code request} part WITHOUT a Content-Type header
 *       (plain form-field style) — proving the fix does not regress the untyped case (2.1);</li>
 *   <li>malformed JSON in the {@code request} part yields
 *       {@code BusinessException("Invalid email request payload: ...")} rendered as a
 *       {@code 400} rather than a multipart type-conversion {@code 500} (2.3);</li>
 *   <li>a bean-validation failure in the {@code request} part yields the first
 *       constraint-violation message (2.3);</li>
 *   <li>the JSON-only endpoint continues to work with {@code @Valid @RequestBody} (3.1).</li>
 * </ul>
 *
 * <p><b>Approach.</b> Mirrors the sibling standalone-MockMvc tests: the real controller is
 * mounted with a real {@link ObjectMapper}/{@link jakarta.validation.Validator} and the
 * production {@link GlobalExceptionHandler}; {@link OwnerEmailService} is a mock so the
 * assertions concern binding, deserialisation, validation and dispatch delegation rather
 * than a real send. The method-security guard is a separate concern covered by the
 * integration test, so it is intentionally not exercised here.</p>
 *
 * <p><b>Validates: Requirements 2.1, 2.2, 2.3, 3.1</b></p>
 */
class OwnerEmailMultipartFixUnitTest {

    private static final String SEND_PATH = "/owners/email/send";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OwnerEmailService ownerEmailService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ownerEmailService = mock(OwnerEmailService.class);

        jakarta.validation.Validator beanValidator;
        try (jakarta.validation.ValidatorFactory validatorFactory =
                     jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            beanValidator = validatorFactory.getValidator();
        }

        OwnerEmailController controller =
                new OwnerEmailController(ownerEmailService, objectMapper, beanValidator);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ------------------------------------------------------------------
    // 2.1, 2.2 — typed request part (Content-Type: application/json) reaches parseAndValidate.
    // ------------------------------------------------------------------

    // Feature: owner-email-attachment-multipart-fix, Property 1: typed request part reaches parseAndValidate and dispatches
    @Test
    void multipartRequestPartWithJsonContentTypeReachesParseAndValidateAndDispatches() throws Exception {
        when(ownerEmailService.sendOwnerEmail(any(SendOwnerEmailRequest.class), any()))
                .thenReturn(stubReport());

        byte[] json = objectMapper.writeValueAsString(validPayload()).getBytes(StandardCharsets.UTF_8);
        // Content-Type set -> Spring resolves the part as a MultipartFile (the frontend Blob shape).
        MockMultipartFile requestPart =
                new MockMultipartFile("request", "request.json", "application/json", json);

        mockMvc.perform(multipart(SEND_PATH).file(requestPart))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Reaching parseAndValidate and dispatching proves the typed part bound successfully.
        verify(ownerEmailService, times(1)).sendOwnerEmail(any(SendOwnerEmailRequest.class), any());
    }

    // ------------------------------------------------------------------
    // 2.1 — untyped request part (no Content-Type) still binds (no regression).
    // ------------------------------------------------------------------

    // Feature: owner-email-attachment-multipart-fix, Property 1: untyped request part still binds after the fix
    @Test
    void multipartRequestPartWithoutContentTypeStillReachesParseAndValidate() throws Exception {
        when(ownerEmailService.sendOwnerEmail(any(SendOwnerEmailRequest.class), any()))
                .thenReturn(stubReport());

        byte[] json = objectMapper.writeValueAsString(validPayload()).getBytes(StandardCharsets.UTF_8);
        // Null content type -> plain form-field style part; the fix must not regress this case.
        MockMultipartFile requestPart =
                new MockMultipartFile("request", "request.json", null, json);

        mockMvc.perform(multipart(SEND_PATH).file(requestPart))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(ownerEmailService, times(1)).sendOwnerEmail(any(SendOwnerEmailRequest.class), any());
    }

    // ------------------------------------------------------------------
    // 2.3 — malformed JSON -> BusinessException("Invalid email request payload: ...") -> 400.
    // ------------------------------------------------------------------

    // Feature: owner-email-attachment-multipart-fix, Property 1: malformed JSON surfaces a business error, not a conversion 500
    @Test
    void malformedJsonRequestPartYieldsInvalidPayloadBusinessException() throws Exception {
        byte[] malformed = "{ this is not valid json ".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile requestPart =
                new MockMultipartFile("request", "request.json", "application/json", malformed);

        mockMvc.perform(multipart(SEND_PATH).file(requestPart))
                // BusinessException is mapped to 400 by the GlobalExceptionHandler, NOT a
                // multipart type-conversion 500.
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.startsWith("Invalid email request payload:")));

        // Deserialisation failed before any send could be dispatched.
        verifyNoInteractions(ownerEmailService);
    }

    // ------------------------------------------------------------------
    // 2.3 — bean-validation failure -> first constraint-violation message.
    // ------------------------------------------------------------------

    // Feature: owner-email-attachment-multipart-fix, Property 1: bean-validation failure surfaces the first constraint-violation message
    @Test
    void beanValidationFailureInRequestPartYieldsFirstConstraintViolationMessage() throws Exception {
        // Valid JSON but a missing subject -> @NotBlank violation surfaced by parseAndValidate.
        SendOwnerEmailRequest invalid = new SendOwnerEmailRequest();
        invalid.setRecipientScope(RecipientScope.ALL);
        invalid.setSubject("");
        invalid.setBody("A body that is present and valid.");
        byte[] json = objectMapper.writeValueAsString(invalid).getBytes(StandardCharsets.UTF_8);

        MockMultipartFile requestPart =
                new MockMultipartFile("request", "request.json", "application/json", json);

        mockMvc.perform(multipart(SEND_PATH).file(requestPart))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                // parseAndValidate throws BusinessException with the first violation message.
                .andExpect(jsonPath("$.message").value("Subject is required"));

        verifyNoInteractions(ownerEmailService);
    }

    // ------------------------------------------------------------------
    // 3.1 — JSON-only endpoint continues to work with @Valid @RequestBody.
    // ------------------------------------------------------------------

    // Feature: owner-email-attachment-multipart-fix, Property 2: Preservation - JSON endpoint still works with @Valid @RequestBody
    @Test
    void jsonOnlyEndpointStillWorksWithValidRequestBody() throws Exception {
        when(ownerEmailService.sendOwnerEmail(any(SendOwnerEmailRequest.class)))
                .thenReturn(stubReport());

        mockMvc.perform(post(SEND_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validPayload())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // The JSON path dispatches through the single-argument (no-attachment) overload.
        verify(ownerEmailService, times(1)).sendOwnerEmail(any(SendOwnerEmailRequest.class));
        verify(ownerEmailService, times(0))
                .sendOwnerEmail(any(SendOwnerEmailRequest.class), any());
    }

    // ------------------------------------------------------------------
    // Extra example: the bound attachment list is the one handed to the service (2.2).
    // ------------------------------------------------------------------

    // Feature: owner-email-attachment-multipart-fix, Property 1: attachments bound on the multipart path reach the service
    @Test
    @SuppressWarnings("unchecked")
    void multipartAttachmentsAreForwardedToTheService() throws Exception {
        when(ownerEmailService.sendOwnerEmail(any(SendOwnerEmailRequest.class), any()))
                .thenReturn(stubReport());

        byte[] json = objectMapper.writeValueAsString(validPayload()).getBytes(StandardCharsets.UTF_8);
        MockMultipartFile requestPart =
                new MockMultipartFile("request", "request.json", "application/json", json);
        MockMultipartFile fileA =
                new MockMultipartFile("attachments", "a.pdf", "application/pdf", "A".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile fileB =
                new MockMultipartFile("attachments", "b.pdf", "application/pdf", "B".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart(SEND_PATH).file(requestPart).file(fileA).file(fileB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        org.mockito.ArgumentCaptor<List<MultipartFile>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(ownerEmailService).sendOwnerEmail(any(SendOwnerEmailRequest.class), captor.capture());

        List<MultipartFile> forwarded = captor.getValue();
        assertThat(forwarded).hasSize(2);
        assertThat(forwarded).extracting(MultipartFile::getOriginalFilename)
                .containsExactly("a.pdf", "b.pdf");
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
