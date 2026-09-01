package com.society.module.ownernoc.service;

import com.society.enums.OwnerNocStatus;
import com.society.exception.BusinessException;
import com.society.exception.ResourceNotFoundException;
import com.society.module.owner.entity.Owner;
import com.society.module.owner.entity.Unit;
import com.society.module.owner.repository.OwnerRepository;
import com.society.module.owner.repository.UnitOwnerRepository;
import com.society.module.owner.repository.UnitRepository;
import com.society.module.ownernoc.dto.OwnerNocRequestCreateRequest;
import com.society.module.ownernoc.dto.OwnerNocRequestDTO;
import com.society.module.ownernoc.entity.NocType;
import com.society.module.ownernoc.entity.OwnerNocRequest;
import com.society.module.ownernoc.repository.NocTypeRepository;
import com.society.module.ownernoc.repository.OwnerNocRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Owner NOC request workflow: owner submits (from member portal), admin
 * approves (generates + emails certificate) or rejects (emails reason).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OwnerNocRequestService {

    private final OwnerNocRequestRepository requestRepository;
    private final NocTypeRepository nocTypeRepository;
    private final OwnerRepository ownerRepository;
    private final UnitRepository unitRepository;
    private final UnitOwnerRepository unitOwnerRepository;
    private final OwnerNocNotificationService notificationService;
    private final OwnerNocPdfService ownerNocPdfService;

    // ==================== MEMBER SUBMISSION ====================

    /**
     * Owner (member portal) submits a NOC request. The ownerId comes from the
     * authenticated member JWT. If a unit is specified, the owner must own it.
     */
    @Transactional
    public OwnerNocRequestDTO submit(OwnerNocRequestCreateRequest request, Long ownerId) {
        Owner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Owner", "ownerId", ownerId));

        NocType type = nocTypeRepository.findById(request.getNocTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("NOC Type", "nocTypeId", request.getNocTypeId()));
        if (!Boolean.TRUE.equals(type.getIsActive())) {
            throw new BusinessException("The selected NOC type is not available.");
        }

        Unit unit = null;
        if (request.getUnitId() != null) {
            unit = unitRepository.findById(request.getUnitId())
                    .orElseThrow(() -> new ResourceNotFoundException("Unit", "unitId", request.getUnitId()));
            boolean ownsUnit = unitOwnerRepository.findByUnit_UnitId(request.getUnitId()).stream()
                    .anyMatch(uo -> uo.getOwner() != null
                            && uo.getOwner().getOwnerId() != null
                            && uo.getOwner().getOwnerId().equals(ownerId));
            if (!ownsUnit) {
                throw new BusinessException("You can only request a NOC for a unit you own.");
            }
        }

        OwnerNocRequest req = OwnerNocRequest.builder()
                .owner(owner)
                .unit(unit)
                .nocType(type)
                .addressee(request.getAddressee())
                .details(request.getDetails())
                .status(OwnerNocStatus.PENDING)
                .build();

        req = requestRepository.save(req);
        return toDTO(req);
    }

    public List<OwnerNocRequestDTO> getMyRequests(Long ownerId) {
        return requestRepository.findByOwnerIdOrderByCreatedOnDesc(ownerId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    // ==================== ADMIN REVIEW ====================

    public List<OwnerNocRequestDTO> getPending() {
        return requestRepository.findByStatusOrderByCreatedOnDesc(OwnerNocStatus.PENDING)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * Approve a pending request: record approver, set final certificate body
     * (admin-provided or the type's default template), generate + email the PDF.
     */
    @Transactional
    public OwnerNocRequestDTO approve(Long requestId, String approver, String finalContent) {
        OwnerNocRequest req = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("NOC Request", "requestId", requestId));
        if (req.getStatus() != OwnerNocStatus.PENDING) {
            throw new BusinessException("Only pending NOC requests can be approved. Current status: " + req.getStatus());
        }

        String body = (finalContent != null && !finalContent.isBlank())
                ? finalContent
                : (req.getNocType() != null ? req.getNocType().getDefaultTemplate() : null);
        req.setFinalContent(body);
        req.setStatus(OwnerNocStatus.APPROVED);
        req.setReviewedBy(approver != null ? approver : "ADMIN");
        req.setReviewedOn(LocalDateTime.now());
        req = requestRepository.save(req);

        notificationService.sendApproved(req);
        log.info("Owner NOC request {} approved by {}.", requestId, approver);
        return toDTO(req);
    }

    @Transactional
    public OwnerNocRequestDTO reject(Long requestId, String approver, String reason) {
        OwnerNocRequest req = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("NOC Request", "requestId", requestId));
        if (req.getStatus() != OwnerNocStatus.PENDING) {
            throw new BusinessException("Only pending NOC requests can be rejected. Current status: " + req.getStatus());
        }

        req.setStatus(OwnerNocStatus.REJECTED);
        req.setReviewedBy(approver != null ? approver : "ADMIN");
        req.setReviewedOn(LocalDateTime.now());
        req.setRejectionReason(reason);
        req = requestRepository.save(req);

        notificationService.sendRejected(req);
        log.info("Owner NOC request {} rejected by {}: {}", requestId, approver, reason);
        return toDTO(req);
    }

    // ==================== CERTIFICATE DOWNLOAD ====================

    /**
     * Generate the certificate PDF bytes for an approved request (download fallback,
     * usable when email is not configured or the owner needs another copy).
     */
    public byte[] generateCertificatePdf(Long requestId) {
        OwnerNocRequest req = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("NOC Request", "requestId", requestId));
        if (req.getStatus() != OwnerNocStatus.APPROVED) {
            throw new BusinessException("Certificate is available only for approved NOC requests.");
        }
        try {
            return ownerNocPdfService.generate(req);
        } catch (java.io.IOException e) {
            throw new BusinessException("Failed to generate certificate PDF: " + e.getMessage());
        }
    }

    // ==================== MAPPER ====================

    private OwnerNocRequestDTO toDTO(OwnerNocRequest r) {
        return OwnerNocRequestDTO.builder()
                .requestId(r.getRequestId())
                .ownerId(r.getOwner() != null ? r.getOwner().getOwnerId() : null)
                .ownerName(r.getOwner() != null ? r.getOwner().getFullName() : null)
                .ownerEmail(r.getOwner() != null ? r.getOwner().getEmail() : null)
                .unitId(r.getUnit() != null ? r.getUnit().getUnitId() : null)
                .unitNumber(r.getUnit() != null ? r.getUnit().getUnitNumber() : null)
                .nocTypeId(r.getNocType() != null ? r.getNocType().getNocTypeId() : null)
                .nocTypeCode(r.getNocType() != null ? r.getNocType().getCode() : null)
                .nocTypeName(r.getNocType() != null ? r.getNocType().getName() : null)
                .addressee(r.getAddressee())
                .details(r.getDetails())
                .finalContent(r.getFinalContent())
                .status(r.getStatus() != null ? r.getStatus().name() : null)
                .reviewedBy(r.getReviewedBy())
                .reviewedOn(r.getReviewedOn())
                .rejectionReason(r.getRejectionReason())
                .createdOn(r.getCreatedOn())
                .build();
    }
}
