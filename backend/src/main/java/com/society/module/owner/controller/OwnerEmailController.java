package com.society.module.owner.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.society.common.ApiResponse;
import com.society.exception.BusinessException;
import com.society.module.owner.dto.SendOwnerEmailRequest;
import com.society.module.owner.dto.SendReportDTO;
import com.society.module.owner.service.OwnerEmailService;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
     * Sends an owner email from a plain JSON body (no attachments).
     *
     * <p>Retained for backward compatibility with clients that post
     * {@code application/json}.</p>
     */
    @PostMapping(value = "/send", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('OWNER_EMAIL_SEND')")
    public ResponseEntity<ApiResponse<SendReportDTO>> sendOwnerEmail(
            @Valid @RequestBody SendOwnerEmailRequest request) {
        SendReportDTO report = ownerEmailService.sendOwnerEmail(request);
        return ResponseEntity.ok(ApiResponse.success("Email send processed", report));
    }

    /**
     * Sends an owner email with optional file attachments via {@code multipart/form-data}.
     *
     * <p>The {@code request} part carries the JSON payload
     * ({@link SendOwnerEmailRequest}); the {@code attachments} part is optional and may
     * contain zero or more files. When no attachments are supplied the behaviour is
     * identical to the JSON endpoint.</p>
     */
    @PostMapping(value = "/send", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('OWNER_EMAIL_SEND')")
    public ResponseEntity<ApiResponse<SendReportDTO>> sendOwnerEmailWithAttachments(
            @RequestPart("request") MultipartFile requestPart,
            @RequestPart(value = "attachments", required = false) List<MultipartFile> attachments) {
        String requestJson;
        try {
            requestJson = new String(requestPart.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException("Invalid email request payload: " + e.getMessage());
        }
        SendOwnerEmailRequest request = parseAndValidate(requestJson);
        SendReportDTO report = ownerEmailService.sendOwnerEmail(request, attachments);
        return ResponseEntity.ok(ApiResponse.success("Email send processed", report));
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
