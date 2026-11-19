-- Suspense assignment now removes the refunded interest from the latest bill immediately.
-- Remembers which bill and how much, so reversing the assignment can put it back.
ALTER TABLE suspense_entries
    ADD COLUMN interest_refund_bill_id BIGINT NULL,
    ADD COLUMN interest_refund_bill_amount DECIMAL(12,2) NULL;
