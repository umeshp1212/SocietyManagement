package com.society.module.voucher.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "voucher_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoucherDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "document_id")
    private Long documentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id", nullable = false)
    private Voucher voucher;

    @Column(name = "document_name", nullable = false, length = 200)
    private String documentName;

    @Column(name = "document_type", length = 50)
    private String documentType;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "uploaded_by", length = 100)
    private String uploadedBy;

    @Column(name = "uploaded_on")
    private LocalDateTime uploadedOn = LocalDateTime.now();
}
