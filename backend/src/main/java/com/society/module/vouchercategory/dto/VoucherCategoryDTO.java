package com.society.module.vouchercategory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoucherCategoryDTO {
    private Long categoryId;
    private String code;
    private String name;
    private String type;
    private String description;
    private Integer displayOrder;
    private Boolean isActive;
    private String createdBy;
    private LocalDateTime createdOn;
    private String modifiedBy;
    private LocalDateTime modifiedOn;
}
