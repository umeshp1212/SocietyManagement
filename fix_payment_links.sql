UPDATE maintenance_bills SET payment_link = NULL, cashfree_order_id = NULL;
SELECT COUNT(*) as bills_reset FROM maintenance_bills WHERE payment_link IS NULL;
