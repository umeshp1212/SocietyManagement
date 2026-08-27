package com.society.module.member.service;

import com.society.exception.BusinessException;
import com.society.module.member.dto.*;
import com.society.module.member.entity.ProfileUpdateRequest;
import com.society.module.member.repository.ProfileUpdateRequestRepository;
import com.society.module.owner.entity.Owner;
import com.society.module.owner.entity.UnitOwner;
import com.society.module.owner.repository.OwnerRepository;
import com.society.module.owner.repository.UnitOwnerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemberProfileService {

    private final OwnerRepository ownerRepository;
    private final UnitOwnerRepository unitOwnerRepository;
    private final ProfileUpdateRequestRepository requestRepository;

    /**
     * Get member's profile with masked personal info.
     */
    public MemberProfileDTO getProfile(Long ownerId) {
        Owner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> new BusinessException("Owner not found"));

        UnitOwner unitOwner = unitOwnerRepository.findByOwner_OwnerId(ownerId).stream()
                .findFirst().orElse(null);

        boolean hasPending = requestRepository.existsByOwner_OwnerIdAndStatus(ownerId, "PENDING");

        List<ProfileUpdateRequestDTO> requests = requestRepository
                .findByOwner_OwnerIdOrderByCreatedOnDesc(ownerId).stream()
                .map(r -> mapToDTO(r, false))
                .collect(Collectors.toList());

        return MemberProfileDTO.builder()
                .ownerId(ownerId)
                .fullName(owner.getFullName())
                .maskedMobile(maskMobile(owner.getContactNumber()))
                .maskedEmail(maskEmail(owner.getEmail()))
                .unitNumber(unitOwner != null ? unitOwner.getUnit().getUnitNumber() : null)
                .wing(unitOwner != null ? unitOwner.getUnit().getWing() : null)
                .floor(unitOwner != null ? unitOwner.getUnit().getFloor() : null)
                .hasPendingRequest(hasPending)
                .updateRequests(requests)
                .build();
    }

    /**
     * Submit a profile update request (member action).
     */
    @Transactional
    public ProfileUpdateRequestDTO submitUpdateRequest(Long ownerId, SubmitProfileUpdateRequest request) {
        Owner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> new BusinessException("Owner not found"));

        // Check for existing pending request
        if (requestRepository.existsByOwner_OwnerIdAndStatus(ownerId, "PENDING")) {
            throw new BusinessException("You already have a pending update request. Please wait for admin approval.");
        }

        // Validate at least one field is being updated
        boolean mobileChanged = request.getNewMobile() != null && !request.getNewMobile().isBlank()
                && !request.getNewMobile().equals(owner.getContactNumber());
        boolean emailChanged = request.getNewEmail() != null && !request.getNewEmail().isBlank()
                && !request.getNewEmail().equals(owner.getEmail());

        if (!mobileChanged && !emailChanged) {
            throw new BusinessException("No changes detected. Please provide a new mobile number or email.");
        }

        String fieldType = mobileChanged && emailChanged ? "BOTH"
                : mobileChanged ? "MOBILE" : "EMAIL";

        ProfileUpdateRequest entity = ProfileUpdateRequest.builder()
                .owner(owner)
                .fieldType(fieldType)
                .oldMobile(owner.getContactNumber())
                .newMobile(mobileChanged ? request.getNewMobile() : null)
                .oldEmail(owner.getEmail())
                .newEmail(emailChanged ? request.getNewEmail() : null)
                .reason(request.getReason())
                .status("PENDING")
                .build();

        requestRepository.save(entity);

        log.info("Profile update request submitted - ownerId: {}, type: {}", ownerId, fieldType);

        return mapToDTO(entity, false);
    }

    /**
     * Get all pending requests (admin action).
     */
    public List<ProfileUpdateRequestDTO> getPendingRequests() {
        return requestRepository.findByStatusOrderByCreatedOnDesc("PENDING").stream()
                .map(r -> mapToDTO(r, true))
                .collect(Collectors.toList());
    }

    /**
     * Get all requests (admin action).
     */
    public List<ProfileUpdateRequestDTO> getAllRequests() {
        return requestRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedOn().compareTo(a.getCreatedOn()))
                .map(r -> mapToDTO(r, true))
                .collect(Collectors.toList());
    }

    /**
     * Approve a profile update request (admin action).
     */
    @Transactional
    public ProfileUpdateRequestDTO approveRequest(Long requestId, String adminName) {
        ProfileUpdateRequest req = requestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException("Request not found"));

        if (!"PENDING".equals(req.getStatus())) {
            throw new BusinessException("Request is already " + req.getStatus().toLowerCase());
        }

        Owner owner = req.getOwner();

        // Apply the changes
        if (req.getNewMobile() != null && !req.getNewMobile().isBlank()) {
            log.info("Updating mobile for owner {} from {} to {}", owner.getOwnerId(),
                    maskMobile(owner.getContactNumber()), maskMobile(req.getNewMobile()));
            owner.setContactNumber(req.getNewMobile());
        }
        if (req.getNewEmail() != null && !req.getNewEmail().isBlank()) {
            log.info("Updating email for owner {} from {} to {}", owner.getOwnerId(),
                    maskEmail(owner.getEmail()), maskEmail(req.getNewEmail()));
            owner.setEmail(req.getNewEmail());
        }
        ownerRepository.save(owner);

        req.setStatus("APPROVED");
        req.setReviewedBy(adminName);
        req.setReviewedOn(LocalDateTime.now());
        requestRepository.save(req);

        log.info("Profile update request {} approved by {}", requestId, adminName);

        return mapToDTO(req, true);
    }

    /**
     * Reject a profile update request (admin action).
     */
    @Transactional
    public ProfileUpdateRequestDTO rejectRequest(Long requestId, String adminName, String rejectionReason) {
        ProfileUpdateRequest req = requestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException("Request not found"));

        if (!"PENDING".equals(req.getStatus())) {
            throw new BusinessException("Request is already " + req.getStatus().toLowerCase());
        }

        req.setStatus("REJECTED");
        req.setReviewedBy(adminName);
        req.setReviewedOn(LocalDateTime.now());
        req.setRejectionReason(rejectionReason);
        requestRepository.save(req);

        log.info("Profile update request {} rejected by {}: {}", requestId, adminName, rejectionReason);

        return mapToDTO(req, true);
    }

    // ==================== HELPERS ====================

    private ProfileUpdateRequestDTO mapToDTO(ProfileUpdateRequest r, boolean showFullValues) {
        String unitNumber = null;
        try {
            UnitOwner uo = unitOwnerRepository.findByOwner_OwnerId(r.getOwner().getOwnerId())
                    .stream().findFirst().orElse(null);
            if (uo != null) unitNumber = uo.getUnit().getUnitNumber();
        } catch (Exception ignored) {}

        ProfileUpdateRequestDTO.ProfileUpdateRequestDTOBuilder builder = ProfileUpdateRequestDTO.builder()
                .requestId(r.getRequestId())
                .ownerId(r.getOwner().getOwnerId())
                .ownerName(r.getOwner().getFullName())
                .unitNumber(unitNumber)
                .fieldType(r.getFieldType())
                .oldMobileMasked(maskMobile(r.getOldMobile()))
                .newMobileMasked(maskMobile(r.getNewMobile()))
                .oldEmailMasked(maskEmail(r.getOldEmail()))
                .newEmailMasked(maskEmail(r.getNewEmail()))
                .reason(r.getReason())
                .status(r.getStatus())
                .reviewedBy(r.getReviewedBy())
                .reviewedOn(r.getReviewedOn())
                .rejectionReason(r.getRejectionReason())
                .createdOn(r.getCreatedOn());

        if (showFullValues) {
            builder.oldMobile(r.getOldMobile())
                    .newMobile(r.getNewMobile())
                    .oldEmail(r.getOldEmail())
                    .newEmail(r.getNewEmail());
        }

        return builder.build();
    }

    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 4) return mobile;
        return mobile.substring(0, 2) + "****" + mobile.substring(mobile.length() - 2);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        String[] parts = email.split("@");
        String name = parts[0];
        return name.substring(0, Math.min(2, name.length())) + "****@" + parts[1];
    }
}
