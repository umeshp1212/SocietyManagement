package com.society.module.ownernoc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NocTypeDTO {
    private Long nocTypeId;
    private String code;
    private String name;
    private String description;
    private String defaultTemplate;
    private Integer displayOrder;
    private Boolean isActive;
}
