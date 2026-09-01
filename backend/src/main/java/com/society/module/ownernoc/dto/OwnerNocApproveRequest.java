package com.society.module.ownernoc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin approval payload. The admin may optionally provide the final certificate
 * body (finalContent); if omitted, the NOC type's default template is used.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerNocApproveRequest {
    private String finalContent;
}
