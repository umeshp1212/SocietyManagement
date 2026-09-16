package com.society.module.owner.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.society.exception.GlobalExceptionHandler;
import com.society.module.owner.dto.RecipientScope;
import com.society.module.owner.dto.SendOwnerEmailRequest;
import com.society.module.owner.dto.SendReportDTO;
import com.society.module.owner.service.OwnerEmailService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bug condition exploration test for {@link OwnerEmailController#sendOwnerEmailWithAttachments}
 * on {@code POST /owners/email/send} (multipart path) — Task 1.
 *
 * <p><b>Feature: owner-email-attachment-multipart-fix, Property 1: Bug Condition —
 * Multipart JSON Part Accepted Regardless of Content-Type.</b></p>
 *
 * <p>Bug condition (from design): {@code isBugCondition(input) = input.contentType =
 * MULTIPART_FORM_DATA AND input.requestPart.hasContentType = true}. The frontend appends
 * the JSON payload as a {@code Blob} typed {@code application/json}, so Spring's multipart
 * resolver classifies the {@code request} part as a {@link org.springframework.web.multipart.MultipartFile}
 * (file part) and cannot bind it to the handler's {@code @RequestParam("request") String}
 * parameter. Binding fails before any controller logic runs.</p>
 *
 * <p><b>Expected behavior (Property 1)</b>: for every input satisfying the bug condition,
 * the endpoint SHALL read the typed {@code request} part regardless of its Content-Type,
 * deserialise it into {@link SendOwnerEmailRequest}, bean-validate it, and — when valid —
 * dispatch via {@link OwnerEmailService#sendOwnerEmail(SendOwnerEmailRequest, List)},
 * returning the {@link SendReportDTO} inside the {@code ApiResponse} envelope with a
 * {@code 200 OK}. This test asserts that expected behavior.</p>
 *
 * <p><b>Status on UNFIXED code</b>: this test is EXPECTED TO FAIL. On the current
 * {@code @RequestParam("request") String} binding, each scoped case produces a
 * {@code 500 Internal Server Error} whose message is {@code Failed to convert value of
 * type '...StandardMultipartFile' to required type 'java.lang.String'}. That failure
 * confirms the bug exists and is the success case for this exploration step. The same
 * test will PASS once the binding is fixed in Task 3.1.</p>
 *
 * <p><b>Scoped PBT approach</b>: this is a deterministic binding defect, so the property
 * is scoped to the concrete failing cases — a typed {@code request} part
 * ({@code MockMultipartFile} carrying {@code application/json}) across attachment
 * variations (one, multiple, none) plus a curl-style {@code ;type=application/json}
 * reproduction. The {@link OwnerEmailService} is mocked so the assertions concern binding,
 * deserialisation, validation and dispatch delegation rather than real email sending.</p>
 *
 * <p><b>Validates: Requirements 1.1, 1.2, 1.3, 2.1, 2.2, 2.3</b></p>
 */
class OwnerEmailMultipartBindingBugConditionPropertyTest {

    private static final String SEND_PATH = "/owners/email/send";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * For every input where the bug condition holds (multipart request whose {@code request}
     * part carries a Content-Type), the endpoint must read, deserialise, validate and
     * dispatch the payload without a type-conversion error, returning the report in the
     * {@code ApiResponse} envelope.
     *
     * <p>The scoped input space is the cross product of {attachment variations} x {Blob-style
     * typed part, curl-style typed part}. Each case is a distinct counterexample against the
     * unfixed binding.</p>
     */
    // Feature: owner-email-attachment-multipart-fix, Property 1: Multipart JSON part accepted regardless of Content-Type
    @Test
    void typedRequestPartIsAcceptedRegardlessOfContentType() throws Exception {
        OwnerEmailService ownerEmailService = mock(OwnerEmailService.class);

        jakarta.validation.Validator beanValidator;
        try (jakarta.validation.ValidatorFactory validatorFactory =
                     jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            beanValidator = validatorFactory.getValidator();
        }

        OwnerEmailController controller =
                new OwnerEmailController(ownerEmailService, objectMapper, beanValidator);

        // Standalone MockMvc with the production advice, mirroring the sibling controller
        // property test. The generic exception handler maps the multipart type-conversion
        // failure to a 500, so on unfixed code the expected-behavior assertions below fail.
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // Enumerate the scoped bug-condition cases: attachment variations x typed-part styles.
        List<Case> cases = new ArrayList<>();
        for (int attachmentCount : new int[]{1, 2, 0}) {
            cases.add(new Case("Blob-style typed request part", "application/json", attachmentCount));
            // curl-style ";type=application/json" also gives the request part a Content-Type.
            cases.add(new Case("curl-style typed request part", "application/json", attachmentCount));
        }

        for (Case testCase : cases) {
            SendOwnerEmailRequest payload = validPayload();
            byte[] jsonBytes = objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);

            // The "request" part carries a Content-Type header -> Spring resolves it as a
            // MultipartFile (the bug condition), reproducing the frontend Blob / curl typed part.
            MockMultipartFile requestPart =
                    new MockMultipartFile("request", "request.json", testCase.requestContentType(), jsonBytes);

            var builder = multipart(SEND_PATH).file(requestPart);
            for (int i = 0; i < testCase.attachmentCount(); i++) {
                builder = builder.file(new MockMultipartFile(
                        "attachments",
                        "document-" + i + ".pdf",
                        "application/pdf",
                        ("PDF-CONTENT-" + i).getBytes(StandardCharsets.UTF_8)));
            }

            reset(ownerEmailService);
            when(ownerEmailService.sendOwnerEmail(any(SendOwnerEmailRequest.class), any()))
                    .thenReturn(stubReport());

            // --- Expected behavior (Property 1): typed part is read, deserialised, validated,
            //     and dispatched, returning 200 with the report envelope. ---
            // On UNFIXED code this fails: the request part binds as MultipartFile, the
            // @RequestParam("request") String conversion fails, and the response is a 500
            // with "Failed to convert value of type '...StandardMultipartFile' to required
            // type 'java.lang.String'".
            mockMvc.perform(builder)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            // Dispatch must occur with the deserialised request and the bound attachment list.
            verify(ownerEmailService, times(1)).sendOwnerEmail(any(SendOwnerEmailRequest.class), any());
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** A scoped bug-condition case: a typed request part plus a number of attachments. */
    private record Case(String label, String requestContentType, int attachmentCount) {
    }

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
