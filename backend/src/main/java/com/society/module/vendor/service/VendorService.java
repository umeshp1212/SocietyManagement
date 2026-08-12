package com.society.module.vendor.service;

import com.society.common.PagedResponse;
import com.society.enums.VendorCategory;
import com.society.enums.VendorStatus;
import com.society.exception.ResourceNotFoundException;
import com.society.module.vendor.dto.*;
import com.society.module.vendor.entity.Vendor;
import com.society.module.vendor.entity.VendorDocument;
import com.society.module.vendor.repository.VendorDocumentRepository;
import com.society.module.vendor.repository.VendorRepository;
import com.society.module.voucher.entity.Voucher;
import com.society.module.voucher.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VendorService {

    private final VendorRepository vendorRepository;
    private final VendorDocumentRepository vendorDocumentRepository;
    private final VoucherRepository voucherRepository;

    // ==================== VENDOR CRUD ====================

    @Transactional
    public VendorDTO createVendor(VendorCreateRequest request) {
        Vendor vendor = Vendor.builder()
                .vendorName(request.getVendorName())
                .category(request.getCategory())
                .contactPerson(request.getContactPerson())
                .phone(request.getPhone())
                .email(request.getEmail())
                .address(request.getAddress())
                .panNumber(request.getPanNumber())
                .gstNumber(request.getGstNumber())
                .bankAccountNumber(request.getBankAccountNumber())
                .bankIfsc(request.getBankIfsc())
                .bankName(request.getBankName())
                .agreementStartDate(request.getAgreementStartDate())
                .agreementEndDate(request.getAgreementEndDate())
                .contractedAmount(request.getContractedAmount())
                .paymentFrequency(request.getPaymentFrequency())
                .status(VendorStatus.ACTIVE)
                .build();

        vendor = vendorRepository.save(vendor);
        return mapToDTO(vendor);
    }

    @Transactional
    public VendorDTO updateVendor(Long vendorId, VendorUpdateRequest request) {
        Vendor vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor", "vendorId", vendorId));

        vendor.setVendorName(request.getVendorName());
        vendor.setCategory(request.getCategory());
        vendor.setContactPerson(request.getContactPerson());
        vendor.setPhone(request.getPhone());
        vendor.setEmail(request.getEmail());
        vendor.setAddress(request.getAddress());
        vendor.setPanNumber(request.getPanNumber());
        vendor.setGstNumber(request.getGstNumber());
        vendor.setBankAccountNumber(request.getBankAccountNumber());
        vendor.setBankIfsc(request.getBankIfsc());
        vendor.setBankName(request.getBankName());
        vendor.setAgreementStartDate(request.getAgreementStartDate());
        vendor.setAgreementEndDate(request.getAgreementEndDate());
        vendor.setContractedAmount(request.getContractedAmount());
        vendor.setPaymentFrequency(request.getPaymentFrequency());
        vendor.setStatus(request.getStatus());

        vendor = vendorRepository.save(vendor);
        return mapToDTO(vendor);
    }

    public VendorDTO getVendorById(Long vendorId) {
        Vendor vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor", "vendorId", vendorId));
        return mapToDTO(vendor);
    }

    public PagedResponse<VendorDTO> getAllVendors(int page, int size, String status, String category, String search) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("vendorName").ascending());
        Page<Vendor> vendorPage;

        if (search != null && !search.isBlank()) {
            if (status != null && !status.isBlank()) {
                vendorPage = vendorRepository.searchVendorsByStatus(
                        VendorStatus.valueOf(status), search, pageable);
            } else {
                vendorPage = vendorRepository.searchVendors(search, pageable);
            }
        } else if (status != null && !status.isBlank() && category != null && !category.isBlank()) {
            vendorPage = vendorRepository.findByStatusAndCategory(
                    VendorStatus.valueOf(status), VendorCategory.valueOf(category), pageable);
        } else if (status != null && !status.isBlank()) {
            vendorPage = vendorRepository.findByStatus(VendorStatus.valueOf(status), pageable);
        } else if (category != null && !category.isBlank()) {
            vendorPage = vendorRepository.findByCategory(VendorCategory.valueOf(category), pageable);
        } else {
            vendorPage = vendorRepository.findAll(pageable);
        }

        List<VendorDTO> content = vendorPage.getContent().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());

        return PagedResponse.<VendorDTO>builder()
                .content(content)
                .page(vendorPage.getNumber())
                .size(vendorPage.getSize())
                .totalElements(vendorPage.getTotalElements())
                .totalPages(vendorPage.getTotalPages())
                .last(vendorPage.isLast())
                .build();
    }

    public List<VendorDTO> getActiveVendorsList() {
        return vendorRepository.findByStatusOrderByVendorNameAsc(VendorStatus.ACTIVE)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // ==================== CONTRACT ALERTS ====================

    public List<VendorDTO> getVendorsWithExpiringContracts(int daysAhead) {
        LocalDate today = LocalDate.now();
        LocalDate expiryDate = today.plusDays(daysAhead);
        return vendorRepository.findVendorsWithExpiringContracts(today, expiryDate)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<VendorDTO> getVendorsWithExpiredContracts() {
        return vendorRepository.findVendorsWithExpiredContracts(LocalDate.now())
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // ==================== VENDOR SUMMARY ====================

    public Map<String, Object> getVendorSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalActive", vendorRepository.countByStatus(VendorStatus.ACTIVE));
        summary.put("totalInactive", vendorRepository.countByStatus(VendorStatus.INACTIVE));
        summary.put("totalBlacklisted", vendorRepository.countByStatus(VendorStatus.BLACKLISTED));
        summary.put("expiringIn30Days", vendorRepository.findVendorsWithExpiringContracts(
                LocalDate.now(), LocalDate.now().plusDays(30)).size());
        summary.put("expiredContracts", vendorRepository.findVendorsWithExpiredContracts(
                LocalDate.now()).size());
        return summary;
    }

    // ==================== DOCUMENTS ====================

    @Transactional
    public VendorDocumentDTO addDocument(Long vendorId, String documentName, String documentType, String filePath) {
        Vendor vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor", "vendorId", vendorId));

        VendorDocument document = VendorDocument.builder()
                .vendor(vendor)
                .documentName(documentName)
                .documentType(documentType)
                .filePath(filePath)
                .uploadedBy("SYSTEM")
                .uploadedOn(LocalDateTime.now())
                .build();

        document = vendorDocumentRepository.save(document);
        return mapToDocumentDTO(document);
    }

    public List<VendorDocumentDTO> getVendorDocuments(Long vendorId) {
        vendorRepository.findById(vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor", "vendorId", vendorId));

        return vendorDocumentRepository.findByVendor_VendorIdOrderByUploadedOnDesc(vendorId)
                .stream()
                .map(this::mapToDocumentDTO)
                .collect(Collectors.toList());
    }

    // ==================== MAPPERS ====================

    private VendorDTO mapToDTO(Vendor vendor) {
        Long daysUntilExpiry = null;
        Boolean isExpired = null;

        if (vendor.getAgreementEndDate() != null) {
            long days = ChronoUnit.DAYS.between(LocalDate.now(), vendor.getAgreementEndDate());
            daysUntilExpiry = days;
            isExpired = days < 0;
        }

        List<VendorDocumentDTO> docs = vendor.getDocuments() != null
                ? vendor.getDocuments().stream().map(this::mapToDocumentDTO).collect(Collectors.toList())
                : Collections.emptyList();

        return VendorDTO.builder()
                .vendorId(vendor.getVendorId())
                .vendorName(vendor.getVendorName())
                .category(vendor.getCategory())
                .contactPerson(vendor.getContactPerson())
                .phone(vendor.getPhone())
                .email(vendor.getEmail())
                .address(vendor.getAddress())
                .panNumber(vendor.getPanNumber())
                .gstNumber(vendor.getGstNumber())
                .bankAccountNumber(vendor.getBankAccountNumber())
                .bankIfsc(vendor.getBankIfsc())
                .bankName(vendor.getBankName())
                .agreementStartDate(vendor.getAgreementStartDate())
                .agreementEndDate(vendor.getAgreementEndDate())
                .contractedAmount(vendor.getContractedAmount())
                .paymentFrequency(vendor.getPaymentFrequency())
                .status(vendor.getStatus())
                .documents(docs)
                .daysUntilExpiry(daysUntilExpiry)
                .isContractExpired(isExpired)
                .createdBy(vendor.getCreatedBy())
                .createdOn(vendor.getCreatedOn())
                .modifiedBy(vendor.getModifiedBy())
                .modifiedOn(vendor.getModifiedOn())
                .build();
    }

    private VendorDocumentDTO mapToDocumentDTO(VendorDocument doc) {
        return VendorDocumentDTO.builder()
                .documentId(doc.getDocumentId())
                .documentName(doc.getDocumentName())
                .documentType(doc.getDocumentType())
                .filePath(doc.getFilePath())
                .uploadedBy(doc.getUploadedBy())
                .uploadedOn(doc.getUploadedOn())
                .build();
    }

    // ==================== VENDOR LEDGER ====================

    public VendorLedgerDTO getVendorLedger(Long vendorId, LocalDate startDate, LocalDate endDate) {
        Vendor vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor", "vendorId", vendorId));

        List<Voucher> vouchers;
        if (startDate != null && endDate != null) {
            vouchers = voucherRepository.findByVendorIdAndDateRange(vendorId, startDate, endDate);
        } else {
            vouchers = voucherRepository.findByVendorIdOrderByDate(vendorId);
        }

        BigDecimal runningTotal = BigDecimal.ZERO;
        List<VendorLedgerDTO.LedgerEntry> entries = new ArrayList<>();

        for (Voucher v : vouchers) {
            runningTotal = runningTotal.add(v.getAmount());
            entries.add(VendorLedgerDTO.LedgerEntry.builder()
                    .voucherId(v.getVoucherId())
                    .voucherNumber(v.getVoucherNumber())
                    .voucherDate(v.getVoucherDate())
                    .voucherType(v.getVoucherType().name())
                    .category(v.getCategory().name())
                    .description(v.getDescription())
                    .amount(v.getAmount())
                    .paymentMode(v.getPaymentMode() != null ? v.getPaymentMode().name() : null)
                    .referenceNumber(v.getReferenceNumber())
                    .status(v.getStatus().name())
                    .financialYear(v.getFinancialYear())
                    .runningTotal(runningTotal)
                    .build());
        }

        return VendorLedgerDTO.builder()
                .vendorId(vendor.getVendorId())
                .vendorName(vendor.getVendorName())
                .totalAmount(runningTotal)
                .entries(entries)
                .build();
    }
}
