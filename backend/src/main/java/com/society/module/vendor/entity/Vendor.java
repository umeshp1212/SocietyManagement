package com.society.module.vendor.entity;

import com.society.common.BaseEntity;
import com.society.enums.PaymentFrequency;
import com.society.enums.VendorCategory;
import com.society.enums.VendorStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "vendors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vendor extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vendor_id")
    private Long vendorId;

    @Column(name = "vendor_name", nullable = false, length = 200)
    private String vendorName;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private VendorCategory category;

    @Column(name = "contact_person", length = 150)
    private String contactPerson;

    @Column(name = "phone", nullable = false, length = 15)
    private String phone;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Column(name = "pan_number", length = 20)
    private String panNumber;

    @Column(name = "gst_number", length = 20)
    private String gstNumber;

    @Column(name = "bank_account_number", length = 30)
    private String bankAccountNumber;

    @Column(name = "bank_ifsc", length = 15)
    private String bankIfsc;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "agreement_start_date")
    private LocalDate agreementStartDate;

    @Column(name = "agreement_end_date")
    private LocalDate agreementEndDate;

    @Column(name = "contracted_amount", precision = 12, scale = 2)
    private BigDecimal contractedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_frequency")
    private PaymentFrequency paymentFrequency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private VendorStatus status = VendorStatus.ACTIVE;

    @OneToMany(mappedBy = "vendor", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<VendorDocument> documents = new ArrayList<>();
}
