package com.society.module.visitor.dto;

import lombok.Data;

/**
 * Body for an owner/tenant rejecting a visitor request. The reason is optional but
 * surfaced to the guard so they can tell the visitor why entry was denied.
 */
@Data
public class VisitorRejectRequest {
    private String reason;
}
