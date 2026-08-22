package com.society.module.voucher.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "tds_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TdsConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tds_config_id")
    private Long tdsConfigId;

    @Column(name = "vendor_category", nullable = false, unique = true, length = 50)
    private String vendorCategory;

    @Column(name = "tds_section", length = 20)
    private String tdsSection;  // e.g., "194C", "194J"

    @Column(name = "tds_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal tdsRate;  // e.g., 1.00, 2.00, 10.00

    @Column(name = "threshold_amount", precision = 12, scale = 2)
    private BigDecimal thresholdAmount;  // TDS applicable only if amount exceeds this

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
