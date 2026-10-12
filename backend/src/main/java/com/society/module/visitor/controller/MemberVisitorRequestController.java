package com.society.module.visitor.controller;

import com.society.common.ApiResponse;
import com.society.module.auth.security.JwtUtil;
import com.society.module.visitor.dto.VisitorRejectRequest;
import com.society.module.visitor.dto.VisitorRequestDTO;
import com.society.module.visitor.service.VisitorRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Member-portal endpoints for an owner to see and respond to pending visitor/delivery
 * requests for their flats. The ownerId is taken from the authenticated member JWT
 * (its {@code userId} claim carries the owner id, as elsewhere in the member path).
 *
 * <p>First-wins and ownership are enforced in {@link VisitorRequestService}: an owner
 * can only decide requests for a flat they own, and a request that is already decided
 * or timed out cannot be changed.</p>
 */
@RestController
@RequestMapping("/member/visitor-requests")
@RequiredArgsConstructor
public class MemberVisitorRequestController {

    private final VisitorRequestService service;
    private final JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<ApiResponse<List<VisitorRequestDTO>>> pending(
            @RequestHeader("Authorization") String authHeader) {
        Long ownerId = extractOwnerId(authHeader);
        return ResponseEntity.ok(ApiResponse.success(service.getPendingForOwner(ownerId)));
    }

    @PostMapping("/{requestId}/approve")
    public ResponseEntity<ApiResponse<VisitorRequestDTO>> approve(
            @PathVariable Long requestId,
            @RequestHeader("Authorization") String authHeader) {
        Long ownerId = extractOwnerId(authHeader);
        String decidedBy = extractUsername(authHeader);
        VisitorRequestDTO dto = service.approve(requestId, ownerId, decidedBy);
        return ResponseEntity.ok(ApiResponse.success("Entry approved.", dto));
    }

    @PostMapping("/{requestId}/reject")
    public ResponseEntity<ApiResponse<VisitorRequestDTO>> reject(
            @PathVariable Long requestId,
            @RequestBody(required = false) VisitorRejectRequest body,
            @RequestHeader("Authorization") String authHeader) {
        Long ownerId = extractOwnerId(authHeader);
        String decidedBy = extractUsername(authHeader);
        String reason = body != null ? body.getReason() : null;
        VisitorRequestDTO dto = service.reject(requestId, ownerId, decidedBy, reason);
        return ResponseEntity.ok(ApiResponse.success("Entry rejected.", dto));
    }

    private Long extractOwnerId(String authHeader) {
        return jwtUtil.extractUserId(token(authHeader));
    }

    private String extractUsername(String authHeader) {
        return jwtUtil.extractUsername(token(authHeader));
    }

    private String token(String authHeader) {
        return authHeader.replace("Bearer ", "");
    }
}
