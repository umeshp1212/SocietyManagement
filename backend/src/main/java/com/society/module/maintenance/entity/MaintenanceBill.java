package com.society.module.maintenance.entity;

import com.society.common.BaseEntity;
import com.society.module.owner.entity.Unit;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "maintenance_bills", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"unit_id", "bill_month", "bill_year"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceBill extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bill_id")
    private Long billId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    @Column(name = "bill_month", nullable = false)
    private Integer billMonth;

    @Column(name = "bill_year", nullable = false)
    private Integer billYear;

    @Column(name = "bill_date", nullable = false)
    private LocalDate billDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    /**
     * Sum of all line items (current month charges only)
     */
    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /**
     * Arrears principal carried from previous months (unpaid balance)
     */
    @Column(name = "previous_arrears", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal previousArrears = BigDecimal.ZERO;

    /**
     * 1% monthly interest on previous arrears
     */
    @Column(name = "interest_on_arrears", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal interestOnArrears = BigDecimal.ZERO;

    @Column(name = "late_fee", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal lateFee = BigDecimal.ZERO;

    /**
     * Grand total = amount (current charges) + previousArrears + interestOnArrears
     */
    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "paid_amount", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "balance_amount", precision = 10, scale = 2)
    private BigDecimal balanceAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private BillStatus status = BillStatus.UNPAID;

    @Column(name = "payment_link", length = 500)
    private String paymentLink;

    @Column(name = "cashfree_order_id", length = 100)
    private String cashfreeOrderId;

    /**
     * Area of unit at time of bill generation (for record keeping)
     */
    @Column(name = "unit_area_sqft", precision = 10, scale = 2)
    private BigDecimal unitAreaSqft;

    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    @OrderBy("displayOrder ASC")
    private List<BillLineItem> lineItems = new ArrayList<>();

    public enum BillStatus {
        UNPAID, PARTIALLY_PAID, PAID, OVERDUE
    }
}
