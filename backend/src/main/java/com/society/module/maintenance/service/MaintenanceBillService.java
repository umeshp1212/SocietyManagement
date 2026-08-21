package com.society.module.maintenance.service;

import com.society.common.PagedResponse;
import com.society.enums.OccupancyStatus;
import com.society.exception.BusinessException;
import com.society.exception.ResourceNotFoundException;
import com.society.module.maintenance.dto.*;
import com.society.module.maintenance.entity.BillLineItem;
import com.society.module.maintenance.entity.MaintenanceBill;
import com.society.module.maintenance.entity.MaintenanceBill.BillStatus;
import com.society.module.maintenance.entity.MaintenanceChargeConfig;
import com.society.module.maintenance.entity.MaintenanceChargeConfig.ApplicableTo;
import com.society.module.maintenance.entity.MaintenanceChargeConfig.CalculationType;
import com.society.module.maintenance.entity.MaintenancePayment;
import com.society.module.maintenance.entity.MaintenancePayment.PaymentMode;
import com.society.module.maintenance.entity.MaintenancePayment.PaymentStatus;
import com.society.module.maintenance.entity.Penalty;
import com.society.module.maintenance.entity.Penalty.PenaltyStatus;
import com.society.module.maintenance.repository.BillLineItemRepository;
import com.society.module.maintenance.repository.MaintenanceBillRepository;
import com.society.module.maintenance.repository.MaintenanceChargeConfigRepository;
import com.society.module.maintenance.repository.MaintenancePaymentRepository;
import com.society.module.maintenance.repository.OpeningBalanceRepository;
import com.society.module.maintenance.repository.PenaltyRepository;
import com.society.module.owner.entity.Unit;
import com.society.module.owner.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaintenanceBillService {

    private final MaintenanceBillRepository billRepository;
    private final MaintenancePaymentRepository paymentRepository;
    private final MaintenanceChargeConfigRepository chargeConfigRepository;
    private final BillLineItemRepository lineItemRepository;
    private final UnitRepository unitRepository;
    private final WaterChargeConfigService waterChargeConfigService;
    private final PenaltyRepository penaltyRepository;
    private final OpeningBalanceRepository openingBalanceRepository;

    /**
     * Interest rate: 1% per month on unpaid arrears
     */
    private static final BigDecimal MONTHLY_INTEREST_RATE = new BigDecimal("0.01");

    // ======================== BILL GENERATION ========================

    @Transactional
    public Map<String, Object> generateMonthlyBills(GenerateBillsRequest request) {
        List<Unit> units = unitRepository.findAll();
        List<MaintenanceChargeConfig> activeCharges = chargeConfigRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
        int billsGenerated = 0;
        int billsRegenerated = 0;
        boolean isRegenerate = Boolean.TRUE.equals(request.getRegenerate());

        if (activeCharges.isEmpty()) {
            throw new BusinessException("No active charge configurations found. Please configure maintenance charges first.");
        }

        for (Unit unit : units) {
            // Skip inactive units
            if (!"ACTIVE".equals(unit.getStatus())) {
                continue;
            }

            // Check if bill already exists for this unit/month/year
            Optional<MaintenanceBill> existingBillOpt = billRepository.findByUnit_UnitIdAndBillMonthAndBillYear(
                    unit.getUnitId(), request.getMonth(), request.getYear());

            if (existingBillOpt.isPresent()) {
                if (isRegenerate) {
                    MaintenanceBill existingBill = existingBillOpt.get();
                    // Only regenerate UNPAID bills - don't touch paid/partially paid
                    if (existingBill.getStatus() == BillStatus.UNPAID) {
                        billRepository.delete(existingBill);
                        billRepository.flush();
                        // Generate fresh bill
                        MaintenanceBill newBill = generateBillForUnit(unit, activeCharges, request);
                        if (newBill != null) {
                            billRepository.save(newBill);
                            billsRegenerated++;
                        }
                    }
                }
                // If not regenerate mode, skip existing bills
                continue;
            }

            // Generate bill with line items
            MaintenanceBill bill = generateBillForUnit(unit, activeCharges, request);
            if (bill != null) {
                billRepository.save(bill);
                billsGenerated++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("billsGenerated", billsGenerated);
        result.put("billsRegenerated", billsRegenerated);
        result.put("month", request.getMonth());
        result.put("year", request.getYear());
        String message = "Successfully generated " + billsGenerated + " new bills";
        if (billsRegenerated > 0) {
            message += " and regenerated " + billsRegenerated + " existing bills";
        }
        message += " for " + Month.of(request.getMonth()).name() + " " + request.getYear();
        result.put("message", message);
        return result;
    }

    /**
     * Generate a bill for a single unit with line items and arrears/interest calculation.
     */
    private MaintenanceBill generateBillForUnit(Unit unit, List<MaintenanceChargeConfig> activeCharges,
                                                 GenerateBillsRequest request) {
        BigDecimal areaSqft = unit.getAreaSqft() != null ? unit.getAreaSqft() : BigDecimal.ZERO;

        MaintenanceBill bill = new MaintenanceBill();
        bill.setUnit(unit);
        bill.setBillMonth(request.getMonth());
        bill.setBillYear(request.getYear());
        bill.setBillDate(LocalDate.of(request.getYear(), request.getMonth(), 1));
        bill.setUnitAreaSqft(areaSqft);

        int dueDayOfMonth = request.getDueDayOfMonth() != null ? request.getDueDayOfMonth() : 10;
        bill.setDueDate(LocalDate.of(request.getYear(), request.getMonth(), dueDayOfMonth));

        // Compute line items from charge configs
        List<BillLineItem> lineItems = new ArrayList<>();
        BigDecimal currentChargesTotal = BigDecimal.ZERO;

        for (MaintenanceChargeConfig charge : activeCharges) {
            if (!isChargeApplicable(charge, unit)) {
                continue;
            }

            BigDecimal lineAmount = computeChargeAmount(charge, areaSqft, unit);
            if (lineAmount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BillLineItem lineItem = BillLineItem.builder()
                    .bill(bill)
                    .chargeCode(charge.getChargeCode())
                    .chargeName(charge.getChargeName())
                    .calculationType(charge.getCalculationType().name())
                    .rate(charge.getCalculationType() == CalculationType.AREA_BASED
                            ? charge.getRatePerSqft() : charge.getFlatAmount())
                    .areaSqft(charge.getCalculationType() == CalculationType.AREA_BASED ? areaSqft : null)
                    .amount(lineAmount)
                    .displayOrder(charge.getDisplayOrder())
                    .build();

            lineItems.add(lineItem);
            currentChargesTotal = currentChargesTotal.add(lineAmount);
        }

        // Add penalty line items for this unit/month/year
        List<Penalty> pendingPenalties = penaltyRepository.findByUnit_UnitIdAndBillMonthAndBillYearAndStatus(
                unit.getUnitId(), request.getMonth(), request.getYear(), PenaltyStatus.PENDING);

        for (Penalty penalty : pendingPenalties) {
            BillLineItem penaltyLineItem = BillLineItem.builder()
                    .bill(bill)
                    .chargeCode("PENALTY_" + penalty.getCategory().name())
                    .chargeName("Penalty - " + penalty.getReason())
                    .calculationType("FLAT")
                    .rate(penalty.getAmount())
                    .amount(penalty.getAmount())
                    .displayOrder(99)
                    .build();

            lineItems.add(penaltyLineItem);
            currentChargesTotal = currentChargesTotal.add(penalty.getAmount());

            // Mark penalty as billed
            penalty.setStatus(PenaltyStatus.BILLED);
            penaltyRepository.save(penalty);
        }

        // If no charges apply, skip this unit
        if (lineItems.isEmpty()) {
            return null;
        }

        // Calculate arrears and interest from previous month's unpaid balance
        BigDecimal previousArrears = calculatePreviousArrears(unit.getUnitId(), request.getMonth(), request.getYear());
        BigDecimal interestOnArrears = previousArrears.multiply(MONTHLY_INTEREST_RATE)
                .setScale(2, RoundingMode.HALF_UP);

        // Set bill amounts
        bill.setAmount(currentChargesTotal);
        bill.setPreviousArrears(previousArrears);
        bill.setInterestOnArrears(interestOnArrears);
        bill.setLateFee(BigDecimal.ZERO);

        // Total = current charges + previous arrears + interest
        BigDecimal totalAmount = currentChargesTotal.add(previousArrears).add(interestOnArrears);
        bill.setTotalAmount(totalAmount);
        bill.setBalanceAmount(totalAmount);
        bill.setPaidAmount(BigDecimal.ZERO);
        bill.setStatus(BillStatus.UNPAID);

        bill.setLineItems(lineItems);

        return bill;
    }

    /**
     * Check if a charge type applies to this unit based on conditions.
     */
    private boolean isChargeApplicable(MaintenanceChargeConfig charge, Unit unit) {
        ApplicableTo applicableTo = charge.getApplicableTo();
        int twoWheelers = unit.getTwoWheelerCount() != null ? unit.getTwoWheelerCount() : 0;
        int fourWheelers = unit.getFourWheelerCount() != null ? unit.getFourWheelerCount() : 0;

        switch (applicableTo) {
            case ALL:
                return true;
            case RENTED:
                return unit.getOccupancyStatus() == OccupancyStatus.RENTED;
            case OWNER_OCCUPIED:
                return unit.getOccupancyStatus() == OccupancyStatus.SELF_OCCUPIED;
            case PARKING:
                // Any parking (two-wheeler or four-wheeler)
                return twoWheelers > 0 || fourWheelers > 0;
            case TWO_WHEELER:
                return twoWheelers > 0;
            case FOUR_WHEELER:
                return fourWheelers > 0;
            default:
                return true;
        }
    }

    /**
     * Compute the charge amount based on calculation type.
     * Special handling for WATER_CHARGES: uses WaterChargeConfig for tank-based or municipal calculation.
     * Falls back to unit-specific waterCharges field if no config exists.
     */
    private BigDecimal computeChargeAmount(MaintenanceChargeConfig charge, BigDecimal areaSqft, Unit unit) {
        // Special handling: Water charges use WaterChargeConfig
        if ("WATER_CHARGES".equals(charge.getChargeCode())) {
            BigDecimal waterCharge = waterChargeConfigService.computeWaterChargeForUnit(unit);
            if (waterCharge.compareTo(BigDecimal.ZERO) > 0) {
                return waterCharge;
            }
            // Fall back to flat amount from config if no water charge config and no unit-specific amount
            return charge.getFlatAmount() != null ? charge.getFlatAmount() : BigDecimal.ZERO;
        }

        // Special handling: Parking charges multiply by vehicle count
        if (charge.getApplicableTo() == ApplicableTo.TWO_WHEELER) {
            int count = unit.getTwoWheelerCount() != null ? unit.getTwoWheelerCount() : 0;
            BigDecimal rate = charge.getFlatAmount() != null ? charge.getFlatAmount() : BigDecimal.ZERO;
            return rate.multiply(BigDecimal.valueOf(count));
        }
        if (charge.getApplicableTo() == ApplicableTo.FOUR_WHEELER) {
            int count = unit.getFourWheelerCount() != null ? unit.getFourWheelerCount() : 0;
            BigDecimal rate = charge.getFlatAmount() != null ? charge.getFlatAmount() : BigDecimal.ZERO;
            return rate.multiply(BigDecimal.valueOf(count));
        }

        if (charge.getCalculationType() == CalculationType.AREA_BASED) {
            if (charge.getRatePerSqft() == null || areaSqft == null || areaSqft.compareTo(BigDecimal.ZERO) <= 0) {
                return BigDecimal.ZERO;
            }
            return charge.getRatePerSqft().multiply(areaSqft).setScale(0, RoundingMode.HALF_UP);
        } else {
            // FLAT amount
            return charge.getFlatAmount() != null ? charge.getFlatAmount() : BigDecimal.ZERO;
        }
    }

    /**
     * Calculate the total unpaid arrears from all previous months for a unit.
     * This includes:
     * 1. Balance from all unpaid/partially-paid/overdue bills (EXCLUDING current month)
     * 2. Opening balance (legacy arrears from before app deployment)
     */
    private BigDecimal calculatePreviousArrears(Long unitId, int currentMonth, int currentYear) {
        List<MaintenanceBill> outstandingBills = billRepository.findOutstandingByUnit(unitId);

        BigDecimal totalArrears = BigDecimal.ZERO;
        for (MaintenanceBill existingBill : outstandingBills) {
            // Only consider bills BEFORE the current month
            if (isBillBeforeMonth(existingBill, currentMonth, currentYear)) {
                BigDecimal balance = existingBill.getBalanceAmount() != null
                        ? existingBill.getBalanceAmount() : BigDecimal.ZERO;
                totalArrears = totalArrears.add(balance);
            }
        }

        // Add opening balance (legacy arrears)
        BigDecimal openingBalance = openingBalanceRepository.getTotalOpeningBalanceByUnit(unitId);
        if (openingBalance != null && openingBalance.compareTo(BigDecimal.ZERO) > 0) {
            totalArrears = totalArrears.add(openingBalance);
        }

        return totalArrears;
    }

    private boolean isBillBeforeMonth(MaintenanceBill bill, int month, int year) {
        if (bill.getBillYear() < year) return true;
        if (bill.getBillYear() == year && bill.getBillMonth() < month) return true;
        return false;
    }

    // ======================== BILL QUERIES ========================

    public PagedResponse<BillDTO> getBillsByMonth(int month, int year, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("unit.unitId").ascending());
        Page<MaintenanceBill> billPage = billRepository.findByBillMonthAndBillYear(month, year, pageable);

        List<BillDTO> bills = billPage.getContent().stream()
                .map(this::mapToBillDTO)
                .collect(Collectors.toList());

        return new PagedResponse<>(bills, billPage.getNumber(), billPage.getSize(),
                billPage.getTotalElements(), billPage.getTotalPages(), billPage.isLast());
    }

    public List<BillDTO> getBillsByUnit(Long unitId) {
        List<MaintenanceBill> bills = billRepository.findByUnit_UnitIdOrderByBillYearDescBillMonthDesc(unitId);
        return bills.stream()
                .map(this::mapToBillDTO)
                .collect(Collectors.toList());
    }

    public BillDTO getBillById(Long billId) {
        MaintenanceBill bill = billRepository.findById(billId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found with id: " + billId));
        return mapToBillDTOWithLineItems(bill);
    }

    public List<BillDTO> getOutstandingByUnit(Long unitId) {
        List<MaintenanceBill> bills = billRepository.findOutstandingByUnit(unitId);
        return bills.stream()
                .map(this::mapToBillDTO)
                .collect(Collectors.toList());
    }

    public BigDecimal getTotalOutstanding(Long unitId) {
        BigDecimal billOutstanding = billRepository.getTotalOutstandingByUnit(unitId);
        BigDecimal openingBalance = openingBalanceRepository.getTotalOpeningBalanceByUnit(unitId);
        BigDecimal total = (billOutstanding != null ? billOutstanding : BigDecimal.ZERO)
                .add(openingBalance != null ? openingBalance : BigDecimal.ZERO);
        return total;
    }

    public List<BillDTO> getDefaulters(int month, int year) {
        List<MaintenanceBill> bills = billRepository.findDefaulters(month, year);
        return bills.stream()
                .map(this::mapToBillDTO)
                .collect(Collectors.toList());
    }

    // ======================== PAYMENT ========================

    @Transactional
    public PaymentDTO recordOfflinePayment(RecordOfflinePaymentRequest request) {
        MaintenanceBill bill = billRepository.findById(request.getBillId())
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found with id: " + request.getBillId()));

        if (bill.getStatus() == BillStatus.PAID) {
            throw new BusinessException("Bill is already fully paid");
        }

        if (request.getAmount().compareTo(bill.getBalanceAmount()) > 0) {
            throw new BusinessException("Payment amount exceeds the outstanding balance of " + bill.getBalanceAmount());
        }

        MaintenancePayment payment = new MaintenancePayment();
        payment.setBill(bill);
        payment.setUnit(bill.getUnit());
        payment.setAmount(request.getAmount());
        payment.setPaymentDate(request.getPaymentDate());
        payment.setPaymentMode(PaymentMode.valueOf(request.getPaymentMode()));
        payment.setTransactionId(request.getTransactionId());
        payment.setPayerName(request.getPayerName());
        payment.setPayerType(request.getPayerType() != null ? request.getPayerType() : "OWNER");
        payment.setReceiptNumber(generateReceiptNumber());
        payment.setStatus(PaymentStatus.VERIFIED);
        payment.setRemarks(request.getRemarks());
        payment.setVerifiedOn(LocalDateTime.now());
        payment.setVerifiedBy("ADMIN");

        paymentRepository.save(payment);

        // Update bill amounts
        BigDecimal paidAmount = bill.getPaidAmount() != null ? bill.getPaidAmount() : BigDecimal.ZERO;
        paidAmount = paidAmount.add(request.getAmount());
        bill.setPaidAmount(paidAmount);
        bill.setBalanceAmount(bill.getTotalAmount().subtract(paidAmount));

        if (bill.getBalanceAmount().compareTo(BigDecimal.ZERO) <= 0) {
            bill.setStatus(BillStatus.PAID);
            bill.setBalanceAmount(BigDecimal.ZERO);
        } else if (paidAmount.compareTo(BigDecimal.ZERO) > 0) {
            bill.setStatus(BillStatus.PARTIALLY_PAID);
        }

        billRepository.save(bill);

        return mapToPaymentDTO(payment);
    }

    public PagedResponse<PaymentDTO> getPaymentsByUnit(Long unitId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "paymentDate"));
        Page<MaintenancePayment> paymentPage = paymentRepository.findByUnit_UnitId(unitId, pageable);

        List<PaymentDTO> payments = paymentPage.getContent().stream()
                .map(this::mapToPaymentDTO)
                .collect(Collectors.toList());

        return new PagedResponse<>(payments, paymentPage.getNumber(), paymentPage.getSize(),
                paymentPage.getTotalElements(), paymentPage.getTotalPages(), paymentPage.isLast());
    }

    public List<PaymentDTO> getPaymentsByBill(Long billId) {
        List<MaintenancePayment> payments = paymentRepository.findByBill_BillId(billId);
        return payments.stream()
                .map(this::mapToPaymentDTO)
                .collect(Collectors.toList());
    }

    public Map<String, Object> getCollectionSummary(int month, int year) {
        List<MaintenanceBill> bills = billRepository.findByBillMonthAndBillYear(month, year);

        BigDecimal totalBilled = bills.stream()
                .map(MaintenanceBill::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCollected = bills.stream()
                .map(b -> b.getPaidAmount() != null ? b.getPaidAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalOutstanding = bills.stream()
                .map(b -> b.getBalanceAmount() != null ? b.getBalanceAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long paidCount = bills.stream().filter(b -> b.getStatus() == BillStatus.PAID).count();
        long unpaidCount = bills.stream().filter(b -> b.getStatus() == BillStatus.UNPAID).count();
        long partialCount = bills.stream().filter(b -> b.getStatus() == BillStatus.PARTIALLY_PAID).count();
        long overdueCount = bills.stream().filter(b -> b.getStatus() == BillStatus.OVERDUE).count();

        Map<String, Object> summary = new HashMap<>();
        summary.put("month", month);
        summary.put("year", year);
        summary.put("totalBilled", totalBilled);
        summary.put("totalCollected", totalCollected);
        summary.put("totalOutstanding", totalOutstanding);
        summary.put("paidCount", paidCount);
        summary.put("unpaidCount", unpaidCount);
        summary.put("partialCount", partialCount);
        summary.put("overdueCount", overdueCount);
        return summary;
    }

    // ======================== HELPERS ========================

    private String generateReceiptNumber() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int randomPart = new Random().nextInt(9000) + 1000;
        return "RCP-" + datePart + "-" + randomPart;
    }

    private BillDTO mapToBillDTO(MaintenanceBill bill) {
        BillDTO dto = new BillDTO();
        dto.setBillId(bill.getBillId());
        dto.setUnitId(bill.getUnit().getUnitId());
        dto.setUnitNumber(bill.getUnit().getUnitNumber());
        dto.setOwnerName(bill.getUnit().getOwnerNames());
        dto.setBillMonth(bill.getBillMonth());
        dto.setBillYear(bill.getBillYear());
        dto.setBillPeriod(Month.of(bill.getBillMonth()).name() + " " + bill.getBillYear());
        dto.setBillDate(bill.getBillDate());
        dto.setDueDate(bill.getDueDate());
        dto.setAmount(bill.getAmount());
        dto.setPreviousArrears(bill.getPreviousArrears());
        dto.setInterestOnArrears(bill.getInterestOnArrears());
        dto.setLateFee(bill.getLateFee());
        dto.setTotalAmount(bill.getTotalAmount());
        dto.setPaidAmount(bill.getPaidAmount());
        dto.setBalanceAmount(bill.getBalanceAmount());
        dto.setStatus(bill.getStatus().name());
        dto.setPaymentLink(bill.getPaymentLink());
        dto.setCashfreeOrderId(bill.getCashfreeOrderId());
        dto.setUnitAreaSqft(bill.getUnitAreaSqft());
        return dto;
    }

    private BillDTO mapToBillDTOWithLineItems(MaintenanceBill bill) {
        BillDTO dto = mapToBillDTO(bill);

        // Load line items
        List<BillLineItem> lineItems = bill.getLineItems();
        if (lineItems != null && !lineItems.isEmpty()) {
            List<BillLineItemDTO> lineItemDTOs = lineItems.stream()
                    .map(li -> BillLineItemDTO.builder()
                            .lineItemId(li.getLineItemId())
                            .chargeCode(li.getChargeCode())
                            .chargeName(li.getChargeName())
                            .calculationType(li.getCalculationType())
                            .rate(li.getRate())
                            .areaSqft(li.getAreaSqft())
                            .amount(li.getAmount())
                            .displayOrder(li.getDisplayOrder())
                            .build())
                    .collect(Collectors.toList());
            dto.setLineItems(lineItemDTOs);
        }

        return dto;
    }

    private PaymentDTO mapToPaymentDTO(MaintenancePayment payment) {
        return PaymentDTO.builder()
                .paymentId(payment.getPaymentId())
                .billId(payment.getBill().getBillId())
                .unitId(payment.getUnit().getUnitId())
                .unitNumber(payment.getUnit().getUnitNumber())
                .amount(payment.getAmount())
                .paymentDate(payment.getPaymentDate())
                .paymentMode(payment.getPaymentMode().name())
                .transactionId(payment.getTransactionId())
                .cashfreePaymentId(payment.getCashfreePaymentId())
                .cashfreeOrderId(payment.getCashfreeOrderId())
                .payerName(payment.getPayerName())
                .payerType(payment.getPayerType())
                .receiptNumber(payment.getReceiptNumber())
                .status(payment.getStatus().name())
                .remarks(payment.getRemarks())
                .verifiedOn(payment.getVerifiedOn())
                .verifiedBy(payment.getVerifiedBy())
                .build();
    }
}
