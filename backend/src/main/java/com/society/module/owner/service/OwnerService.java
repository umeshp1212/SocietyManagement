package com.society.module.owner.service;

import com.society.common.PagedResponse;
import com.society.enums.OccupancyStatus;
import com.society.enums.OwnerStatus;
import com.society.exception.BusinessException;
import com.society.exception.ResourceNotFoundException;
import com.society.module.owner.dto.*;
import com.society.module.owner.entity.Owner;
import com.society.module.owner.entity.OwnershipHistory;
import com.society.module.owner.entity.Unit;
import com.society.module.owner.entity.UnitOwner;
import com.society.module.owner.repository.OwnerRepository;
import com.society.module.owner.repository.OwnershipHistoryRepository;
import com.society.module.owner.repository.UnitOwnerRepository;
import com.society.module.owner.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OwnerService {

    private final OwnerRepository ownerRepository;
    private final UnitRepository unitRepository;
    private final UnitOwnerRepository unitOwnerRepository;
    private final OwnershipHistoryRepository ownershipHistoryRepository;

    // ==================== OWNER CRUD ====================

    @Transactional
    public OwnerDTO createOwner(OwnerCreateRequest request) {
        Owner owner = Owner.builder()
                .fullName(request.getFullName())
                .contactNumber(request.getContactNumber())
                .alternateNumber(request.getAlternateNumber())
                .email(request.getEmail())
                .aadharNumber(request.getAadharNumber())
                .panNumber(request.getPanNumber())
                .permanentAddress(request.getPermanentAddress())
                .occupation(request.getOccupation())
                .emergencyContactName(request.getEmergencyContactName())
                .emergencyContactPhone(request.getEmergencyContactPhone())
                .status(OwnerStatus.ACTIVE)
                .build();

        owner = ownerRepository.save(owner);
        return mapToDTO(owner);
    }

    @Transactional
    public OwnerDTO updateOwner(Long ownerId, OwnerUpdateRequest request) {
        Owner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Owner", "ownerId", ownerId));

        owner.setFullName(request.getFullName());
        owner.setContactNumber(request.getContactNumber());
        owner.setAlternateNumber(request.getAlternateNumber());
        owner.setEmail(request.getEmail());
        owner.setAadharNumber(request.getAadharNumber());
        owner.setPanNumber(request.getPanNumber());
        owner.setPermanentAddress(request.getPermanentAddress());
        owner.setOccupation(request.getOccupation());
        owner.setEmergencyContactName(request.getEmergencyContactName());
        owner.setEmergencyContactPhone(request.getEmergencyContactPhone());

        owner = ownerRepository.save(owner);
        return mapToDTO(owner);
    }

    public OwnerDTO getOwnerById(Long ownerId) {
        Owner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Owner", "ownerId", ownerId));
        return mapToDTO(owner);
    }

    public PagedResponse<OwnerDTO> getAllOwners(int page, int size, String status, String search) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("ownerId").ascending());
        Page<Owner> ownerPage;

        if (search != null && !search.isBlank()) {
            if (status != null && !status.isBlank()) {
                ownerPage = ownerRepository.searchOwnersWithUnits(OwnerStatus.valueOf(status), search, pageable);
            } else {
                ownerPage = ownerRepository.searchAllOwnersWithUnits(search, pageable);
            }
        } else if (status != null && !status.isBlank()) {
            ownerPage = ownerRepository.searchOwnersWithUnits(OwnerStatus.valueOf(status), "", pageable);
        } else {
            ownerPage = ownerRepository.searchAllOwnersWithUnits("", pageable);
        }

        List<OwnerDTO> content = ownerPage.getContent().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());

        return PagedResponse.<OwnerDTO>builder()
                .content(content)
                .page(ownerPage.getNumber())
                .size(ownerPage.getSize())
                .totalElements(ownerPage.getTotalElements())
                .totalPages(ownerPage.getTotalPages())
                .last(ownerPage.isLast())
                .build();
    }

    public List<OwnerDTO> getActiveOwnersList() {
        return ownerRepository.findByStatusOrderByFullNameAsc(OwnerStatus.ACTIVE)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // ==================== OWNERSHIP TRANSFER ====================

    @Transactional
    public OwnershipHistoryDTO transferOwnership(OwnershipTransferRequest request) {
        Unit unit = unitRepository.findById(request.getUnitId())
                .orElseThrow(() -> new ResourceNotFoundException("Unit", "unitId", request.getUnitId()));

        Owner newOwner = ownerRepository.findById(request.getNewOwnerId())
                .orElseThrow(() -> new ResourceNotFoundException("Owner", "ownerId", request.getNewOwnerId()));

        if (newOwner.getStatus() != OwnerStatus.ACTIVE) {
            throw new BusinessException("New owner must have ACTIVE status for transfer");
        }

        // Close current ownership history record(s) for ALL owners of this unit
        List<UnitOwner> existingUnitOwners = unitOwnerRepository.findByUnit_UnitId(unit.getUnitId());
        Optional<OwnershipHistory> currentHistoryOpt = ownershipHistoryRepository
                .findCurrentOwnershipByUnitId(unit.getUnitId());
        List<OwnershipHistory> currentHistories = currentHistoryOpt
                .map(List::of)
                .orElse(List.of());

        // Mark ALL existing owners as TRANSFERRED (if they don't own any other units)
        for (UnitOwner unitOwner : existingUnitOwners) {
            Owner existingOwner = unitOwner.getOwner();
            List<Unit> otherUnits = unitRepository.findByOwnerId(existingOwner.getOwnerId());
            long otherUnitCount = otherUnits.stream()
                    .filter(u -> !u.getUnitId().equals(unit.getUnitId()))
                    .count();
            if (otherUnitCount == 0) {
                existingOwner.setStatus(OwnerStatus.TRANSFERRED);
                ownerRepository.save(existingOwner);
            }
        }

        // Close all current ownership history records for this unit
        if (currentHistories != null && !currentHistories.isEmpty()) {
            for (OwnershipHistory history : currentHistories) {
                history.setOwnershipEndDate(request.getTransferDate());
                ownershipHistoryRepository.save(history);
            }
        } else {
            // Fallback for units that were assigned an owner before ownership-history
            // was recorded on assignment: reconstruct a closed history row for the
            // existing primary owner so the old owner still appears in the history.
            UnitOwner currentPrimary = unitOwnerRepository.findPrimaryOwnerByUnitId(unit.getUnitId())
                    .orElseGet(() -> existingUnitOwners.stream().findFirst().orElse(null));

            if (currentPrimary != null) {
                OwnershipHistory history = OwnershipHistory.builder()
                        .unit(unit)
                        .owner(currentPrimary.getOwner())
                        .ownershipStartDate(currentPrimary.getAddedOn() != null
                                ? currentPrimary.getAddedOn().toLocalDate()
                                : request.getTransferDate())
                        .ownershipEndDate(request.getTransferDate())
                        .transferType(com.society.enums.TransferType.PURCHASE)
                        .remarks("Initial ownership (recorded at transfer)")
                        .recordedBy("SYSTEM")
                        .recordedOn(LocalDateTime.now())
                        .build();
                ownershipHistoryRepository.save(history);
            }
        }

        // Remove all existing owners from the unit (transfer clears all co-owners)
        if (!existingUnitOwners.isEmpty()) {
            unitOwnerRepository.deleteAll(existingUnitOwners);
        }

        // Add new owner as primary with 100% ownership
        UnitOwner newUnitOwner = UnitOwner.builder()
                .unit(unit)
                .owner(newOwner)
                .isPrimary(true)
                .ownershipPercentage(new java.math.BigDecimal("100.00"))
                .addedOn(LocalDateTime.now())
                .addedBy("SYSTEM")
                .build();
        unitOwnerRepository.save(newUnitOwner);

        // Update unit occupancy
        unit.setOccupancyStatus(OccupancyStatus.SELF_OCCUPIED);
        unitRepository.save(unit);

        // Create new ownership history record
        OwnershipHistory newHistory = OwnershipHistory.builder()
                .unit(unit)
                .owner(newOwner)
                .ownershipStartDate(request.getTransferDate())
                .ownershipEndDate(null)
                .transferType(request.getTransferType())
                .remarks(request.getRemarks())
                .recordedBy("SYSTEM")
                .recordedOn(LocalDateTime.now())
                .build();
        newHistory = ownershipHistoryRepository.save(newHistory);

        return mapToHistoryDTO(newHistory);
    }

    // ==================== OWNERSHIP HISTORY ====================

    public List<OwnershipHistoryDTO> getOwnershipHistoryByUnit(Long unitId) {
        unitRepository.findById(unitId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit", "unitId", unitId));

        return ownershipHistoryRepository.findByUnitIdOrderByStartDateDesc(unitId)
                .stream()
                .map(this::mapToHistoryDTO)
                .collect(Collectors.toList());
    }

    public List<OwnershipHistoryDTO> getOwnershipHistoryByOwner(Long ownerId) {
        ownerRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Owner", "ownerId", ownerId));

        return ownershipHistoryRepository.findByOwnerIdOrderByStartDateDesc(ownerId)
                .stream()
                .map(this::mapToHistoryDTO)
                .collect(Collectors.toList());
    }

    // ==================== MAPPERS ====================

    private OwnerDTO mapToDTO(Owner owner) {
        // Fetch unit numbers owned by this owner
        String unitNumbers = unitOwnerRepository.findByOwner_OwnerId(owner.getOwnerId())
                .stream()
                .map(uo -> uo.getUnit().getUnitNumber())
                .collect(Collectors.joining(", "));

        return OwnerDTO.builder()
                .ownerId(owner.getOwnerId())
                .fullName(owner.getFullName())
                .contactNumber(owner.getContactNumber())
                .alternateNumber(owner.getAlternateNumber())
                .email(owner.getEmail())
                .aadharNumber(owner.getAadharNumber())
                .panNumber(owner.getPanNumber())
                .permanentAddress(owner.getPermanentAddress())
                .occupation(owner.getOccupation())
                .photoPath(owner.getPhotoPath())
                .emergencyContactName(owner.getEmergencyContactName())
                .emergencyContactPhone(owner.getEmergencyContactPhone())
                .status(owner.getStatus())
                .unitNumbers(unitNumbers)
                .createdBy(owner.getCreatedBy())
                .createdOn(owner.getCreatedOn())
                .modifiedBy(owner.getModifiedBy())
                .modifiedOn(owner.getModifiedOn())
                .build();
    }

    private OwnershipHistoryDTO mapToHistoryDTO(OwnershipHistory history) {
        return OwnershipHistoryDTO.builder()
                .historyId(history.getHistoryId())
                .unitId(history.getUnit().getUnitId())
                .unitNumber(history.getUnit().getUnitNumber())
                .ownerId(history.getOwner().getOwnerId())
                .ownerName(history.getOwner().getFullName())
                .ownershipStartDate(history.getOwnershipStartDate())
                .ownershipEndDate(history.getOwnershipEndDate())
                .transferType(history.getTransferType())
                .transferDocumentPath(history.getTransferDocumentPath())
                .remarks(history.getRemarks())
                .recordedBy(history.getRecordedBy())
                .recordedOn(history.getRecordedOn())
                .build();
    }
}
