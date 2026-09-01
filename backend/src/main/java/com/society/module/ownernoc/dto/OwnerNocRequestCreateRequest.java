package com.society.module.ownernoc.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload for an owner submitting a NOC request from the member portal.
 * The owner supplies the type, optional unit, addressee, and free-text details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerNocRequestCreateRequest {

    @NotNull(message = "NOC type is required")
    private Long nocTypeId;

    /** Optional: the specific unit this NOC concerns. */
    private Long unitId;

    /** Whom the certificate should be addressed to (e.g. "HDFC Bank Ltd."). */
    private String addressee;

    /** Free-text purpose / details (loan account, reason, etc.). */
    private String details;
}
