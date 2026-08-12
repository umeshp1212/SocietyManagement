package com.society.module.tenant.service;

import com.society.common.PagedResponse;
import com.society.enums.*;
import com.society.exception.BusinessException;
import com.society.exception.ResourceNotFoundException;
import com.society.module.owner.entity.Unit;
import com.society.module.owner.repository.UnitRepository;
import com.society.module.tenant.dto.*;
import com.society.module.tenant.entity.*;
import com.society.module.tenant.repository.TenantDocumentRepository;
import com.society.module.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantDocumentRepository tenantDocumentRepository;
    private final UnitRepository unitRepository;

    // ==================== TENANT REGISTRATION ====================

    @Transactional
    public TenantDTO registerTenant(TenantCreateRequest request) {
        Unit unit = unitRepository.findById(request.getUnitId())
                .orElseThrow(() -> new ResourceNotFoundException("Unit", "unitId", request.getUnitId()));

        // Check no active tenant on this unit
        Optional<Tenant> activeTenant = tenantRepository.findActiveByUnitId(
                request.getUnitId(), TenantStatus.ACTIVE);
        if (activeTenant.isPresent()) {
            throw new BusinessException("Unit " + unit.getUnitNumber() +
                    " already has an active tenant. Please vacate the current tenant first.");
        }

        Tenant tenant = Tenant.builder()
                .unit(unit)
                .tenantName(request.getTenantName())
                .contactNumber(request.getContactNumber())
                .email(request.getEmail())
                .aadharNumber(request.getAadharNumber())
                .panNumber(request.getPanNumber())
                .permanentAddress(request.getPermanentAddress())
                .rentStartDate(request.getRentStartDate())
                .rentEndDate(request.getRentEndDate())
                .monthlyRentAmount(request.getMonthlyRentAmount())
                .securityDeposit(request.getSecurityDeposit())
                .policeVerificationStatus(PoliceVerificationStatus.NOT_INITIATED)
                .nocStatus(NocStatus.PENDING)
                .status(TenantStatus.ACTIVE)
                .build();

        // Add family members
        if (request.getFamilyMembers() != null) {
            for (FamilyMemberDTO fm : request.getFamilyMembers()) {
                TenantFamilyMember member = TenantFamilyMember.builder()
                        .tenant(tenant)
                        .memberName(fm.getMemberName())
                        .age(fm.getAge())
                        .relation(fm.getRelation())
                        .aadharNumber(fm.getAadharNumber())
                        .contactNumber(fm.getContactNumber())
                        .build();
                tenant.getFamilyMembers().add(member);
            }
        }

        // Add vehicles
        if (request.getVehicles() != null) {
            for (VehicleDTO v : request.getVehicles()) {
                TenantVehicle vehicle = TenantVehicle.builder()
                        .tenant(tenant)
                        .vehicleType(v.getVehicleType())
                        .vehicleNumber(v.getVehicleNumber())
                        .parkingSlot(v.getParkingSlot())
                        .build();
                tenant.getVehicles().add(vehicle);
            }
        }

        tenant = tenantRepository.save(tenant);

        // Update unit occupancy status
        unit.setOccupancyStatus(OccupancyStatus.RENTED);
        unitRepository.save(unit);

        return mapToDTO(tenant);
    }

    // ==================== TENANT UPDATE ====================

    @Transactional
    public TenantDTO updateTenant(Long tenantId, TenantUpdateRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", "tenantId", tenantId));

        tenant.setTenantName(request.getTenantName());
        tenant.setContactNumber(request.getContactNumber());
        tenant.setEmail(request.getEmail());
        tenant.setAadharNumber(request.getAadharNumber());
        tenant.setPanNumber(request.getPanNumber());
        tenant.setPermanentAddress(request.getPermanentAddress());
        tenant.setRentEndDate(request.getRentEndDate());
        tenant.setMonthlyRentAmount(request.getMonthlyRentAmount());
        tenant.setSecurityDeposit(request.getSecurityDeposit());

        // Update family members - clear and re-add
        if (request.getFamilyMembers() != null) {
            tenant.getFamilyMembers().clear();
            for (FamilyMemberDTO fm : request.getFamilyMembers()) {
                TenantFamilyMember member = TenantFamilyMember.builder()
                        .tenant(tenant)
                        .memberName(fm.getMemberName())
                        .age(fm.getAge())
                        .relation(fm.getRelation())
                        .aadharNumber(fm.getAadharNumber())
                        .contactNumber(fm.getContactNumber())
                        .build();
                tenant.getFamilyMembers().add(member);
            }
        }

        // Update vehicles - clear and re-add
        if (request.getVehicles() != null) {
            tenant.getVehicles().clear();
            for (VehicleDTO v : request.getVehicles()) {
                TenantVehicle vehicle = TenantVehicle.builder()
                        .tenant(tenant)
                        .vehicleType(v.getVehicleType())
                        .vehicleNumber(v.getVehicleNumber())
                        .parkingSlot(v.getParkingSlot())
                        .build();
                tenant.getVehicles().add(vehicle);
            }
        }

        tenant = tenantRepository.save(tenant);
        return mapToDTO(tenant);
    }

    // ==================== NOC APPROVAL ====================

    @Transactional
    public TenantDTO updateNocStatus(Long tenantId, NocApprovalRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", "tenantId", tenantId));

        tenant.setNocStatus(request.getNocStatus());
        if (request.getNocStatus() == NocStatus.APPROVED) {
            tenant.setNocApprovedBy("SYSTEM"); // Will be replaced with actual user
            tenant.setNocApprovedOn(LocalDateTime.now());
        }

        tenant = tenantRepository.save(tenant);
        return mapToDTO(tenant);
    }

    // ==================== POLICE VERIFICATION ====================

    @Transactional
    public TenantDTO updatePoliceVerificationStatus(Long tenantId, PoliceVerificationStatus status) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", "tenantId", tenantId));

        tenant.setPoliceVerificationStatus(status);
        tenant = tenantRepository.save(tenant);
        return mapToDTO(tenant);
    }

    // ==================== MOVE OUT ====================

    @Transactional
    public TenantDTO moveOutTenant(Long tenantId, MoveOutRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", "tenantId", tenantId));

        if (tenant.getStatus() == TenantStatus.VACATED) {
            throw new BusinessException("Tenant has already vacated");
        }

        tenant.setStatus(TenantStatus.VACATED);
        tenant.setMoveOutDate(request.getMoveOutDate());
        tenant.setMoveOutReason(request.getMoveOutReason());
        tenant = tenantRepository.save(tenant);

        // Update unit occupancy - check if owner is self-occupying or vacant
        Unit unit = tenant.getUnit();
        unit.setOccupancyStatus(OccupancyStatus.VACANT);
        unitRepository.save(unit);

        return mapToDTO(tenant);
    }

    @Transactional
    public TenantDTO markNoticePeriod(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", "tenantId", tenantId));

        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new BusinessException("Only active tenants can be marked for notice period");
        }

        tenant.setStatus(TenantStatus.NOTICE_PERIOD);
        tenant = tenantRepository.save(tenant);
        return mapToDTO(tenant);
    }

    // ==================== QUERIES ====================

    public TenantDTO getTenantById(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", "tenantId", tenantId));
        return mapToDTO(tenant);
    }

    public PagedResponse<TenantDTO> getAllTenants(int page, int size, String status, String nocStatus, String search) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("tenantName").ascending());
        Page<Tenant> tenantPage;

        if (search != null && !search.isBlank()) {
            if (status != null && !status.isBlank()) {
                tenantPage = tenantRepository.searchTenantsByStatus(
                        TenantStatus.valueOf(status), search, pageable);
            } else {
                tenantPage = tenantRepository.searchTenants(search, pageable);
            }
        } else if (status != null && !status.isBlank()) {
            tenantPage = tenantRepository.findByStatus(TenantStatus.valueOf(status), pageable);
        } else if (nocStatus != null && !nocStatus.isBlank()) {
            tenantPage = tenantRepository.findByNocStatus(NocStatus.valueOf(nocStatus), pageable);
        } else {
            tenantPage = tenantRepository.findAll(pageable);
        }

        List<TenantDTO> content = tenantPage.getContent().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());

        return PagedResponse.<TenantDTO>builder()
                .content(content)
                .page(tenantPage.getNumber())
                .size(tenantPage.getSize())
                .totalElements(tenantPage.getTotalElements())
                .totalPages(tenantPage.getTotalPages())
                .last(tenantPage.isLast())
                .build();
    }

    public List<TenantDTO> getTenantHistoryByUnit(Long unitId) {
        unitRepository.findById(unitId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit", "unitId", unitId));

        return tenantRepository.findAllByUnitIdOrderByRentStartDateDesc(unitId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public TenantDTO getActiveTenantByUnit(Long unitId) {
        Tenant tenant = tenantRepository.findActiveByUnitId(unitId, TenantStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("No active tenant found for unit ID: " + unitId));
        return mapToDTO(tenant);
    }

    // ==================== ALERTS ====================

    public List<TenantDTO> getTenantsWithExpiringAgreements(int daysAhead) {
        LocalDate today = LocalDate.now();
        return tenantRepository.findTenantsWithExpiringAgreements(today, today.plusDays(daysAhead))
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<TenantDTO> getTenantsWithPendingPoliceVerification() {
        LocalDate cutoff = LocalDate.now().minusDays(15);
        return tenantRepository.findTenantsWithPendingPoliceVerification(cutoff)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // ==================== SUMMARY ====================

    public Map<String, Object> getTenantSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalActive", tenantRepository.countByStatus(TenantStatus.ACTIVE));
        summary.put("totalNoticePeriod", tenantRepository.countByStatus(TenantStatus.NOTICE_PERIOD));
        summary.put("totalVacated", tenantRepository.countByStatus(TenantStatus.VACATED));
        summary.put("pendingNoc", tenantRepository.countByNocStatus(NocStatus.PENDING));
        summary.put("pendingPoliceVerification",
                tenantRepository.countByPoliceVerificationStatus(PoliceVerificationStatus.NOT_INITIATED) +
                tenantRepository.countByPoliceVerificationStatus(PoliceVerificationStatus.SUBMITTED));
        summary.put("expiringAgreements30Days",
                tenantRepository.findTenantsWithExpiringAgreements(
                        LocalDate.now(), LocalDate.now().plusDays(30)).size());
        return summary;
    }

    // ==================== DOCUMENTS ====================

    @Transactional
    public TenantDocumentDTO addDocument(Long tenantId, String documentName, String documentType, String filePath) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", "tenantId", tenantId));

        TenantDocument document = TenantDocument.builder()
                .tenant(tenant)
                .documentName(documentName)
                .documentType(documentType)
                .filePath(filePath)
                .uploadedBy("SYSTEM")
                .uploadedOn(LocalDateTime.now())
                .build();

        document = tenantDocumentRepository.save(document);
        return mapToDocumentDTO(document);
    }

    public List<TenantDocumentDTO> getTenantDocuments(Long tenantId) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", "tenantId", tenantId));

        return tenantDocumentRepository.findByTenant_TenantIdOrderByUploadedOnDesc(tenantId)
                .stream()
                .map(this::mapToDocumentDTO)
                .collect(Collectors.toList());
    }

    // ==================== MAPPERS ====================

    private TenantDTO mapToDTO(Tenant tenant) {
        Long daysUntilExpiry = null;
        Boolean isExpired = null;
        if (tenant.getRentEndDate() != null) {
            long days = ChronoUnit.DAYS.between(LocalDate.now(), tenant.getRentEndDate());
            daysUntilExpiry = days;
            isExpired = days < 0;
        }

        List<FamilyMemberDTO> familyMembers = tenant.getFamilyMembers() != null
                ? tenant.getFamilyMembers().stream().map(this::mapToFamilyMemberDTO).collect(Collectors.toList())
                : Collections.emptyList();

        List<VehicleDTO> vehicles = tenant.getVehicles() != null
                ? tenant.getVehicles().stream().map(this::mapToVehicleDTO).collect(Collectors.toList())
                : Collections.emptyList();

        List<TenantDocumentDTO> documents = tenant.getDocuments() != null
                ? tenant.getDocuments().stream().map(this::mapToDocumentDTO).collect(Collectors.toList())
                : Collections.emptyList();

        return TenantDTO.builder()
                .tenantId(tenant.getTenantId())
                .unitId(tenant.getUnit().getUnitId())
                .unitNumber(tenant.getUnit().getUnitNumber())
                .ownerName(tenant.getUnit().getPrimaryOwner() != null
                        ? tenant.getUnit().getPrimaryOwner().getFullName() : null)
                .tenantName(tenant.getTenantName())
                .contactNumber(tenant.getContactNumber())
                .email(tenant.getEmail())
                .aadharNumber(tenant.getAadharNumber())
                .panNumber(tenant.getPanNumber())
                .permanentAddress(tenant.getPermanentAddress())
                .photoPath(tenant.getPhotoPath())
                .rentStartDate(tenant.getRentStartDate())
                .rentEndDate(tenant.getRentEndDate())
                .monthlyRentAmount(tenant.getMonthlyRentAmount())
                .securityDeposit(tenant.getSecurityDeposit())
                .agreementDocumentPath(tenant.getAgreementDocumentPath())
                .policeVerificationStatus(tenant.getPoliceVerificationStatus())
                .policeVerificationDocumentPath(tenant.getPoliceVerificationDocumentPath())
                .nocStatus(tenant.getNocStatus())
                .nocDocumentPath(tenant.getNocDocumentPath())
                .nocApprovedBy(tenant.getNocApprovedBy())
                .nocApprovedOn(tenant.getNocApprovedOn())
                .status(tenant.getStatus())
                .moveOutDate(tenant.getMoveOutDate())
                .moveOutReason(tenant.getMoveOutReason())
                .familyMembers(familyMembers)
                .vehicles(vehicles)
                .documents(documents)
                .createdBy(tenant.getCreatedBy())
                .createdOn(tenant.getCreatedOn())
                .daysUntilAgreementExpiry(daysUntilExpiry)
                .isAgreementExpired(isExpired)
                .build();
    }

    private FamilyMemberDTO mapToFamilyMemberDTO(TenantFamilyMember member) {
        return FamilyMemberDTO.builder()
                .memberId(member.getMemberId())
                .memberName(member.getMemberName())
                .age(member.getAge())
                .relation(member.getRelation())
                .aadharNumber(member.getAadharNumber())
                .contactNumber(member.getContactNumber())
                .build();
    }

    private VehicleDTO mapToVehicleDTO(TenantVehicle vehicle) {
        return VehicleDTO.builder()
                .vehicleId(vehicle.getVehicleId())
                .vehicleType(vehicle.getVehicleType())
                .vehicleNumber(vehicle.getVehicleNumber())
                .parkingSlot(vehicle.getParkingSlot())
                .build();
    }

    private TenantDocumentDTO mapToDocumentDTO(TenantDocument doc) {
        return TenantDocumentDTO.builder()
                .documentId(doc.getDocumentId())
                .documentName(doc.getDocumentName())
                .documentType(doc.getDocumentType())
                .filePath(doc.getFilePath())
                .uploadedBy(doc.getUploadedBy())
                .uploadedOn(doc.getUploadedOn())
                .build();
    }
}
