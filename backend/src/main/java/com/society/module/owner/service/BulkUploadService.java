package com.society.module.owner.service;

import com.society.enums.*;
import com.society.module.owner.dto.BulkUploadResultDTO;
import com.society.module.owner.entity.Owner;
import com.society.module.owner.entity.Unit;
import com.society.module.owner.entity.UnitOwner;
import com.society.module.owner.repository.OwnerRepository;
import com.society.module.owner.repository.UnitOwnerRepository;
import com.society.module.owner.repository.UnitRepository;
import com.society.module.tenant.entity.Tenant;
import com.society.module.tenant.entity.TenantFamilyMember;
import com.society.module.tenant.entity.TenantVehicle;
import com.society.module.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BulkUploadService {

    private final OwnerRepository ownerRepository;
    private final UnitRepository unitRepository;
    private final UnitOwnerRepository unitOwnerRepository;
    private final TenantRepository tenantRepository;

    // ==================== BULK UPLOAD OWNERS ====================

    @Transactional
    public BulkUploadResultDTO bulkUploadOwners(MultipartFile file) {
        BulkUploadResultDTO result = BulkUploadResultDTO.builder()
                .totalRecords(0).successCount(0).failedCount(0).build();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                     .builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreHeaderCase(true)
                     .setTrim(true)
                     .build())) {

            int rowNum = 1;
            for (CSVRecord record : csvParser) {
                rowNum++;
                result.setTotalRecords(result.getTotalRecords() + 1);

                try {
                    processOwnerRecord(record, rowNum, result);
                } catch (Exception e) {
                    result.setFailedCount(result.getFailedCount() + 1);
                    result.getErrors().add("Row " + rowNum + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            result.getErrors().add("File parsing error: " + e.getMessage());
        }

        return result;
    }

    private void processOwnerRecord(CSVRecord record, int rowNum, BulkUploadResultDTO result) {
        String unitNumber = getField(record, "unit_number");
        String fullName = getField(record, "full_name");
        String contactNumber = getField(record, "contact_number");

        // Validate required fields
        if (unitNumber == null || unitNumber.isBlank()) {
            throw new RuntimeException("unit_number is required");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new RuntimeException("full_name is required");
        }

        // Find or create unit
        Unit unit = unitRepository.findByUnitNumber(unitNumber).orElse(null);
        if (unit == null) {
            // Auto-create unit from CSV data
            String wing = null;
            String floor = null;
            String typeStr = "FLAT";

            if (unitNumber.startsWith("S-")) {
                typeStr = "SHOP";
                floor = "G";
            } else {
                // Extract wing and floor from unit number like C-101, D-1401
                String[] parts = unitNumber.split("-");
                if (parts.length == 2) {
                    wing = parts[0];
                    String numPart = parts[1];
                    if (numPart.length() <= 3) {
                        floor = numPart.substring(0, 1);
                    } else {
                        floor = numPart.substring(0, numPart.length() - 2);
                    }
                }
            }

            unit = Unit.builder()
                    .unitNumber(unitNumber)
                    .wing(wing)
                    .floor(floor)
                    .unitType(com.society.enums.UnitType.valueOf(typeStr))
                    .monthlyMaintenanceAmount(java.math.BigDecimal.ZERO)
                    .occupancyStatus(OccupancyStatus.VACANT)
                    .status("ACTIVE")
                    .build();
            unit = unitRepository.save(unit);
        }

        // Check if owner already exists by full_name (and contact if available)
        Owner owner;
        Optional<Owner> existingOwner;
        if (contactNumber != null && !contactNumber.isBlank()) {
            existingOwner = ownerRepository.findAll().stream()
                    .filter(o -> contactNumber.equals(o.getContactNumber()))
                    .findFirst();
        } else {
            existingOwner = ownerRepository.findAll().stream()
                    .filter(o -> o.getFullName().equalsIgnoreCase(fullName))
                    .findFirst();
        }

        if (existingOwner.isPresent()) {
            owner = existingOwner.get();
            // Update fields if provided
            updateOwnerFields(owner, record);
            ownerRepository.save(owner);
        } else {
            // Create new owner
            owner = Owner.builder()
                    .fullName(fullName)
                    .contactNumber(contactNumber)
                    .alternateNumber(getField(record, "alternate_number"))
                    .email(getField(record, "email"))
                    .aadharNumber(getField(record, "aadhar_number"))
                    .panNumber(getField(record, "pan_number"))
                    .permanentAddress(getField(record, "permanent_address"))
                    .occupation(getField(record, "occupation"))
                    .emergencyContactName(getField(record, "emergency_contact_name"))
                    .emergencyContactPhone(getField(record, "emergency_contact_phone"))
                    .status(OwnerStatus.ACTIVE)
                    .build();
            owner = ownerRepository.save(owner);
        }

        // Link owner to unit (if not already linked)
        if (!unitOwnerRepository.existsByUnit_UnitIdAndOwner_OwnerId(unit.getUnitId(), owner.getOwnerId())) {
            // Check max 4 owners
            long currentCount = unitOwnerRepository.countByUnit_UnitId(unit.getUnitId());
            if (currentCount >= 4) {
                throw new RuntimeException("Unit " + unitNumber + " already has 4 owners (max limit)");
            }

            boolean isPrimary = "true".equalsIgnoreCase(getField(record, "is_primary")) || currentCount == 0;
            BigDecimal percentage = parseDecimal(getField(record, "ownership_percentage"), new BigDecimal("100"));

            // If marking as primary, unmark existing
            if (isPrimary) {
                unitOwnerRepository.findPrimaryOwnerByUnitId(unit.getUnitId())
                        .ifPresent(existing -> {
                            existing.setIsPrimary(false);
                            unitOwnerRepository.save(existing);
                        });
            }

            UnitOwner unitOwner = UnitOwner.builder()
                    .unit(unit)
                    .owner(owner)
                    .isPrimary(isPrimary)
                    .ownershipPercentage(percentage)
                    .addedOn(LocalDateTime.now())
                    .addedBy("BULK_UPLOAD")
                    .build();
            unitOwnerRepository.save(unitOwner);

            // Update unit occupancy
            if (unit.getOccupancyStatus() == OccupancyStatus.VACANT) {
                unit.setOccupancyStatus(OccupancyStatus.SELF_OCCUPIED);
                unitRepository.save(unit);
            }
        }

        result.setSuccessCount(result.getSuccessCount() + 1);
        result.getSuccessMessages().add("Row " + rowNum + ": " + fullName + " -> " + unitNumber);
    }

    private void updateOwnerFields(Owner owner, CSVRecord record) {
        String alt = getField(record, "alternate_number");
        if (alt != null && !alt.isBlank()) owner.setAlternateNumber(alt);

        String email = getField(record, "email");
        if (email != null && !email.isBlank()) owner.setEmail(email);

        String address = getField(record, "permanent_address");
        if (address != null && !address.isBlank()) owner.setPermanentAddress(address);

        String occupation = getField(record, "occupation");
        if (occupation != null && !occupation.isBlank()) owner.setOccupation(occupation);

        String ecName = getField(record, "emergency_contact_name");
        if (ecName != null && !ecName.isBlank()) owner.setEmergencyContactName(ecName);

        String ecPhone = getField(record, "emergency_contact_phone");
        if (ecPhone != null && !ecPhone.isBlank()) owner.setEmergencyContactPhone(ecPhone);
    }

    // ==================== BULK UPLOAD TENANTS ====================

    @Transactional
    public BulkUploadResultDTO bulkUploadTenants(MultipartFile file) {
        BulkUploadResultDTO result = BulkUploadResultDTO.builder()
                .totalRecords(0).successCount(0).failedCount(0).build();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                     .builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreHeaderCase(true)
                     .setTrim(true)
                     .build())) {

            int rowNum = 1;
            for (CSVRecord record : csvParser) {
                rowNum++;
                result.setTotalRecords(result.getTotalRecords() + 1);

                try {
                    processTenantRecord(record, rowNum, result);
                } catch (Exception e) {
                    result.setFailedCount(result.getFailedCount() + 1);
                    result.getErrors().add("Row " + rowNum + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            result.getErrors().add("File parsing error: " + e.getMessage());
        }

        return result;
    }

    private void processTenantRecord(CSVRecord record, int rowNum, BulkUploadResultDTO result) {
        String unitNumber = getField(record, "unit_number");
        String tenantName = getField(record, "tenant_name");
        String contactNumber = getField(record, "contact_number");
        String rentStartDateStr = getField(record, "rent_start_date");

        // Validate required
        if (unitNumber == null || unitNumber.isBlank()) throw new RuntimeException("unit_number is required");
        if (tenantName == null || tenantName.isBlank()) throw new RuntimeException("tenant_name is required");
        if (contactNumber == null || contactNumber.isBlank()) throw new RuntimeException("contact_number is required");
        if (rentStartDateStr == null || rentStartDateStr.isBlank()) throw new RuntimeException("rent_start_date is required");

        // Find unit
        Unit unit = unitRepository.findByUnitNumber(unitNumber).orElse(null);
        if (unit == null) {
            throw new RuntimeException("Unit '" + unitNumber + "' not found");
        }

        // Check no active tenant
        Optional<Tenant> activeTenant = tenantRepository.findActiveByUnitId(unit.getUnitId(), TenantStatus.ACTIVE);
        if (activeTenant.isPresent()) {
            throw new RuntimeException("Unit " + unitNumber + " already has an active tenant: " +
                    activeTenant.get().getTenantName());
        }

        // Parse dates
        LocalDate rentStartDate = parseDate(rentStartDateStr);
        LocalDate rentEndDate = parseDate(getField(record, "rent_end_date"));

        // Create tenant
        Tenant tenant = Tenant.builder()
                .unit(unit)
                .tenantName(tenantName)
                .contactNumber(contactNumber)
                .email(getField(record, "email"))
                .aadharNumber(getField(record, "aadhar_number"))
                .panNumber(getField(record, "pan_number"))
                .permanentAddress(getField(record, "permanent_address"))
                .rentStartDate(rentStartDate)
                .rentEndDate(rentEndDate)
                .monthlyRentAmount(parseDecimal(getField(record, "monthly_rent_amount"), null))
                .securityDeposit(parseDecimal(getField(record, "security_deposit"), null))
                .policeVerificationStatus(PoliceVerificationStatus.NOT_INITIATED)
                .nocStatus(NocStatus.PENDING)
                .status(TenantStatus.ACTIVE)
                .build();

        // Add family member if provided
        String fm1Name = getField(record, "family_member_1_name");
        if (fm1Name != null && !fm1Name.isBlank()) {
            TenantFamilyMember member = TenantFamilyMember.builder()
                    .tenant(tenant)
                    .memberName(fm1Name)
                    .age(parseInt(getField(record, "family_member_1_age")))
                    .relation(getField(record, "family_member_1_relation") != null
                            ? getField(record, "family_member_1_relation") : "Other")
                    .build();
            tenant.getFamilyMembers().add(member);
        }

        // Add vehicle if provided
        String v1Type = getField(record, "vehicle_1_type");
        String v1Number = getField(record, "vehicle_1_number");
        if (v1Type != null && !v1Type.isBlank() && v1Number != null && !v1Number.isBlank()) {
            TenantVehicle vehicle = TenantVehicle.builder()
                    .tenant(tenant)
                    .vehicleType(v1Type)
                    .vehicleNumber(v1Number)
                    .build();
            tenant.getVehicles().add(vehicle);
        }

        tenantRepository.save(tenant);

        // Update unit occupancy
        unit.setOccupancyStatus(OccupancyStatus.RENTED);
        unitRepository.save(unit);

        result.setSuccessCount(result.getSuccessCount() + 1);
        result.getSuccessMessages().add("Row " + rowNum + ": " + tenantName + " -> " + unitNumber);
    }

    // ==================== HELPERS ====================

    private String getField(CSVRecord record, String name) {
        try {
            String value = record.get(name);
            return (value != null && !value.isBlank()) ? value.trim() : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private BigDecimal parseDecimal(String value, BigDecimal defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Integer parseInt(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            try {
                return LocalDate.parse(value.trim(), DateTimeFormatter.ofPattern("dd-MM-yyyy"));
            } catch (Exception e2) {
                throw new RuntimeException("Invalid date format: " + value + ". Use yyyy-MM-dd or dd-MM-yyyy");
            }
        }
    }
}
