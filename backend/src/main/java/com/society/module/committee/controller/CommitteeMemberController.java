package com.society.module.committee.controller;

import com.society.common.ApiResponse;
import com.society.module.committee.dto.CommitteeMemberCreateRequest;
import com.society.module.committee.dto.CommitteeMemberDTO;
import com.society.module.committee.dto.CommitteeMemberUpdateRequest;
import com.society.module.committee.service.CommitteeMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/committee-members")
@RequiredArgsConstructor
public class CommitteeMemberController {

    private final CommitteeMemberService service;

    /**
     * Public endpoint - returns active committee members for the landing page.
     * No authentication required.
     */
    @GetMapping("/public")
    public ResponseEntity<ApiResponse<List<CommitteeMemberDTO>>> getPublicMembers() {
        List<CommitteeMemberDTO> members = service.getActiveMembers();
        return ResponseEntity.ok(ApiResponse.success(members));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CommitteeMemberDTO>>> getAllMembers() {
        List<CommitteeMemberDTO> members = service.getAllMembers();
        return ResponseEntity.ok(ApiResponse.success(members));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CommitteeMemberDTO>> getMemberById(@PathVariable Long id) {
        CommitteeMemberDTO member = service.getMemberById(id);
        return ResponseEntity.ok(ApiResponse.success(member));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CommitteeMemberDTO>> createMember(
            @Valid @RequestBody CommitteeMemberCreateRequest request) {
        CommitteeMemberDTO member = service.createMember(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Committee member added successfully", member));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CommitteeMemberDTO>> updateMember(
            @PathVariable Long id,
            @Valid @RequestBody CommitteeMemberUpdateRequest request) {
        CommitteeMemberDTO member = service.updateMember(id, request);
        return ResponseEntity.ok(ApiResponse.success("Committee member updated successfully", member));
    }

    @PostMapping(value = "/{id}/photo", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<CommitteeMemberDTO>> uploadPhoto(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        CommitteeMemberDTO member = service.uploadPhoto(id, file);
        return ResponseEntity.ok(ApiResponse.success("Photo uploaded successfully", member));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMember(@PathVariable Long id) {
        service.deleteMember(id);
        return ResponseEntity.ok(ApiResponse.success("Committee member deleted successfully", null));
    }
}
