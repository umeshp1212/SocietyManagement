package com.society.module.owner.service;

import com.society.common.PagedResponse;
import com.society.enums.OccupancyStatus;
import com.society.enums.OwnerStatus;
import com.society.enums.UnitType;
import com.society.exception.BusinessException;
import com.society.exception.ResourceNotFoundException;
import com.society.module.owner.dto.AddCoOwnerRequest;
import com.society.module.owner.dto.UnitCreateRequest;
import com.society.module.owner.dto.UnitDTO;
import com.society.module.owner.dto.UnitOwnerDTO;
import com.society.module.owner.entity.Owner;
import com.society.module.owner.entity.Unit;
import com.society.module.owner.entity.UnitOwner;
import com.society.module.owner.repository.OwnerRepository;
import com.society.module.owner.repository.UnitOwnerRepository;
import com.society.module.owner.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UnitService {

    private final UnitRepository unitRepository;
    private final OwnerRepository ownerRepository;
    private final UnitOwnerRepository unitOwnerRepository;

    private static final int MAX_OWNERS_PER_UNIT = 4;

    // ==================== UNIT CRUD ====================

    @Transactional
    public UnitDTO createUnit(UnitCreateRequest request) {
        if (unitRepository.existsByUnitNumber(request.getUnitNumber())) {
            throw new BusinessException("Unit number '" + request.getUnitNumber() + "' already exists");
        }

        Unit unit = Unit.builder()
                .unitNumber(request.getUnitNumber())
                .wing(request.getWing())
                .floor(request.getFloor())
                .unitType(request.getUnitType())
                .bhkType(request.getBhkType())
                .areaSqft(request.getAreaSqft())
                .monthlyMaintenanceAmount(request.getMonthlyMaintenanceAmount())
                .waterCharges(request.getWaterCharges())
                .parkingType(request.getParkingType() != null ? request.getParkingType()
                        : com.society.enums.ParkingType.NONE)
                .occupancyStatus(OccupancyStatus.VACANT)
                .status("ACTIVE")
                .build();

        unit = unitRepository.save(unit);
        return mapToDTO(unit);
    }

    @Transactional
    public UnitDTO updateUnit(Long unitId, UnitCreateRequest request) {
        Unit unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit", "unitId", unitId));

        if (!unit.getUnitNumber().equals(request.getUnitNumber())
                && unitRepository.existsByUnitNumber(request.getUnitNumber())) {
            throw new BusinessException("Unit number '" + request.getUnitNumber() + "' already exists");
        }

        unit.setUnitNumber(request.getUnitNumber());
        unit.setWing(request.getWing());
        unit.setFloor(request.getFloor());
        unit.setUnitType(request.getUnitType());
        unit.setBhkType(request.getBhkType());
        unit.setAreaSqft(request.getAreaSqft());
        unit.setMonthlyMaintenanceAmount(request.getMonthlyMaintenanceAmount());
        unit.setWaterCharges(request.getWaterCharges());
        if (request.getParkingType() != null) {
            unit.setParkingType(request.getParkingType());
        }

        unit = unitRepository.save(unit);
        return mapToDTO(unit);
    }

    public UnitDTO getUnitById(Long unitId) {
        Unit unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit", "unitId", unitId));
        return mapToDTO(unit);
    }

    public PagedResponse<UnitDTO> getAllUnits(int page, int size, String wing, String unitType, String occupancyStatus) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("unitId").ascending());
        Page<Unit> unitPage;

        boolean hasWing = wing != null && !wing.isBlank();
        boolean hasType = unitType != null && !unitType.isBlank();
        boolean hasOccupancy = occupancyStatus != null && !occupancyStatus.isBlank();

        if (hasOccupancy && hasType) {
            // Both filters - use combined query
            unitPage = unitRepository.findByUnitTypeAndOccupancyStatus(
                    UnitType.valueOf(unitType), OccupancyStatus.valueOf(occupancyStatus), pageable);
        } else if (hasWing) {
            unitPage = unitRepository.findByWing(wing, pageable);
        } else if (hasType) {
            unitPage = unitRepository.findByUnitType(UnitType.valueOf(unitType), pageable);
        } else if (hasOccupancy) {
            unitPage = unitRepository.findByOccupancyStatus(OccupancyStatus.valueOf(occupancyStatus), pageable);
        } else {
            unitPage = unitRepository.findAll(pageable);
        }

        List<UnitDTO> content = unitPage.getContent().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());

        return PagedResponse.<UnitDTO>builder()
                .content(content)
                .page(unitPage.getNumber())
                .size(unitPage.getSize())
                .totalElements(unitPage.getTotalElements())
                .totalPages(unitPage.getTotalPages())
                .last(unitPage.isLast())
                .build();
    }

    public List<UnitDTO> getUnitsByOwner(Long ownerId) {
        return unitRepository.findByOwnerId(ownerId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<UnitDTO> getVacantUnits() {
        return unitRepository.findUnitsWithNoOwners()
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // ==================== CO-OWNER MANAGEMENT ====================

    @Transactional
    public UnitDTO addOwnerToUnit(AddCoOwnerRequest request) {
        Unit unit = unitRepository.findById(request.getUnitId())
                .orElseThrow(() -> new ResourceNotFoundException("Unit", "unitId", request.getUnitId()));

        Owner owner = ownerRepository.findById(request.getOwnerId())
                .orElseThrow(() -> new ResourceNotFoundException("Owner", "ownerId", request.getOwnerId()));

        if (owner.getStatus() != OwnerStatus.ACTIVE) {
            throw new BusinessException("Owner must have ACTIVE status to be added to a unit");
        }

        // Check max owners
        long currentOwnerCount = unitOwnerRepository.countByUnit_UnitId(unit.getUnitId());
        if (currentOwnerCount >= MAX_OWNERS_PER_UNIT) {
            throw new BusinessException("Maximum " + MAX_OWNERS_PER_UNIT + " owners allowed per unit. " +
                    "This unit already has " + currentOwnerCount + " owner(s).");
        }

        // Check if owner already linked to this unit
        if (unitOwnerRepository.existsByUnit_UnitIdAndOwner_OwnerId(unit.getUnitId(), owner.getOwnerId())) {
            throw new BusinessException("Owner '" + owner.getFullName() + "' is already linked to unit " + unit.getUnitNumber());
        }

        // If this is marked as primary, unmark existing primary
        if (Boolean.TRUE.equals(request.getIsPrimary())) {
            unitOwnerRepository.findPrimaryOwnerByUnitId(unit.getUnitId())
                    .ifPresent(existing -> {
                        existing.setIsPrimary(false);
                        unitOwnerRepository.save(existing);
                    });
        }

        // If this is the first owner, make them primary automatically
        boolean makePrimary = request.getIsPrimary() || currentOwnerCount == 0;

        UnitOwner unitOwner = UnitOwner.builder()
                .unit(unit)
                .owner(owner)
                .isPrimary(makePrimary)
                .ownershipPercentage(request.getOwnershipPercentage())
                .addedOn(LocalDateTime.now())
                .addedBy("SYSTEM")
                .build();

        unitOwnerRepository.save(unitOwner);

        // Update unit occupancy if it was vacant
        if (unit.getOccupancyStatus() == OccupancyStatus.VACANT) {
            unit.setOccupancyStatus(OccupancyStatus.SELF_OCCUPIED);
            unitRepository.save(unit);
        }

        // Reload to get fresh data
        unit = unitRepository.findById(unit.getUnitId()).orElseThrow();
        return mapToDTO(unit);
    }

    @Transactional
    public UnitDTO removeOwnerFromUnit(Long unitId, Long ownerId) {
        Unit unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit", "unitId", unitId));

        UnitOwner unitOwner = unitOwnerRepository.findByUnit_UnitIdAndOwner_OwnerId(unitId, ownerId)
                .orElseThrow(() -> new BusinessException("Owner is not linked to this unit"));

        boolean wasPrimary = unitOwner.getIsPrimary();
        unitOwnerRepository.delete(unitOwner);

        // If removed owner was primary, make another one primary
        if (wasPrimary) {
            List<UnitOwner> remainingOwners = unitOwnerRepository.findByUnit_UnitId(unitId);
            if (!remainingOwners.isEmpty()) {
                UnitOwner newPrimary = remainingOwners.get(0);
                newPrimary.setIsPrimary(true);
                unitOwnerRepository.save(newPrimary);
            }
        }

        // If no owners left, mark as vacant
        long remaining = unitOwnerRepository.countByUnit_UnitId(unitId);
        if (remaining == 0) {
            unit.setOccupancyStatus(OccupancyStatus.VACANT);
            unitRepository.save(unit);
        }

        unit = unitRepository.findById(unitId).orElseThrow();
        return mapToDTO(unit);
    }

    public List<UnitOwnerDTO> getUnitOwners(Long unitId) {
        unitRepository.findById(unitId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit", "unitId", unitId));

        return unitOwnerRepository.findByUnit_UnitId(unitId)
                .stream()
                .map(this::mapToUnitOwnerDTO)
                .collect(Collectors.toList());
    }

    // ==================== SUMMARY ====================

    public Map<String, Long> getOccupancySummary() {
        Map<String, Long> summary = new HashMap<>();
        summary.put("selfOccupied", unitRepository.countByOccupancyStatus(OccupancyStatus.SELF_OCCUPIED));
        summary.put("rented", unitRepository.countByOccupancyStatus(OccupancyStatus.RENTED));
        summary.put("vacant", unitRepository.countByOccupancyStatus(OccupancyStatus.VACANT));
        summary.put("totalFlats", unitRepository.countByUnitType(UnitType.FLAT));
        summary.put("totalShops", unitRepository.countByUnitType(UnitType.SHOP));
        summary.put("total", unitRepository.count());
        return summary;
    }

    // ==================== MAPPERS ====================

    private UnitDTO mapToDTO(Unit unit) {
        List<UnitOwnerDTO> ownerDTOs = unit.getUnitOwners() != null
                ? unit.getUnitOwners().stream().map(this::mapToUnitOwnerDTO).collect(Collectors.toList())
                : Collections.emptyList();

        Owner primaryOwner = unit.getPrimaryOwner();

        return UnitDTO.builder()
                .unitId(unit.getUnitId())
                .unitNumber(unit.getUnitNumber())
                .wing(unit.getWing())
                .floor(unit.getFloor())
                .unitType(unit.getUnitType())
                .bhkType(unit.getBhkType())
                .areaSqft(unit.getAreaSqft())
                .monthlyMaintenanceAmount(unit.getMonthlyMaintenanceAmount())
                .waterCharges(unit.getWaterCharges())
                .parkingType(unit.getParkingType())
                .primaryOwnerName(primaryOwner != null ? primaryOwner.getFullName() : null)
                .allOwnerNames(unit.getOwnerNames())
                .owners(ownerDTOs)
                .occupancyStatus(unit.getOccupancyStatus())
                .status(unit.getStatus())
                .build();
    }

    private UnitOwnerDTO mapToUnitOwnerDTO(UnitOwner uo) {
        return UnitOwnerDTO.builder()
                .id(uo.getId())
                .ownerId(uo.getOwner().getOwnerId())
                .ownerName(uo.getOwner().getFullName())
                .ownerContact(uo.getOwner().getContactNumber())
                .isPrimary(uo.getIsPrimary())
                .ownershipPercentage(uo.getOwnershipPercentage())
                .addedOn(uo.getAddedOn())
                .build();
    }
}
