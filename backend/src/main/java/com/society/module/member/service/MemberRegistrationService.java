package com.society.module.member.service;

import com.society.exception.BusinessException;
import com.society.module.member.dto.MemberRegistrationRequestDTO;
import com.society.module.member.entity.MemberRegistrationRequest;
import com.society.module.member.repository.MemberRegistrationRequestRepository;
import com.society.module.owner.entity.Owner;
import com.society.module.owner.entity.Unit;
import com.society.module.owner.entity.UnitOwner;
import com.society.module.owner.repository.OwnerRepository;
import com.society.module.owner.repository.UnitOwnerRepository;
import com.society.module.owner.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemberRegistrationService {

    private final MemberRegistrationRequestRepository registrationRepository;
    private final OtpService otpService;
    private final OwnerRepository ownerRepository;
    private final UnitRepository unitRepository;
    private final UnitOwnerRepository unitOwnerRepository;

    /**
     * Step 1: Member submits email + mobile + unit. OTP is sent to email.
     */
    @Transactional
    public void submitRegistration(String email, String mobile, Long unitId) {
        if (email == null || email.isBlank()) {
            throw new BusinessException("Email is required");
        }
        if (mobile == null || mobile.isBlank()) {
            throw new BusinessException("Mobile number is required");
        }
        if (unitId == null) {
            throw new BusinessException("Please select your unit/flat number");
        }

        // Validate unit exists
        Unit unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new BusinessException("Selected unit not found"));

        // Check if any owner of this unit already has this mobile registered
        List<UnitOwner> unitOwners = unitOwnerRepository.findByUnit_UnitId(unitId);
        for (UnitOwner uo : unitOwners) {
            Owner existingOwner = uo.getOwner();
            if (mobile.equals(existingOwner.getContactNumber()) || mobile.equals(existingOwner.getAlternateNumber())) {
                throw new BusinessException("This mobile number is already registered for unit " + unit.getUnitNumber() + ". Please use the login option.");
            }
        }

        // Check if already has a pending request
        if (registrationRepository.existsByEmailAndStatus(email, "PENDING")) {
            throw new BusinessException("A registration request with this email is already pending. Please wait for admin approval.");
        }
        if (registrationRepository.existsByMobileAndStatus(mobile, "PENDING")) {
            throw new BusinessException("A registration request with this mobile is already pending. Please wait for admin approval.");
        }

        // Check if mobile already exists in owner records
        List<Owner> existingOwners = ownerRepository.findByContactNumberOrAlternateNumber(mobile);
        if (!existingOwners.isEmpty()) {
            throw new BusinessException("This mobile number is already registered. Please use the login option.");
        }

        // Send OTP to the email for verification
        otpService.generateAndSendOtp(mobile, email);

        log.info("Registration OTP sent to email: {} for mobile: {}, unitId: {}", email, mobile, unitId);
    }

    /**
     * Step 2: Verify OTP and create the registration request.
     */
    @Transactional
    public MemberRegistrationRequestDTO verifyAndCreateRequest(String email, String mobile, String otp, Long unitId) {
        // Verify OTP
        otpService.verifyOtp(mobile, otp);

        // Get unit number for display
        Unit unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new BusinessException("Unit not found"));

        // Create registration request
        MemberRegistrationRequest request = MemberRegistrationRequest.builder()
                .email(email)
                .mobile(mobile)
                .unitId(unitId)
                .unitNumber(unit.getUnitNumber())
                .emailVerified(true)
                .status("PENDING")
                .build();
        registrationRepository.save(request);

        log.info("Registration request created - email: {}, mobile: {}, unit: {}", email, mobile, unit.getUnitNumber());

        return mapToDTO(request);
    }

    /**
     * Admin: Get all pending registration requests.
     */
    public List<MemberRegistrationRequestDTO> getPendingRequests() {
        return registrationRepository.findByStatusOrderByCreatedOnDesc("PENDING").stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Admin: Get all registration requests.
     */
    public List<MemberRegistrationRequestDTO> getAllRequests() {
        return registrationRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedOn().compareTo(a.getCreatedOn()))
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Admin: Approve request.
     * If ownerId is provided, link to that specific owner.
     * If not, auto-link to the primary owner of the unit.
     */
    @Transactional
    public MemberRegistrationRequestDTO approveRequest(Long requestId, Long ownerId, String adminName) {
        MemberRegistrationRequest req = registrationRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException("Request not found"));

        if (!"PENDING".equals(req.getStatus())) {
            throw new BusinessException("Request is already " + req.getStatus().toLowerCase());
        }

        // Find the target owner
        if (ownerId == null && req.getUnitId() != null) {
            List<UnitOwner> unitOwners = unitOwnerRepository.findByUnit_UnitId(req.getUnitId());
            if (unitOwners.size() > 1) {
                throw new BusinessException("This unit has multiple owners. Please select which owner to link.");
            }
            ownerId = unitOwners.stream()
                    .filter(uo -> Boolean.TRUE.equals(uo.getIsPrimary()))
                    .map(uo -> uo.getOwner().getOwnerId())
                    .findFirst()
                    .orElse(unitOwners.isEmpty() ? null : unitOwners.get(0).getOwner().getOwnerId());
        }

        if (ownerId == null) {
            throw new BusinessException("No owner found for this unit. Please select an owner manually.");
        }

        // Check if this owner already has a phone number
        final Long finalOwnerId = ownerId;
        Owner owner = ownerRepository.findById(finalOwnerId)
                .orElseThrow(() -> new BusinessException("Owner not found with ID: " + finalOwnerId));

        if (owner.getContactNumber() != null && !owner.getContactNumber().isBlank()) {
            throw new BusinessException("Owner " + owner.getFullName() + " already has mobile " +
                    owner.getContactNumber().substring(0, 2) + "****" +
                    owner.getContactNumber().substring(owner.getContactNumber().length() - 2) +
                    " registered. Reject this request if it's a duplicate.");
        }

        // Update owner's contact details
        owner.setEmail(req.getEmail());
        owner.setContactNumber(req.getMobile());
        ownerRepository.save(owner);

        // Update request status
        req.setStatus("APPROVED");
        req.setLinkedOwnerId(ownerId);
        req.setReviewedBy(adminName);
        req.setReviewedOn(LocalDateTime.now());
        registrationRepository.save(req);

        log.info("Registration request {} approved - linked to owner {} ({}) by {}",
                requestId, ownerId, owner.getFullName(), adminName);

        return mapToDTO(req);
    }

    /**
     * Admin: Reject request.
     */
    @Transactional
    public MemberRegistrationRequestDTO rejectRequest(Long requestId, String adminName, String reason) {
        MemberRegistrationRequest req = registrationRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException("Request not found"));

        if (!"PENDING".equals(req.getStatus())) {
            throw new BusinessException("Request is already " + req.getStatus().toLowerCase());
        }

        req.setStatus("REJECTED");
        req.setReviewedBy(adminName);
        req.setReviewedOn(LocalDateTime.now());
        req.setRejectionReason(reason);
        registrationRepository.save(req);

        log.info("Registration request {} rejected by {}: {}", requestId, adminName, reason);

        return mapToDTO(req);
    }

    /**
     * Get owners of a specific unit (for admin to pick owner/co-owner).
     */
    public List<Map<String, Object>> getUnitOwners(Long unitId) {
        return unitOwnerRepository.findByUnit_UnitId(unitId).stream()
                .map(uo -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("ownerId", uo.getOwner().getOwnerId());
                    m.put("fullName", uo.getOwner().getFullName());
                    m.put("isPrimary", uo.getIsPrimary());
                    m.put("hasPhone", uo.getOwner().getContactNumber() != null && !uo.getOwner().getContactNumber().isBlank());
                    return m;
                })
                .collect(Collectors.toList());
    }

    private MemberRegistrationRequestDTO mapToDTO(MemberRegistrationRequest r) {
        String ownerName = null;
        if (r.getLinkedOwnerId() != null) {
            ownerName = ownerRepository.findById(r.getLinkedOwnerId())
                    .map(Owner::getFullName).orElse(null);
        }

        return MemberRegistrationRequestDTO.builder()
                .requestId(r.getRequestId())
                .email(r.getEmail())
                .mobile(r.getMobile())
                .unitId(r.getUnitId())
                .unitNumber(r.getUnitNumber())
                .emailVerified(r.getEmailVerified())
                .status(r.getStatus())
                .linkedOwnerId(r.getLinkedOwnerId())
                .linkedOwnerName(ownerName)
                .reviewedBy(r.getReviewedBy())
                .reviewedOn(r.getReviewedOn())
                .rejectionReason(r.getRejectionReason())
                .createdOn(r.getCreatedOn())
                .build();
    }
}
