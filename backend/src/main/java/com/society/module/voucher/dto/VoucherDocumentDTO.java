package com.society.module.voucher.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoucherDocumentDTO {
    private Long documentId;
    private String documentName;
    private String documentType;
    private String filePath;
    private String uploadedBy;
    private LocalDateTime uploadedOn;
}
