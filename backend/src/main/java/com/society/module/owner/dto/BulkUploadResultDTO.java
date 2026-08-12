package com.society.module.owner.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkUploadResultDTO {
    private int totalRecords;
    private int successCount;
    private int failedCount;
    @Builder.Default
    private List<String> errors = new ArrayList<>();
    @Builder.Default
    private List<String> successMessages = new ArrayList<>();
}
