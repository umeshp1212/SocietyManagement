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
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Supporting property-based tests for {@link OwnerEmailController} on
 * {@code POST /owners/email/send} — Task 4 of the {@code owner-email-attachment-multipart-fix}
 * bugfix, "Property-based tests" slice of the design Testing Strategy.
 *
 * <p>These complement the scoped exploration property test
 * ({@link OwnerEmailMultipartBindingBugConditionPropertyTest}) and the preservation
 * property tests by exercising the fixed handler across a broad, generated input domain:</p>
 * <ul>
 *   <li><b>Fix Checking / Property 1</b> — random valid {@link SendOwnerEmailRequest}
 *       payloads sent as a typed multipart {@code request} part are deserialised,
 *       validated and dispatched, and the deserialised request handed to the service is
 *       equal to the one generated (2.1, 2.2, 2.3);</li>
 *   <li><b>Preservation / Property 2</b> — random valid payloads sent across BOTH
 *       representations (JSON body vs multipart typed part), with and without attachments,
 *       produce the same {@link SendOwnerEmailRequest} at the service boundary (3.1, 3.2,
 *       3.4);</li>
 *   <li>random attachment lists (including empty) are forwarded to the service exactly as
 *       the handler bound them (2.2).</li>
 * </ul>
 *
 * <p><b>Approach.</b> Mirrors the sibling standalone-MockMvc property tests: the real
 * controller is mounted with a real {@link ObjectMapper}/{@link jakarta.validation.Validator}
 * and the production {@link GlobalExceptionHandler}; {@link OwnerEmailService} is a mock
 * whose arguments are captured to assert on the deserialised request and the bound
 * attachment list. jqwik generators drive many payloads through {@code sampleStream()}
 * inside a single JUnit fixture, matching the project's other jqwik-based property tests.</p>
 *
 * <p><b>Validates: Requirements 2.1, 2.2, 2.3, 3.1, 3.2, 3.4</b></p>
 */
class OwnerEmailMultipartFixDispatchPropertyTest {

    private static final int TRIES = 120;

    private static final String SEND_PATH = "/owners/email/send";

    private final ObjectMapper objectMapper = new ObjectMapper();

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
    // Fix Checking / Property 1 — random valid payloads sent as a typed multipart part
    // are deserialised, validated, and dispatched with the identical request object.
    // ------------------------------------------------------------------

    // Feature: owner-email-attachment-multipart-fix, Property 1: typed multipart payloads are deserialised, validated and dispatched
    @Test
    void randomValidPayloadsSentAsTypedMultipartPartAreDeserialisedValidatedAndDispatched() throws Exception {
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
            when(ownerEmailService.sendOwnerEmail(any(SendOwnerEmailRequest.class), any()))
                    .thenReturn(stubReport());

            byte[] json = objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
            MockMultipartFile requestPart =
                    new MockMultipartFile("request", "request.json", "application/json", json);

            mockMvc.perform(multipart(SEND_PATH).file(requestPart))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            // The deserialised request handed to the service must match what was generated.
            ArgumentCaptor<SendOwnerEmailRequest> captor =
                    ArgumentCaptor.forClass(SendOwnerEmailRequest.class);
            verify(ownerEmailService, times(1)).sendOwnerEmail(captor.capture(), any());
            assertRequestEquals(payload, captor.getValue());
        }

        assertThat(sawAllScope).as("generator should exercise the ALL recipient scope").isTrue();
        assertThat(sawSelectedScope).as("generator should exercise the SELECTED recipient scope").isTrue();
    }

    // ------------------------------------------------------------------
    // Preservation / Property 2 — the same valid payload yields the identical
    // SendOwnerEmailRequest at the service boundary whether sent as a JSON body or as a
    // typed multipart part, with or without attachments.
    // ------------------------------------------------------------------

    // Feature: owner-email-attachment-multipart-fix, Property 2: Preservation - both representations produce an identical request
    @Test
    void jsonBodyAndTypedMultipartPartProduceIdenticalRequestAtTheServiceBoundary() throws Exception {
        OwnerEmailService ownerEmailService = mock(OwnerEmailService.class);
        MockMvc mockMvc = standaloneMockMvc(ownerEmailService);

        java.util.Iterator<SendOwnerEmailRequest> payloads = validPayloads().sampleStream().iterator();
        java.util.Iterator<Integer> attachmentCounts =
                Arbitraries.integers().between(0, 3).sampleStream().iterator();

        boolean sawWithAttachments = false;
        boolean sawWithoutAttachments = false;

        for (int i = 0; i < TRIES; i++) {
            SendOwnerEmailRequest payload = payloads.next();
            int attachmentCount = attachmentCounts.next();
            if (attachmentCount > 0) {
                sawWithAttachments = true;
            } else {
                sawWithoutAttachments = true;
            }

            byte[] json = objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);

            // --- JSON body representation -> single-arg overload ---
            reset(ownerEmailService);
            when(ownerEmailService.sendOwnerEmail(any(SendOwnerEmailRequest.class)))
                    .thenReturn(stubReport());

            mockMvc.perform(post(SEND_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            ArgumentCaptor<SendOwnerEmailRequest> jsonCaptor =
                    ArgumentCaptor.forClass(SendOwnerEmailRequest.class);
            verify(ownerEmailService, times(1)).sendOwnerEmail(jsonCaptor.capture());
            SendOwnerEmailRequest fromJson = jsonCaptor.getValue();

            // --- Multipart typed-part representation (with/without attachments) -> two-arg overload ---
            reset(ownerEmailService);
            when(ownerEmailService.sendOwnerEmail(any(SendOwnerEmailRequest.class), any()))
                    .thenReturn(stubReport());

            MockMultipartFile requestPart =
                    new MockMultipartFile("request", "request.json", "application/json", json);
            var builder = multipart(SEND_PATH).file(requestPart);
            for (int a = 0; a < attachmentCount; a++) {
                builder = builder.file(new MockMultipartFile(
                        "attachments", "doc-" + a + ".pdf", "application/pdf",
                        ("PDF-" + a).getBytes(StandardCharsets.UTF_8)));
            }

            mockMvc.perform(builder)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            ArgumentCaptor<SendOwnerEmailRequest> mpCaptor =
                    ArgumentCaptor.forClass(SendOwnerEmailRequest.class);
            verify(ownerEmailService, times(1)).sendOwnerEmail(mpCaptor.capture(), any());
            SendOwnerEmailRequest fromMultipart = mpCaptor.getValue();

            // Preservation: both representations must yield the identical request object.
            assertRequestEquals(fromJson, fromMultipart);
            assertRequestEquals(payload, fromMultipart);
        }

        assertThat(sawWithAttachments).as("generator should exercise multipart sends with attachments").isTrue();
        assertThat(sawWithoutAttachments).as("generator should exercise multipart sends without attachments").isTrue();
    }

    // ------------------------------------------------------------------
    // Random attachment lists (including empty) are forwarded to the service exactly.
    // ------------------------------------------------------------------

    // Feature: owner-email-attachment-multipart-fix, Property 1: the service receives the exact attachment list the handler bound
    @Test
    @SuppressWarnings("unchecked")
    void boundAttachmentListIsForwardedToTheServiceUnchanged() throws Exception {
        OwnerEmailService ownerEmailService = mock(OwnerEmailService.class);
        MockMvc mockMvc = standaloneMockMvc(ownerEmailService);

        SendOwnerEmailRequest payload = validPayload();
        byte[] json = objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);

        java.util.Iterator<List<String>> attachmentNameLists = attachmentNameLists().sampleStream().iterator();

        boolean sawEmpty = false;
        boolean sawNonEmpty = false;

        for (int i = 0; i < TRIES; i++) {
            List<String> names = attachmentNameLists.next();
            if (names.isEmpty()) {
                sawEmpty = true;
            } else {
                sawNonEmpty = true;
            }

            reset(ownerEmailService);
            when(ownerEmailService.sendOwnerEmail(any(SendOwnerEmailRequest.class), any()))
                    .thenReturn(stubReport());

            MockMultipartFile requestPart =
                    new MockMultipartFile("request", "request.json", "application/json", json);
            var builder = multipart(SEND_PATH).file(requestPart);
            for (String name : names) {
                builder = builder.file(new MockMultipartFile(
                        "attachments", name, "application/pdf",
                        ("CONTENT-" + name).getBytes(StandardCharsets.UTF_8)));
            }

            mockMvc.perform(builder)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            ArgumentCaptor<List<MultipartFile>> captor = ArgumentCaptor.forClass(List.class);
            verify(ownerEmailService, times(1)).sendOwnerEmail(any(SendOwnerEmailRequest.class), captor.capture());

            List<MultipartFile> forwarded = captor.getValue();
            if (names.isEmpty()) {
                // Spring supplies null for an absent optional multipart list.
                assertThat(forwarded == null || forwarded.isEmpty())
                        .as("no attachments -> service receives null or empty list").isTrue();
            } else {
                assertThat(forwarded).isNotNull();
                assertThat(forwarded).extracting(MultipartFile::getOriginalFilename)
                        .containsExactlyElementsOf(names);
            }
        }

        assertThat(sawEmpty).as("generator should exercise the empty attachment list").isTrue();
        assertThat(sawNonEmpty).as("generator should exercise non-empty attachment lists").isTrue();
    }

    // ------------------------------------------------------------------
    // Generators
    // ------------------------------------------------------------------

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
                    request.setOwnerIds(scope == RecipientScope.SELECTED ? ids : null);
                    return request;
                });
    }

    /** Random attachment file-name lists, including the empty list. */
    private Arbitrary<List<String>> attachmentNameLists() {
        Arbitrary<String> names = Arbitraries.strings().alpha().numeric()
                .ofMinLength(1).ofMaxLength(8).map(s -> s + ".pdf");
        return names.list().ofMinSize(0).ofMaxSize(4);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Field-wise equality on the parts of {@link SendOwnerEmailRequest} that carry over the wire. */
    private void assertRequestEquals(SendOwnerEmailRequest expected, SendOwnerEmailRequest actual) {
        assertThat(actual).isNotNull();
        assertThat(actual.getRecipientScope()).isEqualTo(expected.getRecipientScope());
        assertThat(actual.getSubject()).isEqualTo(expected.getSubject());
        assertThat(actual.getBody()).isEqualTo(expected.getBody());
        assertThat(normalise(actual.getOwnerIds())).isEqualTo(normalise(expected.getOwnerIds()));
    }

    /** Treat null and empty owner-id lists as equivalent, since JSON round-tripping may vary. */
    private List<Long> normalise(List<Long> ids) {
        return ids == null ? new ArrayList<>() : new ArrayList<>(ids);
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
                .notEmailed(new ArrayList<>())
                .build();
    }
}
