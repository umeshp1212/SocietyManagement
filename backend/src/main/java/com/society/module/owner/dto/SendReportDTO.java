package com.society.module.owner.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Outcome report for an owner email send operation.
 *
 * <p>Counts reconcile such that {@code sentCount + notEmailedCount == totalAttempted}
 * and {@code notEmailed.size() == notEmailedCount}. When no mail transport is
 * configured, {@code mailConfigured} is {@code false} and {@code sentCount} is 0.</p>
 */
@Data
@Builder
public class SendReportDTO {
    private int totalAttempted;
    private int sentCount;
    private int notEmailedCount;
    private boolean mailConfigured;
    private List<NotEmailedEntry> notEmailed;
}
