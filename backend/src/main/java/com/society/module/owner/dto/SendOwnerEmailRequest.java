package com.society.module.owner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Request payload for sending an email to society owners.
 *
 * <p>The {@code recipientScope} determines how recipients are resolved server-side:
 * {@link RecipientScope#ALL} targets every active owner, while
 * {@link RecipientScope#SELECTED} targets only the owners whose IDs are supplied
 * in {@code ownerIds}.</p>
 */
@Data
public class SendOwnerEmailRequest {

    @NotNull(message = "Recipient scope is required")
    private RecipientScope recipientScope;

    /** Owner IDs to target; required (non-empty) when scope is {@link RecipientScope#SELECTED}. */
    private List<Long> ownerIds;

    @NotBlank(message = "Subject is required")
    @Size(max = 200, message = "Subject must not exceed 200 characters")
    private String subject;

    @NotBlank(message = "Body is required")
    @Size(max = 10000, message = "Body must not exceed 10000 characters")
    private String body;
}
