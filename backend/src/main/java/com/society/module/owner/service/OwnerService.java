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
                ownerPage = ownerRepository.searchOwners(OwnerStatus.valueOf(status), search, pageable);
            } else {
                ownerPage = ownerRepository.searchAllOwners(search, pageable);
            }
        } else if (status != null && !status.isBlank()) {
            ownerPage = ownerRepository.findByStatus(OwnerStatus.valueOf(status), pageable);
        } else {
            ownerPage = ownerRepository.findAll(pageable);
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

        // Close current ownership history record
        OwnershipHistory currentHistory = ownershipHistoryRepository
                .findCurrentOwnershipByUnitId(unit.getUnitId())
                .orElse(null);

        if (currentHistory != null) {
            currentHistory.setOwnershipEndDate(request.getTransferDate());
            ownershipHistoryRepository.save(currentHistory);

            // Mark old owner as TRANSFERRED if they don't own any other units
            Owner oldOwner = currentHistory.getOwner();
            List<Unit> otherUnits = unitRepository.findByOwnerId(oldOwner.getOwnerId());
            long otherUnitCount = otherUnits.stream()
                    .filter(u -> !u.getUnitId().equals(unit.getUnitId()))
                    .count();
            if (otherUnitCount == 0) {
                oldOwner.setStatus(OwnerStatus.TRANSFERRED);
                ownerRepository.save(oldOwner);
            }
        }

        // Remove all existing owners from the unit (transfer clears all co-owners)
        List<UnitOwner> existingOwners = unitOwnerRepository.findByUnit_UnitId(unit.getUnitId());
        unitOwnerRepository.deleteAll(existingOwners);

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
