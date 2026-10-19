package com.society.module.settings.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "society_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocietySettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "society_name", nullable = false, length = 300)
    private String societyName;

    @Column(name = "address_line1", length = 300)
    private String addressLine1;

    @Column(name = "address_line2", length = 300)
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "pincode", length = 10)
    private String pincode;

    @Column(name = "registration_number", length = 100)
    private String registrationNumber;

    @Column(name = "registration_date", length = 20)
    private String registrationDate;

    @Column(name = "phone", length = 15)
    private String phone;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "chairman_name", length = 150)
    private String chairmanName;

    @Column(name = "secretary_name", length = 150)
    private String secretaryName;

    @Column(name = "treasurer_name", length = 150)
    private String treasurerName;

    @Column(name = "logo_path", length = 500)
    private String logoPath;

    /**
     * Active payment gateway: RAZORPAY or CASHFREE
     */
    @Column(name = "payment_gateway", length = 20)
    @Builder.Default
    private String paymentGateway = "RAZORPAY";

    // ===== Online Payment Discount Settings =====

    /**
     * Enable/disable online payment discount
     */
    @Column(name = "discount_enabled")
    @Builder.Default
    private Boolean discountEnabled = false;

    /**
     * Discount percentage on current month charges (e.g., 2.0 means 2%)
     */
    @Column(name = "discount_percent", precision = 5, scale = 2)
    @Builder.Default
    private java.math.BigDecimal discountPercent = java.math.BigDecimal.ZERO;

    /**
     * Number of days from bill date within which payment qualifies for discount
     * e.g., 10 means pay within 10 days of bill date to get the discount
     */
    @Column(name = "discount_due_days")
    @Builder.Default
    private Integer discountDueDays = 10;

    /**
     * Promotional message shown to members on the dashboard
     */
    @Column(name = "discount_message", length = 500)
    @Builder.Default
    private String discountMessage = "Pay online before the due date and get a discount!";

    // ===== Visitor / Delivery Gate-Approval Settings =====

    /**
     * Minutes a visitor/delivery entry request stays PENDING before the scheduler
     * auto-expires it to TIMED_OUT. Configurable by the society admin.
     */
    @Column(name = "visitor_approval_timeout_minutes")
    @Builder.Default
    private Integer visitorApprovalTimeoutMinutes = 10;

    // ===== Maintenance Bill Settings =====

    /**
     * Flat late fee applied once to a bill when the unit is carrying arrears. 0 = disabled.
     * DB-driven replacement for the old static {@code app.maintenance.late-fee} property so
     * the treasurer can change it from the admin portal without a redeploy.
     */
    @Column(name = "late_fee_amount", precision = 10, scale = 2)
    @Builder.Default
    private java.math.BigDecimal lateFeeAmount = java.math.BigDecimal.ZERO;

    /**
     * Day-of-month the maintenance bill is due. 0 (the default) means "last day of the
     * month"; a generated bill's due date is clamped to the month's real length, so 31 on a
     * 30-day month, or 0 anywhere, both resolve to the final calendar day.
     */
    @Column(name = "bill_due_day")
    @Builder.Default
    private Integer billDueDay = 0;

    // ===== Automated Monthly Bill Generation =====

    /**
     * Master on/off switch for the scheduled monthly bill-generation job. The admin
     * "Start/Stop" button flips this. When false the scheduler runs but does nothing.
     */
    @Column(name = "auto_bill_generation_enabled")
    @Builder.Default
    private Boolean autoBillGenerationEnabled = false;

    /**
     * Day-of-month on which bills are auto-generated (for the current month). Default 1.
     * 0 means "last day of the month"; any day past the month's length is clamped to the
     * last day, so 31 is safe in February.
     */
    @Column(name = "bill_generation_day")
    @Builder.Default
    private Integer billGenerationDay = 1;

    // ===== Automated Payment Reminders (email) =====

    /**
     * Master on/off switch for the scheduled email payment-reminder job. The admin
     * "Start/Stop" button flips this. When false the scheduler runs but sends nothing.
     */
    @Column(name = "auto_reminder_enabled")
    @Builder.Default
    private Boolean autoReminderEnabled = false;

    /**
     * Day-of-month on which reminder emails are sent to members with outstanding dues.
     * Default 5. 0 means "last day of the month"; any day past the month's length is clamped
     * to the last day.
     */
    @Column(name = "reminder_day_of_month")
    @Builder.Default
    private Integer reminderDayOfMonth = 5;
}
