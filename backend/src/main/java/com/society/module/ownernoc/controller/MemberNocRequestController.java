package com.society.module.ownernoc.controller;

import com.society.common.ApiResponse;
import com.society.module.auth.security.JwtUtil;
import com.society.module.ownernoc.dto.NocTypeDTO;
import com.society.module.ownernoc.dto.OwnerNocRequestCreateRequest;
import com.society.module.ownernoc.dto.OwnerNocRequestDTO;
import com.society.module.ownernoc.service.NocTypeService;
import com.society.module.ownernoc.service.OwnerNocRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Member-portal endpoint for an owner to apply for a NOC and view their own
 * requests. The ownerId is taken from the authenticated member JWT.
 */
@RestController
@RequestMapping("/member/noc-requests")
@RequiredArgsConstructor
public class MemberNocRequestController {

    private final OwnerNocRequestService service;
    private final NocTypeService nocTypeService;
    private final JwtUtil jwtUtil;

    @PostMapping
    public ResponseEntity<ApiResponse<OwnerNocRequestDTO>> submit(
            @Valid @RequestBody OwnerNocRequestCreateRequest request,
            @RequestHeader("Authorization") String authHeader) {
        Long ownerId = extractOwnerId(authHeader);
        OwnerNocRequestDTO dto = service.submit(request, ownerId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "NOC request submitted. It will be issued once the society admin approves it.", dto));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<OwnerNocRequestDTO>>> myRequests(
            @RequestHeader("Authorization") String authHeader) {
        Long ownerId = extractOwnerId(authHeader);
        return ResponseEntity.ok(ApiResponse.success(service.getMyRequests(ownerId)));
    }

    /** Active NOC types for the member portal dropdown (member-token accessible). */
    @GetMapping("/types")
    public ResponseEntity<ApiResponse<List<NocTypeDTO>>> activeTypes() {
        return ResponseEntity.ok(ApiResponse.success(nocTypeService.getActiveTypes()));
    }

    /** Owner downloads their own approved NOC certificate PDF. */
    @GetMapping("/{requestId}/certificate")
    public ResponseEntity<byte[]> downloadMyCertificate(
            @PathVariable Long requestId,
            @RequestHeader("Authorization") String authHeader) {
        Long ownerId = extractOwnerId(authHeader);
        byte[] pdf = service.generateCertificatePdfForOwner(requestId, ownerId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"NOC-" + requestId + ".pdf\"")
                .body(pdf);
    }

    private Long extractOwnerId(String authHeader) {
        return jwtUtil.extractUserId(authHeader.replace("Bearer ", ""));
    }
}
