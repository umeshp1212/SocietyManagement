package com.society.module.vouchercategory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoucherCategoryCreateRequest {

    @NotBlank(message = "Code is required")
    @Size(max = 50, message = "Code must be at most 50 characters")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "Code must be uppercase letters, digits, and underscores")
    private String code;

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;

    @NotBlank(message = "Type is required")
    @Pattern(regexp = "^(EXPENSE|INCOME)$", message = "Type must be EXPENSE or INCOME")
    private String type;

    @Size(max = 255, message = "Description must be at most 255 characters")
    private String description;

    private Integer displayOrder;
}
