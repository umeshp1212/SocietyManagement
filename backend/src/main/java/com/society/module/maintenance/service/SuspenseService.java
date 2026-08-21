package com.society.module.maintenance.service;

import com.society.common.PagedResponse;
import com.society.enums.PaymentMode;
import com.society.exception.BusinessException;
import com.society.exception.ResourceNotFoundException;
import com.society.module.maintenance.dto.SuspenseAuditDTO;
import com.society.module.maintenance.dto.SuspenseEntryDTO;
import com.society.module.maintenance.entity.SuspenseAuditTrail;
import com.society.module.maintenance.entity.SuspenseAuditTrail.SuspenseAction;
import com.society.module.maintenance.entity.SuspenseEntry;
import com.society.module.maintenance.entity.SuspenseEntry.SuspenseStatus;
import com.society.module.maintenance.repository.SuspenseAuditTrailRepository;
import com.society.module.maintenance.repository.SuspenseEntryRepository;
import com.society.module.owner.entity.Unit;
import com.society.module.owner.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SuspenseService {

    private final SuspenseEntryRepository suspenseEntryRepository;
    private final SuspenseAuditTrailRepository auditTrailRepository;
    private final UnitRepository unitRepository;
    private final OpeningBalanceService openingBalanceService;

    // ==================== CREATE ====================

    @Transactional
    public SuspenseEntryDTO createSuspenseEntry(BigDecimal amount, LocalDate receivedDate,
                                                 String paymentMode, String referenceNumber,
                                                 String description, String createdBy) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Amount must be positive");
        }

        SuspenseEntry entry = SuspenseEntry.builder()
                .amount(amount)
                .receivedDate(receivedDate != null ? receivedDate : LocalDate.now())
                .paymentMode(paymentMode != null ? PaymentMode.valueOf(paymentMode) : null)
                .referenceNumber(referenceNumber)
                .description(description)
                .status(SuspenseStatus.UNASSIGNED)
                .build();

        entry = suspenseEntryRepository.save(entry);

        // Audit trail
        createAudit(entry, SuspenseAction.CREATED, null, null, createdBy,
                "Suspense entry created: " + amount + " via " + (paymentMode != null ? paymentMode : "Unknown"));

        log.info("Suspense entry created: ID={}, Amount={}, Ref={}", entry.getSuspenseId(), amount, referenceNumber);
        return mapToDTO(entry);
    }

    // ==================== ASSIGN ====================

    @Transactional
    public SuspenseEntryDTO assignToUnit(Long suspenseId, Long unitId, String remarks,
                                          boolean applyToOpeningBalance, String assignedBy) {
        SuspenseEntry entry = suspenseEntryRepository.findById(suspenseId)
                .orElseThrow(() -> new ResourceNotFoundException("SuspenseEntry", "suspenseId", suspenseId));

        if (entry.getStatus() == SuspenseStatus.ASSIGNED) {
            throw new BusinessException("This entry is already assigned to Unit " +
                    entry.getAssignedToUnit().getUnitNumber() + ". Reverse it first before reassigning.");
        }

        Unit unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit", "unitId", unitId));

        entry.setStatus(SuspenseStatus.ASSIGNED);
        entry.setAssignedToUnit(unit);
        entry.setAssignedBy(assignedBy);
        entry.setAssignedOn(LocalDateTime.now());
        entry.setAssignmentRemarks(remarks);
        entry.setApplyToOpeningBalance(applyToOpeningBalance);

        entry = suspenseEntryRepository.save(entry);

        // If applying to opening balance, reduce the unit's legacy arrears
        if (applyToOpeningBalance) {
            openingBalanceService.recordPaymentAgainstOpeningBalance(unitId, entry.getAmount());
            log.info("Suspense {} applied to opening balance of unit {}", suspenseId, unit.getUnitNumber());
        }

        // Audit trail
        createAudit(entry, SuspenseAction.ASSIGNED, unitId, unit.getUnitNumber(), assignedBy,
                remarks != null ? remarks : "Assigned to " + unit.getUnitNumber());

        log.info("Suspense entry {} assigned to unit {}", suspenseId, unit.getUnitNumber());
        return mapToDTO(entry);
    }

    // ==================== REVERSE ====================

    @Transactional
    public SuspenseEntryDTO reverseAssignment(Long suspenseId, String reason, String reversedBy) {
        SuspenseEntry entry = suspenseEntryRepository.findById(suspenseId)
                .orElseThrow(() -> new ResourceNotFoundException("SuspenseEntry", "suspenseId", suspenseId));

        if (entry.getStatus() != SuspenseStatus.ASSIGNED) {
            throw new BusinessException("Only ASSIGNED entries can be reversed");
        }

        if (reason == null || reason.isBlank()) {
            throw new BusinessException("Reason is mandatory for reversal");
        }

        Long previousUnitId = entry.getAssignedToUnit().getUnitId();
        String previousUnitNumber = entry.getAssignedToUnit().getUnitNumber();

        // If it was applied to opening balance, reverse that too
        if (Boolean.TRUE.equals(entry.getApplyToOpeningBalance())) {
            openingBalanceService.reversePaymentFromOpeningBalance(previousUnitId, entry.getAmount());
            log.info("Reversed opening balance payment for unit {} amount {}", previousUnitNumber, entry.getAmount());
        }

        entry.setStatus(SuspenseStatus.REVERSED);
        entry.setAssignedToUnit(null);
        entry.setAssignedBy(null);
        entry.setAssignedOn(null);
        entry.setAssignmentRemarks(null);
        entry.setApplyToOpeningBalance(false);

        entry = suspenseEntryRepository.save(entry);

        // Audit trail
        createAudit(entry, SuspenseAction.REVERSED, previousUnitId, previousUnitNumber, reversedBy, reason);

        log.info("Suspense entry {} reversed from unit {}. Reason: {}", suspenseId, previousUnitNumber, reason);
        return mapToDTO(entry);
    }

    // ==================== REASSIGN (shortcut: reverse + assign) ====================

    @Transactional
    public SuspenseEntryDTO reassign(Long suspenseId, Long newUnitId, String reason,
                                      String remarks, boolean applyToOpeningBalance, String performedBy) {
        // First reverse
        SuspenseEntry entry = suspenseEntryRepository.findById(suspenseId)
                .orElseThrow(() -> new ResourceNotFoundException("SuspenseEntry", "suspenseId", suspenseId));

        if (entry.getStatus() == SuspenseStatus.ASSIGNED) {
            reverseAssignment(suspenseId, reason, performedBy);
        }

        // Reset status to UNASSIGNED so assign works
        entry = suspenseEntryRepository.findById(suspenseId).orElseThrow();
        entry.setStatus(SuspenseStatus.UNASSIGNED);
        suspenseEntryRepository.save(entry);

        // Then assign to new unit
        return assignToUnit(suspenseId, newUnitId, remarks, applyToOpeningBalance, performedBy);
    }

    // ==================== QUERIES ====================

    public PagedResponse<SuspenseEntryDTO> getAllEntries(int page, int size, String status, String search) {
        Pageable pageable = PageRequest.of(page, size);
        Page<SuspenseEntry> entryPage;

        if (search != null && !search.isBlank()) {
            entryPage = suspenseEntryRepository.search(search, pageable);
        } else if (status != null && !status.isBlank()) {
            entryPage = suspenseEntryRepository.findByStatusOrderByReceivedDateDesc(
                    SuspenseStatus.valueOf(status), pageable);
        } else {
            entryPage = suspenseEntryRepository.findAllByOrderByReceivedDateDesc(pageable);
        }

        List<SuspenseEntryDTO> content = entryPage.getContent().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());

        return PagedResponse.<SuspenseEntryDTO>builder()
                .content(content)
                .page(entryPage.getNumber())
                .size(entryPage.getSize())
                .totalElements(entryPage.getTotalElements())
                .totalPages(entryPage.getTotalPages())
                .last(entryPage.isLast())
                .build();
    }

    public SuspenseEntryDTO getById(Long suspenseId) {
        SuspenseEntry entry = suspenseEntryRepository.findById(suspenseId)
                .orElseThrow(() -> new ResourceNotFoundException("SuspenseEntry", "suspenseId", suspenseId));

        SuspenseEntryDTO dto = mapToDTO(entry);
        // Include audit trail for detail view
        List<SuspenseAuditDTO> audits = auditTrailRepository
                .findBySuspenseEntry_SuspenseIdOrderByPerformedOnDesc(suspenseId)
                .stream().map(this::mapToAuditDTO).collect(Collectors.toList());
        dto.setAuditTrail(audits);
        return dto;
    }

    public List<SuspenseEntryDTO> getUnassignedEntries() {
        return suspenseEntryRepository.findByStatusOrderByReceivedDateDesc(SuspenseStatus.UNASSIGNED)
                .stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    public List<SuspenseEntryDTO> getEntriesAssignedToUnit(Long unitId) {
        return suspenseEntryRepository.findByAssignedToUnit_UnitIdOrderByAssignedOnDesc(unitId)
                .stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    public Map<String, Object> getSummary() {
        long unassignedCount = suspenseEntryRepository.countByStatus(SuspenseStatus.UNASSIGNED);
        long assignedCount = suspenseEntryRepository.countByStatus(SuspenseStatus.ASSIGNED);
        long reversedCount = suspenseEntryRepository.countByStatus(SuspenseStatus.REVERSED);
        BigDecimal unassignedAmount = suspenseEntryRepository.getTotalAmountByStatus(SuspenseStatus.UNASSIGNED);
        BigDecimal assignedAmount = suspenseEntryRepository.getTotalAmountByStatus(SuspenseStatus.ASSIGNED);
        long totalEntries = suspenseEntryRepository.count();

        return Map.of(
                "totalEntries", totalEntries,
                "unassignedCount", unassignedCount,
                "unassignedAmount", unassignedAmount != null ? unassignedAmount : BigDecimal.ZERO,
                "assignedCount", assignedCount,
                "assignedAmount", assignedAmount != null ? assignedAmount : BigDecimal.ZERO,
                "reversedCount", reversedCount
        );
    }

    // ==================== HELPERS ====================

    private void createAudit(SuspenseEntry entry, SuspenseAction action, Long unitId,
                              String unitNumber, String performedBy, String reason) {
        SuspenseAuditTrail audit = SuspenseAuditTrail.builder()
                .suspenseEntry(entry)
                .action(action)
                .unitId(unitId)
                .unitNumber(unitNumber)
                .performedBy(performedBy)
                .performedOn(LocalDateTime.now())
                .reason(reason)
                .build();
        auditTrailRepository.save(audit);
    }

    private SuspenseEntryDTO mapToDTO(SuspenseEntry entry) {
        return SuspenseEntryDTO.builder()
                .suspenseId(entry.getSuspenseId())
                .amount(entry.getAmount())
                .receivedDate(entry.getReceivedDate())
                .paymentMode(entry.getPaymentMode() != null ? entry.getPaymentMode().name() : null)
                .referenceNumber(entry.getReferenceNumber())
                .description(entry.getDescription())
                .status(entry.getStatus().name())
                .assignedToUnitId(entry.getAssignedToUnit() != null ? entry.getAssignedToUnit().getUnitId() : null)
                .assignedToUnitNumber(entry.getAssignedToUnit() != null ? entry.getAssignedToUnit().getUnitNumber() : null)
                .assignedToOwnerName(entry.getAssignedToUnit() != null ? entry.getAssignedToUnit().getOwnerNames() : null)
                .assignedBy(entry.getAssignedBy())
                .assignedOn(entry.getAssignedOn())
                .assignmentRemarks(entry.getAssignmentRemarks())
                .applyToOpeningBalance(entry.getApplyToOpeningBalance())
                .createdOn(entry.getCreatedOn())
                .createdBy(entry.getCreatedBy())
                .auditTrail(Collections.emptyList())
                .build();
    }

    private SuspenseAuditDTO mapToAuditDTO(SuspenseAuditTrail audit) {
        return SuspenseAuditDTO.builder()
                .auditId(audit.getAuditId())
                .action(audit.getAction().name())
                .unitId(audit.getUnitId())
                .unitNumber(audit.getUnitNumber())
                .performedBy(audit.getPerformedBy())
                .performedOn(audit.getPerformedOn())
                .reason(audit.getReason())
                .build();
    }
}
