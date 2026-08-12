package com.society.module.maintenance.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillDTO {
    private Long billId;
    private Long unitId;
    private String unitNumber;
    private String ownerName;
    private Integer billMonth;
    private Integer billYear;
    private String billPeriod;
    private LocalDate billDate;
    private LocalDate dueDate;

    // Current month charges (sum of line items)
    private BigDecimal amount;

    // Arrears tracking
    private BigDecimal previousArrears;
    private BigDecimal interestOnArrears;

    private BigDecimal lateFee;

    // Grand total = amount + previousArrears + interestOnArrears
    private BigDecimal totalAmount;

    private BigDecimal paidAmount;
    private BigDecimal balanceAmount;
    private String status;
    private String paymentLink;
    private String cashfreeOrderId;

    // Unit area for display
    private BigDecimal unitAreaSqft;

    // Line items breakdown (loaded on bill detail)
    private List<BillLineItemDTO> lineItems;
}
