package com.society.module.owner.entity;

import com.society.enums.TransferType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "ownership_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OwnershipHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private Owner owner;

    @Column(name = "ownership_start_date", nullable = false)
    private LocalDate ownershipStartDate;

    @Column(name = "ownership_end_date")
    private LocalDate ownershipEndDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_type", nullable = false)
    private TransferType transferType;

    @Column(name = "transfer_document_path", length = 500)
    private String transferDocumentPath;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "recorded_by", length = 100)
    private String recordedBy;

    @Column(name = "recorded_on")
    private LocalDateTime recordedOn = LocalDateTime.now();
}
