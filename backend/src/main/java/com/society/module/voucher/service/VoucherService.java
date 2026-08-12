package com.society.module.voucher.service;

import com.society.common.PagedResponse;
import com.society.enums.*;
import com.society.exception.BusinessException;
import com.society.exception.ResourceNotFoundException;
import com.society.module.vendor.entity.Vendor;
import com.society.module.vendor.repository.VendorRepository;
import com.society.module.voucher.dto.*;
import com.society.module.voucher.entity.*;
import com.society.module.voucher.repository.*;
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
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VoucherService {

    private final VoucherRepository voucherRepository;
    private final VoucherAuditTrailRepository auditTrailRepository;
    private final VoucherSequenceRepository sequenceRepository;
    private final VoucherDocumentRepository documentRepository;
    private final VendorRepository vendorRepository;
    private final com.society.common.FileUploadService fileUploadService;

    // ==================== CREATE VOUCHER ====================

    @Transactional
    public VoucherDTO createVoucher(VoucherCreateRequest request) {
        String financialYear = getFinancialYear(request.getVoucherDate());
        String voucherNumber = generateVoucherNumber(request.getVoucherType(), financialYear);

        Vendor vendor = null;
        if (request.getVendorId() != null) {
            vendor = vendorRepository.findById(request.getVendorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Vendor", "vendorId", request.getVendorId()));

            // Warn about potential duplicates
            List<Voucher> duplicates = voucherRepository.findPotentialDuplicates(
                    request.getVendorId(), request.getAmount(), request.getVoucherDate());
            if (!duplicates.isEmpty()) {
                // Log warning but don't block - just flag it
            }
        }

        Voucher voucher = Voucher.builder()
                .voucherNumber(voucherNumber)
                .voucherDate(request.getVoucherDate())
                .voucherType(request.getVoucherType())
                .category(request.getCategory())
                .vendor(vendor)
                .description(request.getDescription())
                .amount(request.getAmount())
                .paymentMode(request.getPaymentMode())
                .referenceNumber(request.getReferenceNumber())
                .billInvoiceNumber(request.getBillInvoiceNumber())
                .billDate(request.getBillDate())
                .status(VoucherStatus.DRAFT)
                .financialYear(financialYear)
                .build();

        voucher = voucherRepository.save(voucher);

        // Create audit trail entry for creation
        createAuditEntry(voucher, "status", null, "DRAFT", "Voucher created", "SYSTEM");

        return mapToDTO(voucher);
    }

    // ==================== UPDATE VOUCHER ====================

    @Transactional
    public VoucherDTO updateVoucher(Long voucherId, VoucherUpdateRequest request) {
        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new ResourceNotFoundException("Voucher", "voucherId", voucherId));

        // Amount can only be changed in DRAFT status
        if (voucher.getStatus() != VoucherStatus.DRAFT &&
                request.getAmount().compareTo(voucher.getAmount()) != 0) {
            throw new BusinessException("Amount can only be changed when voucher is in DRAFT status. " +
                    "Please cancel this voucher and create a new one.");
        }

        // Vendor can only be changed in DRAFT status
        Long currentVendorId = voucher.getVendor() != null ? voucher.getVendor().getVendorId() : null;
        if (voucher.getStatus() != VoucherStatus.DRAFT &&
                !Objects.equals(request.getVendorId(), currentVendorId)) {
            throw new BusinessException("Vendor can only be changed when voucher is in DRAFT status.");
        }

        // Track changes for audit trail
        trackChanges(voucher, request);

        // Apply updates
        voucher.setCategory(request.getCategory());
        voucher.setDescription(request.getDescription());
        voucher.setAmount(request.getAmount());
        voucher.setPaymentMode(request.getPaymentMode());
        voucher.setReferenceNumber(request.getReferenceNumber());
        voucher.setBillInvoiceNumber(request.getBillInvoiceNumber());
        voucher.setBillDate(request.getBillDate());

        if (request.getVendorId() != null) {
            Vendor vendor = vendorRepository.findById(request.getVendorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Vendor", "vendorId", request.getVendorId()));
            voucher.setVendor(vendor);
        } else {
            voucher.setVendor(null);
        }

        voucher = voucherRepository.save(voucher);
        return mapToDTO(voucher);
    }

    // ==================== FINALIZE VOUCHER ====================

    @Transactional
    public VoucherDTO finalizeVoucher(Long voucherId) {
        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new ResourceNotFoundException("Voucher", "voucherId", voucherId));

        if (voucher.getStatus() != VoucherStatus.DRAFT) {
            throw new BusinessException("Only DRAFT vouchers can be finalized");
        }

        createAuditEntry(voucher, "status", "DRAFT", "FINAL", "Voucher finalized", "SYSTEM");

        voucher.setStatus(VoucherStatus.FINAL);
        voucher = voucherRepository.save(voucher);
        return mapToDTO(voucher);
    }

    // ==================== CANCEL VOUCHER ====================

    @Transactional
    public VoucherDTO cancelVoucher(Long voucherId, VoucherCancelRequest request) {
        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new ResourceNotFoundException("Voucher", "voucherId", voucherId));

        if (voucher.getStatus() == VoucherStatus.CANCELLED) {
            throw new BusinessException("Voucher is already cancelled");
        }

        String oldStatus = voucher.getStatus().name();
        createAuditEntry(voucher, "status", oldStatus, "CANCELLED",
                request.getCancellationReason(), "SYSTEM");

        voucher.setStatus(VoucherStatus.CANCELLED);
        voucher.setCancellationReason(request.getCancellationReason());
        voucher.setCancelledBy("SYSTEM");
        voucher.setCancelledOn(LocalDateTime.now());
        voucher = voucherRepository.save(voucher);
        return mapToDTO(voucher);
    }

    // ==================== GET VOUCHER ====================

    public VoucherDTO getVoucherById(Long voucherId) {
        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new ResourceNotFoundException("Voucher", "voucherId", voucherId));
        return mapToDTO(voucher);
    }

    public VoucherDTO getVoucherByNumber(String voucherNumber) {
        Voucher voucher = voucherRepository.findByVoucherNumber(voucherNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Voucher", "voucherNumber", voucherNumber));
        return mapToDTO(voucher);
    }

    public PagedResponse<VoucherDTO> getAllVouchers(int page, int size, String type, String status,
                                                    String category, String financialYear,
                                                    LocalDate startDate, LocalDate endDate, String search) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("voucherDate").descending());
        Page<Voucher> voucherPage;

        if (search != null && !search.isBlank()) {
            voucherPage = voucherRepository.searchVouchers(search, pageable);
        } else if (startDate != null && endDate != null) {
            voucherPage = voucherRepository.findByVoucherDateBetween(startDate, endDate, pageable);
        } else if (type != null && !type.isBlank()) {
            voucherPage = voucherRepository.findByVoucherType(VoucherType.valueOf(type), pageable);
        } else if (status != null && !status.isBlank()) {
            voucherPage = voucherRepository.findByStatus(VoucherStatus.valueOf(status), pageable);
        } else if (category != null && !category.isBlank()) {
            voucherPage = voucherRepository.findByCategory(ExpenseCategory.valueOf(category), pageable);
        } else if (financialYear != null && !financialYear.isBlank()) {
            voucherPage = voucherRepository.findByFinancialYear(financialYear, pageable);
        } else {
            voucherPage = voucherRepository.findAll(pageable);
        }

        List<VoucherDTO> content = voucherPage.getContent().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());

        return PagedResponse.<VoucherDTO>builder()
                .content(content)
                .page(voucherPage.getNumber())
                .size(voucherPage.getSize())
                .totalElements(voucherPage.getTotalElements())
                .totalPages(voucherPage.getTotalPages())
                .last(voucherPage.isLast())
                .build();
    }

    // ==================== AUDIT TRAIL ====================

    public List<VoucherAuditDTO> getAuditTrailByVoucher(Long voucherId) {
        voucherRepository.findById(voucherId)
                .orElseThrow(() -> new ResourceNotFoundException("Voucher", "voucherId", voucherId));

        return auditTrailRepository.findByVoucher_VoucherIdOrderByChangedOnDesc(voucherId)
                .stream()
                .map(this::mapToAuditDTO)
                .collect(Collectors.toList());
    }

    // ==================== REPORTS ====================

    public Map<String, Object> getVoucherSummary(String financialYear) {
        String fy = financialYear != null ? financialYear : getCurrentFinancialYear();
        Map<String, Object> summary = new HashMap<>();

        BigDecimal totalPayments = voucherRepository.getTotalByTypeInFinancialYear(VoucherType.PAYMENT, fy);
        BigDecimal totalReceipts = voucherRepository.getTotalByTypeInFinancialYear(VoucherType.RECEIPT, fy);

        summary.put("financialYear", fy);
        summary.put("totalPayments", totalPayments != null ? totalPayments : BigDecimal.ZERO);
        summary.put("totalReceipts", totalReceipts != null ? totalReceipts : BigDecimal.ZERO);
        summary.put("draftCount", voucherRepository.countByStatus(VoucherStatus.DRAFT));
        summary.put("finalCount", voucherRepository.countByStatus(VoucherStatus.FINAL));
        summary.put("cancelledCount", voucherRepository.countByStatus(VoucherStatus.CANCELLED));
        return summary;
    }

    public List<Map<String, Object>> getCategoryWiseExpenseReport(LocalDate startDate, LocalDate endDate) {
        List<Object[]> results = voucherRepository.getMonthlyCategoryWiseExpense(startDate, endDate);
        List<Map<String, Object>> report = new ArrayList<>();

        for (Object[] row : results) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("category", row[0]);
            entry.put("totalAmount", row[1]);
            report.add(entry);
        }
        return report;
    }

    public List<Map<String, Object>> getVendorWisePaymentReport(LocalDate startDate, LocalDate endDate) {
        List<Object[]> results = voucherRepository.getVendorWisePaymentSummary(startDate, endDate);
        List<Map<String, Object>> report = new ArrayList<>();

        for (Object[] row : results) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("vendorId", row[0]);
            entry.put("vendorName", row[1]);
            entry.put("totalAmount", row[2]);
            report.add(entry);
        }
        return report;
    }

    // ==================== DOCUMENTS ====================

    @Transactional
    public VoucherDocumentDTO addDocument(Long voucherId, String documentName, String documentType, String filePath) {
        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new ResourceNotFoundException("Voucher", "voucherId", voucherId));

        VoucherDocument document = VoucherDocument.builder()
                .voucher(voucher)
                .documentName(documentName)
                .documentType(documentType)
                .filePath(filePath)
                .uploadedBy("SYSTEM")
                .uploadedOn(LocalDateTime.now())
                .build();

        document = documentRepository.save(document);
        return mapToDocumentDTO(document);
    }

    @Transactional
    public VoucherDocumentDTO uploadDocument(Long voucherId, org.springframework.web.multipart.MultipartFile file, String documentType) {
        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new ResourceNotFoundException("Voucher", "voucherId", voucherId));

        String filePath = fileUploadService.uploadFile(file, "vouchers/" + voucherId);

        VoucherDocument document = VoucherDocument.builder()
                .voucher(voucher)
                .documentName(file.getOriginalFilename())
                .documentType(documentType)
                .filePath(filePath)
                .uploadedBy("SYSTEM")
                .uploadedOn(LocalDateTime.now())
                .build();

        document = documentRepository.save(document);
        return mapToDocumentDTO(document);
    }

    public List<VoucherDocumentDTO> getVoucherDocuments(Long voucherId) {
        voucherRepository.findById(voucherId)
                .orElseThrow(() -> new ResourceNotFoundException("Voucher", "voucherId", voucherId));
        return documentRepository.findByVoucher_VoucherIdOrderByUploadedOnDesc(voucherId)
                .stream()
                .map(this::mapToDocumentDTO)
                .collect(Collectors.toList());
    }

    // ==================== HELPER METHODS ====================

    private String generateVoucherNumber(VoucherType type, String financialYear) {
        VoucherSequence sequence = sequenceRepository
                .findByVoucherTypeAndFinancialYearForUpdate(type, financialYear)
                .orElseGet(() -> {
                    VoucherSequence newSeq = VoucherSequence.builder()
                            .voucherType(type)
                            .financialYear(financialYear)
                            .lastNumber(0)
                            .build();
                    return sequenceRepository.save(newSeq);
                });

        sequence.setLastNumber(sequence.getLastNumber() + 1);
        sequenceRepository.save(sequence);

        String prefix = getVoucherPrefix(type);
        String yearPart = financialYear.replace("-", "");
        return String.format("%s-%s-%03d", prefix, yearPart.substring(0, 4), sequence.getLastNumber());
    }

    private String getVoucherPrefix(VoucherType type) {
        return switch (type) {
            case PAYMENT -> "PV";
            case RECEIPT -> "RV";
            case JOURNAL -> "JV";
            case CONTRA -> "CV";
        };
    }

    private String getFinancialYear(LocalDate date) {
        int year = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
        return year + "-" + String.valueOf(year + 1).substring(2);
    }

    private String getCurrentFinancialYear() {
        return getFinancialYear(LocalDate.now());
    }

    private void trackChanges(Voucher voucher, VoucherUpdateRequest request) {
        if (request.getAmount().compareTo(voucher.getAmount()) != 0) {
            createAuditEntry(voucher, "amount",
                    voucher.getAmount().toString(), request.getAmount().toString(),
                    request.getUpdateReason(), "SYSTEM");
        }
        if (!request.getDescription().equals(voucher.getDescription())) {
            createAuditEntry(voucher, "description",
                    voucher.getDescription(), request.getDescription(),
                    request.getUpdateReason(), "SYSTEM");
        }
        if (request.getCategory() != voucher.getCategory()) {
            createAuditEntry(voucher, "category",
                    voucher.getCategory().name(), request.getCategory().name(),
                    request.getUpdateReason(), "SYSTEM");
        }
        if (!Objects.equals(request.getPaymentMode(), voucher.getPaymentMode())) {
            createAuditEntry(voucher, "paymentMode",
                    voucher.getPaymentMode() != null ? voucher.getPaymentMode().name() : null,
                    request.getPaymentMode() != null ? request.getPaymentMode().name() : null,
                    request.getUpdateReason(), "SYSTEM");
        }
        if (!Objects.equals(request.getReferenceNumber(), voucher.getReferenceNumber())) {
            createAuditEntry(voucher, "referenceNumber",
                    voucher.getReferenceNumber(), request.getReferenceNumber(),
                    request.getUpdateReason(), "SYSTEM");
        }
    }

    private void createAuditEntry(Voucher voucher, String field, String oldValue,
                                   String newValue, String reason, String changedBy) {
        VoucherAuditTrail audit = VoucherAuditTrail.builder()
                .voucher(voucher)
                .fieldChanged(field)
                .oldValue(oldValue)
                .newValue(newValue)
                .changeReason(reason)
                .changedBy(changedBy)
                .changedOn(LocalDateTime.now())
                .build();
        auditTrailRepository.save(audit);
    }

    // ==================== MAPPERS ====================

    private VoucherDTO mapToDTO(Voucher voucher) {
        List<VoucherDocumentDTO> docs = voucher.getDocuments() != null
                ? voucher.getDocuments().stream().map(this::mapToDocumentDTO).collect(Collectors.toList())
                : Collections.emptyList();

        return VoucherDTO.builder()
                .voucherId(voucher.getVoucherId())
                .voucherNumber(voucher.getVoucherNumber())
                .voucherDate(voucher.getVoucherDate())
                .voucherType(voucher.getVoucherType())
                .category(voucher.getCategory())
                .vendorId(voucher.getVendor() != null ? voucher.getVendor().getVendorId() : null)
                .vendorName(voucher.getVendor() != null ? voucher.getVendor().getVendorName() : null)
                .description(voucher.getDescription())
                .amount(voucher.getAmount())
                .paymentMode(voucher.getPaymentMode())
                .referenceNumber(voucher.getReferenceNumber())
                .billInvoiceNumber(voucher.getBillInvoiceNumber())
                .billDate(voucher.getBillDate())
                .status(voucher.getStatus())
                .cancellationReason(voucher.getCancellationReason())
                .cancelledBy(voucher.getCancelledBy())
                .cancelledOn(voucher.getCancelledOn())
                .financialYear(voucher.getFinancialYear())
                .documents(docs)
                .createdBy(voucher.getCreatedBy())
                .createdOn(voucher.getCreatedOn())
                .modifiedBy(voucher.getModifiedBy())
                .modifiedOn(voucher.getModifiedOn())
                .build();
    }

    private VoucherDocumentDTO mapToDocumentDTO(VoucherDocument doc) {
        return VoucherDocumentDTO.builder()
                .documentId(doc.getDocumentId())
                .documentName(doc.getDocumentName())
                .documentType(doc.getDocumentType())
                .filePath(doc.getFilePath())
                .uploadedBy(doc.getUploadedBy())
                .uploadedOn(doc.getUploadedOn())
                .build();
    }

    private VoucherAuditDTO mapToAuditDTO(VoucherAuditTrail audit) {
        return VoucherAuditDTO.builder()
                .auditId(audit.getAuditId())
                .voucherId(audit.getVoucher().getVoucherId())
                .voucherNumber(audit.getVoucher().getVoucherNumber())
                .fieldChanged(audit.getFieldChanged())
                .oldValue(audit.getOldValue())
                .newValue(audit.getNewValue())
                .changeReason(audit.getChangeReason())
                .changedBy(audit.getChangedBy())
                .changedOn(audit.getChangedOn())
                .ipAddress(audit.getIpAddress())
                .build();
    }
}
