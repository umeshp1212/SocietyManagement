package com.society.module.visitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A resolved notification recipient for a visitor request: an owner or the active
 * tenant of the destination flat. Carries the identifiers the notification layer
 * (Task 6, FCM) needs to look up device tokens, plus display/contact fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisitorRecipient {

    public enum Type { OWNER, TENANT }

    private Type type;

    /** Owner id when {@code type == OWNER}; null otherwise. */
    private Long ownerId;

    /** Tenant id when {@code type == TENANT}; null otherwise. */
    private Long tenantId;

    private String name;
    private String phone;
    private String email;
}
