package com.society.module.visitor.service;

import com.society.module.visitor.dto.VisitorRecipient;
import com.society.module.visitor.entity.VisitorRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fallback {@link VisitorNotifier} that simply logs that recipients would have been
 * notified, so the gate approval flow works end-to-end via in-app polling even without
 * FCM.
 *
 * <p>The FCM adapter added in Task 6 is annotated {@code @Primary}, so when it is present
 * it is injected in preference to this bean; this remains the only notifier until then.</p>
 */
@Component
@Slf4j
public class NoOpVisitorNotifier implements VisitorNotifier {

    @Override
    public void notifyNewRequest(VisitorRequest request, List<VisitorRecipient> recipients) {
        if (log.isInfoEnabled()) {
            log.info("Push not configured — visitor request {} for unit {} would notify {} recipient(s): {}",
                    request.getVisitorRequestId(),
                    request.getUnit() != null ? request.getUnit().getUnitNumber() : null,
                    recipients.size(),
                    recipients.stream().map(VisitorRecipient::getName).toList());
        }
    }
}
