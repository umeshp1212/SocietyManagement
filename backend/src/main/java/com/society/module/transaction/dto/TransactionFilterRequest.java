package com.society.module.transaction.dto;

import com.society.module.maintenance.entity.MaintenancePayment;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Plain request DTO used to bind Transaction Page filter query parameters.
 * Absent/blank fields contribute no filter predicate downstream.
 */
@Data
public class TransactionFilterRequest {

    private LocalDate startDate;

    private LocalDate endDate;

    private MaintenancePayment.PaymentMode paymentMode;

    private List<MaintenancePayment.PaymentStatus> statuses;

    private String payerType;

    private Long unitId;

    private String unitSearch;

    private String reference;
}
