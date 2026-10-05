import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PageShellComponent } from './page-shell.component';

@Component({
  selector: 'app-refunds',
  standalone: true,
  imports: [CommonModule, PageShellComponent],
  template: `
    <app-page-shell title="Refunds &amp; Cancellations" subtitle="Last updated: {{ lastUpdated }}">
      <div class="legal">
        <p>
          This Refunds &amp; Cancellations Policy explains how refunds are handled for
          maintenance charges and other contributions paid online to
          Poonam Park View C &amp; D Wing CHS LTD. All amounts referenced are in Indian Rupees
          (INR, &#8377;).
        </p>

        <h2>1. Nature of Payments</h2>
        <p>
          Payments made through this portal are society maintenance charges and contributions
          levied on members as per the Society's bye-laws. As these are statutory/association
          dues and not a purchase of goods or services, payments are generally non-refundable
          once credited to the Society, except in the cases described below.
        </p>

        <h2>2. Duplicate or Erroneous Payments</h2>
        <ul>
          <li>If you are charged more than once for the same bill, the duplicate amount will be
              refunded after verification.</li>
          <li>If an amount is debited from your account but is not reflected against your dues
              due to a technical/gateway error, the amount will be refunded or adjusted against
              your next bill, at your option.</li>
          <li>If you are charged an incorrect amount, the excess will be refunded or adjusted.</li>
        </ul>

        <h2>3. Failed Transactions</h2>
        <p>
          If a payment fails but the amount is debited, it is normally auto-reversed by your bank
          or the payment gateway within 5&ndash;7 business days. If it is not, contact us with
          the transaction details and we will assist in resolving it.
        </p>

        <h2>4. How to Request a Refund</h2>
        <p>
          Email <a href="mailto:ppvsociety.cdwing@gmail.com">ppvsociety.cdwing&#64;gmail.com</a>
          with your flat/unit details, payment reference / transaction ID and the reason for the
          request. We will acknowledge within 2 business days.
        </p>

        <h2>5. Refund Processing Time</h2>
        <p>
          Approved refunds are processed to the original payment method within 7&ndash;10
          business days. The time taken for the amount to reflect depends on your bank or card
          issuer.
        </p>

        <h2>6. Contact</h2>
        <p>
          For any questions about payments, adjustments or refunds, contact
          <a href="mailto:ppvsociety.cdwing@gmail.com">ppvsociety.cdwing&#64;gmail.com</a>.
        </p>
      </div>
    </app-page-shell>
  `,
  styles: [`
    .legal { line-height:1.75; color:#444; font-size:0.96rem; }
    .legal h2 { color:#1565c0; font-size:1.15rem; margin:28px 0 10px; }
    .legal p { margin-bottom:12px; }
    .legal ul { margin:0 0 12px 20px; }
    .legal ul li { margin-bottom:8px; }
    .legal a { color:#1976d2; }
  `]
})
export class RefundsComponent {
  lastUpdated = 'October 2026';
}
