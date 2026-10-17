package com.society.module.visitor.service;

import com.society.common.FileUploadService;
import com.society.enums.TenantStatus;
import com.society.enums.VisitorStatus;
import com.society.exception.BusinessException;
import com.society.exception.ResourceNotFoundException;
import com.society.module.owner.entity.Owner;
import com.society.module.owner.entity.Unit;
import com.society.module.owner.entity.UnitOwner;
import com.society.module.owner.repository.UnitOwnerRepository;
import com.society.module.owner.repository.UnitRepository;
import com.society.module.settings.service.SocietySettingsService;
import com.society.module.tenant.entity.Tenant;
import com.society.module.tenant.repository.TenantRepository;
import com.society.module.visitor.dto.VisitorRecipient;
import com.society.module.visitor.dto.VisitorRequestDTO;
import com.society.module.visitor.entity.VisitorRequest;
import com.society.module.visitor.repository.VisitorRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Visitor/delivery gate-approval workflow.
 *
 * <p>Guards (gate-portal) create a {@link VisitorRequest} against a flat; the service
 * resolves the flat's recipients (all owners + the active tenant if rented), persists
 * a PENDING request with the configurable expiry, and asks the {@link VisitorNotifier}
 * to push a notification. Owners/tenants (member portal) approve or reject — the first
 * decision wins. A separate scheduler ({@code VisitorExpiryScheduler}) flips stale
 * PENDING requests to {@link VisitorStatus#TIMED_OUT}.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VisitorRequestService {

    private static final int DEFAULT_TIMEOUT_MINUTES = 10;

    private final VisitorRequestRepository requestRepository;
    private final UnitRepository unitRepository;
    private final UnitOwnerRepository unitOwnerRepository;
    private final TenantRepository tenantRepository;
    private final SocietySettingsService settingsService;
    private final FileUploadService fileUploadService;
    private final VisitorNotifier notifier;

    // ==================== GUARD: CREATE ====================

    /**
     * Register a new visitor/delivery entry request for a flat. The flat may be given
     * by unit id or unit number (whatever the gate screen captured). The guard's
     * identity is recorded automatically via JPA auditing ({@code createdBy}).
     *
     * @param unitId      destination unit id (preferred); may be null if unitNumber is given
     * @param unitNumber  destination unit number (fallback lookup); may be null if unitId is given
     * @param visitorName name of the delivery person / visitor (required)
     * @param company     delivery company / courier (optional)
     * @param purpose     free-text purpose (optional)
     * @param photo       photo of the visitor/ID (optional)
     */
    @Transactional
    public VisitorRequestDTO createRequest(Long unitId, String unitNumber, String visitorName,
                                           String company, String purpose, MultipartFile photo) {
        if (visitorName == null || visitorName.isBlank()) {
            throw new BusinessException("Visitor name is required.");
        }

        Unit unit = resolveUnit(unitId, unitNumber);

        List<VisitorRecipient> recipients = resolveRecipients(unit);
        if (recipients.isEmpty()) {
            throw new BusinessException(
                    "No owner or active tenant is registered for flat " + unit.getUnitNumber()
                            + ". Cannot request approval.");
        }

        String photoPath = null;
        if (photo != null && !photo.isEmpty()) {
            photoPath = fileUploadService.uploadFile(photo, "visitors/" + unit.getUnitId());
        }

        LocalDateTime now = LocalDateTime.now();
        VisitorRequest request = VisitorRequest.builder()
                .unit(unit)
                .visitorName(visitorName.trim())
                .company(trimToNull(company))
                .purpose(trimToNull(purpose))
                .photoPath(photoPath)
                .status(VisitorStatus.PENDING)
                .expiresAt(now.plusMinutes(resolveTimeoutMinutes()))
                .build();

        request = requestRepository.save(request);

        // Never let a notification failure roll back the persisted request.
        try {
            notifier.notifyNewRequest(request, recipients);
        } catch (RuntimeException e) {
            log.warn("Failed to send visitor-request notification for request {}: {}",
                    request.getVisitorRequestId(), e.toString());
        }

        log.info("Visitor request {} created for flat {} by guard {} ({} recipient(s)).",
                request.getVisitorRequestId(), unit.getUnitNumber(), request.getCreatedBy(), recipients.size());

        VisitorRequestDTO dto = toDTO(request);
        dto.setNotifiedRecipients(recipients.stream()
                .map(VisitorRecipient::getName)
                .collect(Collectors.toList()));
        return dto;
    }

    /**
     * Resolves all notification recipients for a flat: every owner of the unit, plus the
     * active tenant when the flat is rented. Returns an empty list when the flat has
     * neither (an unassigned/vacant unit the guard shouldn't be sending requests for).
     */
    public List<VisitorRecipient> resolveRecipients(Unit unit) {
        List<VisitorRecipient> recipients = new ArrayList<>();

        for (UnitOwner unitOwner : unitOwnerRepository.findByUnit_UnitId(unit.getUnitId())) {
            Owner owner = unitOwner.getOwner();
            if (owner != null) {
                recipients.add(VisitorRecipient.builder()
                        .type(VisitorRecipient.Type.OWNER)
                        .ownerId(owner.getOwnerId())
                        .name(owner.getFullName())
                        .phone(owner.getContactNumber())
                        .email(owner.getEmail())
                        .build());
            }
        }

        tenantRepository.findActiveByUnitId(unit.getUnitId(), TenantStatus.ACTIVE)
                .ifPresent(tenant -> recipients.add(toTenantRecipient(tenant)));

        return recipients;
    }

    private VisitorRecipient toTenantRecipient(Tenant tenant) {
        return VisitorRecipient.builder()
                .type(VisitorRecipient.Type.TENANT)
                .tenantId(tenant.getTenantId())
                .name(tenant.getTenantName())
                .phone(tenant.getContactNumber())
                .email(tenant.getEmail())
                .build();
    }

    // ==================== GUARD: UNIT PICKER ====================

    /**
     * All active units as lightweight descriptors for the gate portal's tappable
     * Wing &rarr; Floor &rarr; Flat picker. Prefers the unit's {@code wing}/{@code floor}
     * columns; when a column is blank it derives the value from the unit number using
     * the society's numbering convention (e.g. {@code D-105} / {@code D1105} / {@code C101}
     * &rarr; wing letter, last two digits = flat, the remaining leading digits = floor).
     */
    public List<com.society.module.visitor.dto.GateUnitDTO> getActiveUnitsForGate() {
        return unitRepository.findAll().stream()
                .filter(u -> u.getStatus() == null || "ACTIVE".equalsIgnoreCase(u.getStatus()))
                .map(u -> {
                    String[] wf = deriveWingFloor(u.getUnitNumber(), u.getWing(), u.getFloor());
                    return com.society.module.visitor.dto.GateUnitDTO.builder()
                            .unitId(u.getUnitId())
                            .unitNumber(u.getUnitNumber())
                            .wing(wf[0])
                            .floor(wf[1])
                            .build();
                })
                .sorted((a, b) -> {
                    String an = a.getUnitNumber() == null ? "" : a.getUnitNumber();
                    String bn = b.getUnitNumber() == null ? "" : b.getUnitNumber();
                    return an.compareTo(bn);
                })
                .collect(Collectors.toList());
    }

    /**
     * Returns {@code [wing, floor]} for a unit, trusting the stored columns when present
     * and otherwise parsing the unit number. Parsing rule, matching the society's scheme:
     * leading letters are the wing; the digit block (after an optional '-') has its last
     * two characters as the flat and the remaining leading digits as the floor. A unit
     * number that doesn't fit leaves the derived part null (grouped as "Other" in the UI).
     */
    static String[] deriveWingFloor(String unitNumber, String wingColumn, String floorColumn) {
        String wing = (wingColumn != null && !wingColumn.isBlank()) ? wingColumn.trim() : null;
        String floor = (floorColumn != null && !floorColumn.isBlank()) ? floorColumn.trim() : null;
        if (wing != null && floor != null) {
            return new String[]{wing, floor};
        }

        String parsedWing = null;
        String parsedFloor = null;
        if (unitNumber != null && !unitNumber.isBlank()) {
            String un = unitNumber.trim().toUpperCase();
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("^([A-Z]+)-?(\\d+)$").matcher(un);
            if (m.matches()) {
                parsedWing = m.group(1);
                String digits = m.group(2);
                if (digits.length() > 2) {
                    parsedFloor = String.valueOf(Integer.parseInt(digits.substring(0, digits.length() - 2)));
                } else {
                    // 1–2 digit block: no floor part (e.g. a ground-floor/shop style number).
                    parsedFloor = "0";
                }
            }
        }
        return new String[]{wing != null ? wing : parsedWing, floor != null ? floor : parsedFloor};
    }

    // ==================== GUARD: READ ====================

    public VisitorRequestDTO getById(Long requestId) {
        return toDTO(findOrThrow(requestId));
    }

    public List<VisitorRequestDTO> getByStatus(VisitorStatus status) {
        return requestRepository.findByStatusOrderByCreatedOnDesc(status)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<VisitorRequestDTO> getAll() {
        return requestRepository.findAllByOrderByCreatedOnDesc()
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    // ==================== MEMBER: READ + DECIDE ====================

    /** Pending requests across all units an owner owns. */
    public List<VisitorRequestDTO> getPendingForOwner(Long ownerId) {
        List<Long> unitIds = unitRepository.findByOwnerId(ownerId).stream()
                .map(Unit::getUnitId)
                .collect(Collectors.toList());
        if (unitIds.isEmpty()) {
            return List.of();
        }
        return requestRepository.findByStatusAndUnitIds(VisitorStatus.PENDING, unitIds)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * Approve a pending request. First decision wins. The caller (owner) must own the
     * flat the request is for. TIMED_OUT/decided requests cannot be approved.
     */
    @Transactional
    public VisitorRequestDTO approve(Long requestId, Long ownerId, String decidedBy) {
        VisitorRequest request = findDecidableForOwner(requestId, ownerId);
        request.setStatus(VisitorStatus.APPROVED);
        request.setDecidedBy(decidedBy);
        request.setDecidedOn(LocalDateTime.now());
        request = requestRepository.save(request);
        log.info("Visitor request {} APPROVED by {}.", requestId, decidedBy);
        return toDTO(request);
    }

    /**
     * Reject a pending request. First decision wins. The caller (owner) must own the
     * flat the request is for. TIMED_OUT/decided requests cannot be rejected.
     */
    @Transactional
    public VisitorRequestDTO reject(Long requestId, Long ownerId, String decidedBy, String reason) {
        VisitorRequest request = findDecidableForOwner(requestId, ownerId);
        request.setStatus(VisitorStatus.REJECTED);
        request.setDecidedBy(decidedBy);
        request.setDecidedOn(LocalDateTime.now());
        request.setRejectionReason(trimToNull(reason));
        request = requestRepository.save(request);
        log.info("Visitor request {} REJECTED by {}: {}", requestId, decidedBy, reason);
        return toDTO(request);
    }

    private VisitorRequest findDecidableForOwner(Long requestId, Long ownerId) {
        VisitorRequest request = findOrThrow(requestId);

        boolean ownsUnit = unitOwnerRepository.findByUnit_UnitId(request.getUnit().getUnitId()).stream()
                .anyMatch(uo -> uo.getOwner() != null
                        && uo.getOwner().getOwnerId() != null
                        && uo.getOwner().getOwnerId().equals(ownerId));
        if (!ownsUnit) {
            throw new BusinessException("You can only respond to visitor requests for your own flat.");
        }

        if (request.getStatus() != VisitorStatus.PENDING) {
            // First-wins / expiry: a decided or timed-out request cannot be changed.
            throw new BusinessException(
                    "This visitor request has already been " + request.getStatus().name().toLowerCase()
                            + " and can no longer be changed.");
        }
        return request;
    }

    // ==================== SCHEDULER SUPPORT ====================

    /**
     * Flips every still-PENDING request past its expiry to TIMED_OUT. Returns the number
     * of requests expired. Invoked by the auto-expiry scheduler (Task 5).
     */
    @Transactional
    public int expireOverdueRequests() {
        LocalDateTime now = LocalDateTime.now();
        List<VisitorRequest> expired = requestRepository.findExpiredPending(VisitorStatus.PENDING, now);
        for (VisitorRequest request : expired) {
            request.setStatus(VisitorStatus.TIMED_OUT);
        }
        if (!expired.isEmpty()) {
            requestRepository.saveAll(expired);
            log.info("Auto-expired {} pending visitor request(s).", expired.size());
        }
        return expired.size();
    }

    // ==================== HELPERS ====================

    private Unit resolveUnit(Long unitId, String unitNumber) {
        if (unitId != null) {
            return unitRepository.findById(unitId)
                    .orElseThrow(() -> new ResourceNotFoundException("Unit", "unitId", unitId));
        }
        if (unitNumber != null && !unitNumber.isBlank()) {
            return unitRepository.findByUnitNumber(unitNumber.trim())
                    .orElseThrow(() -> new ResourceNotFoundException("Unit", "unitNumber", unitNumber));
        }
        throw new BusinessException("A flat (unit id or unit number) is required.");
    }

    private int resolveTimeoutMinutes() {
        try {
            Integer minutes = settingsService.getSettings().getVisitorApprovalTimeoutMinutes();
            if (minutes != null && minutes > 0) {
                return minutes;
            }
        } catch (RuntimeException e) {
            log.warn("Could not read visitor approval timeout from settings; using default {} min: {}",
                    DEFAULT_TIMEOUT_MINUTES, e.toString());
        }
        return DEFAULT_TIMEOUT_MINUTES;
    }

    private VisitorRequest findOrThrow(Long requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Visitor Request", "visitorRequestId", requestId));
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private VisitorRequestDTO toDTO(VisitorRequest r) {
        Unit unit = r.getUnit();
        return VisitorRequestDTO.builder()
                .visitorRequestId(r.getVisitorRequestId())
                .unitId(unit != null ? unit.getUnitId() : null)
                .unitNumber(unit != null ? unit.getUnitNumber() : null)
                .wing(unit != null ? unit.getWing() : null)
                .floor(unit != null ? unit.getFloor() : null)
                .visitorName(r.getVisitorName())
                .company(r.getCompany())
                .purpose(r.getPurpose())
                .photoPath(r.getPhotoPath())
                .status(r.getStatus() != null ? r.getStatus().name() : null)
                .createdBy(r.getCreatedBy())
                .createdOn(r.getCreatedOn())
                .decidedBy(r.getDecidedBy())
                .decidedOn(r.getDecidedOn())
                .rejectionReason(r.getRejectionReason())
                .expiresAt(r.getExpiresAt())
                .build();
    }
}
