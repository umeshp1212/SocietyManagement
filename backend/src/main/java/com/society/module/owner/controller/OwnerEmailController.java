package com.society.module.owner.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.society.common.ApiResponse;
import com.society.exception.BusinessException;
import com.society.module.owner.dto.EmailJobDTO;
import com.society.module.owner.dto.SendOwnerEmailRequest;
import com.society.module.owner.service.OwnerEmailService;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * REST endpoint for sending templated emails to society owners.
 *
 * <p>The send operation is guarded so that only {@code SUPER_ADMIN} or callers
 * granted the {@code OWNER_EMAIL_SEND} authority may invoke it. Recipient
 * resolution, template assembly and the best-effort send are delegated to
 * {@link OwnerEmailService}, and the outcome is returned inside the shared
 * {@link ApiResponse} envelope.</p>
 */
@RestController
@RequestMapping("/owners/email")
@RequiredArgsConstructor
public class OwnerEmailController {

    private final OwnerEmailService ownerEmailService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    /**
     * Accepts an owner email from a plain JSON body (no attachments) and dispatches the
     * send to a background job.
     *
     * <p>Validation and template assembly happen synchronously, so an invalid request still
     * fails fast with a 400. On success the response is {@code 202 Accepted} carrying the
     * {@link EmailJobDTO} whose {@code jobId} the client polls via {@link #getJob(String)}.
     * Retained for backward compatibility with clients that post {@code application/json}.</p>
     */
    @PostMapping(value = "/send", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('OWNER_EMAIL_SEND')")
    public ResponseEntity<ApiResponse<EmailJobDTO>> sendOwnerEmail(
            @Valid @RequestBody SendOwnerEmailRequest request) {
        EmailJobDTO job = ownerEmailService.submitOwnerEmail(request, null);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Email send accepted", job));
    }

    /**
     * Accepts an owner email with optional file attachments via {@code multipart/form-data}
     * and dispatches the send to a background job.
     *
     * <p>The {@code request} part carries the JSON payload
     * ({@link SendOwnerEmailRequest}); the {@code attachments} part is optional and may
     * contain zero or more files. Attachment bytes are read before the response returns, so
     * the background send remains valid after the request completes. On success the response
     * is {@code 202 Accepted} carrying the pollable {@link EmailJobDTO}.</p>
     */
    @PostMapping(value = "/send", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('OWNER_EMAIL_SEND')")
    public ResponseEntity<ApiResponse<EmailJobDTO>> sendOwnerEmailWithAttachments(
            @RequestPart("request") MultipartFile requestPart,
            @RequestPart(value = "attachments", required = false) List<MultipartFile> attachments) {
        String requestJson;
        try {
            requestJson = new String(requestPart.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException("Invalid email request payload: " + e.getMessage());
        }
        SendOwnerEmailRequest request = parseAndValidate(requestJson);
        EmailJobDTO job = ownerEmailService.submitOwnerEmail(request, attachments);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Email send accepted", job));
    }

    /**
     * Returns the current state of a previously submitted send job so the client can poll
     * for progress and the final report.
     *
     * <p>Responds 404 when the job id is unknown or its result has expired from the
     * transient in-memory store.</p>
     */
    @GetMapping("/jobs/{jobId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('OWNER_EMAIL_SEND')")
    public ResponseEntity<ApiResponse<EmailJobDTO>> getJob(@PathVariable String jobId) {
        return ownerEmailService.getJob(jobId)
                .map(job -> ResponseEntity.ok(ApiResponse.success("Job status", job)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Email job not found or expired")));
    }

    /**
     * Deserialises the JSON request part and applies bean-validation, mirroring the
     * {@code @Valid @RequestBody} guarantees of the JSON endpoint so multipart requests
     * are held to the same subject/body/recipient-scope rules.
     */
    private SendOwnerEmailRequest parseAndValidate(String requestJson) {
        SendOwnerEmailRequest request;
        try {
            request = objectMapper.readValue(requestJson, SendOwnerEmailRequest.class);
        } catch (Exception e) {
            throw new BusinessException("Invalid email request payload: " + e.getMessage());
        }
        Set<ConstraintViolation<SendOwnerEmailRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String message = violations.iterator().next().getMessage();
            throw new BusinessException(message);
        }
        return request;
    }
}
