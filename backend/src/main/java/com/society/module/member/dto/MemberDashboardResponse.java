package com.society.module.member.dto;

import com.society.module.maintenance.dto.BillDTO;
import com.society.module.maintenance.dto.PaymentDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberDashboardResponse {

    private Long ownerId;
    private String ownerName;

    // Unit info
    private Long unitId;
    private String unitNumber;
    private String wing;
    private String floor;

    // Outstanding summary
    private BigDecimal totalOutstanding;
    private int outstandingBillCount;

    // Total paid across all bills
    private BigDecimal totalPaid;

    // Outstanding bills detail
    private List<BillDTO> outstandingBills;

    // Recent payment history (last 10)
    private List<PaymentDTO> recentPayments;

    // Online payment discount info
    private Boolean discountEnabled;
    private BigDecimal discountPercent;
    private Integer discountDueDays;
    private String discountMessage;
    private Boolean discountEligible;
}
