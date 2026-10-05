import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PageShellComponent } from './page-shell.component';

@Component({
  selector: 'app-terms',
  standalone: true,
  imports: [CommonModule, PageShellComponent],
  template: `
    <app-page-shell title="Terms &amp; Conditions" subtitle="Last updated: {{ lastUpdated }}">
      <div class="legal">
        <p>
          These Terms &amp; Conditions ("Terms") govern your access to and use of the society
          management portal and related services ("Service") operated by
          Poonam Park View C &amp; D Wing CHS LTD ("the Society", "we", "us", "our"). By
          accessing or using the Service, you agree to be bound by these Terms. If you do not
          agree, please do not use the Service.
        </p>

        <h2>1. Service Description</h2>
        <p>
          The Service is an online portal that allows members of the Society to view their
          maintenance bills and pay society maintenance charges and other applicable
          contributions online. All charges are billed to and collected from members in Indian
          Rupees (INR, &#8377;). The Service is used solely for collection of society dues from
          its own members and not for the sale of any goods or services to the general public.
        </p>

        <h2>2. Eligibility &amp; Accounts</h2>
        <p>
          Access is intended for members of Poonam Park View C &amp; D Wing CHS LTD and
          authorised society personnel. You must provide accurate information and keep it up to
          date. You are responsible for maintaining the confidentiality of your login
          credentials and for all activity under your account.
        </p>

        <h2>3. Charges &amp; Payments</h2>
        <ul>
          <li>Maintenance and other charges are fixed by the Society as per its bye-laws and
              decisions of the general body / managing committee. All amounts are in INR.</li>
          <li>The exact amount payable for your flat is shown on your bill after you log in.</li>
          <li>Online payments are processed through a third-party payment gateway. By making a
              payment you also agree to the payment gateway's terms.</li>
          <li>Late payment may attract interest or charges as decided by the Society.</li>
        </ul>

        <h2>4. Acceptable Use</h2>
        <p>
          You agree not to misuse the Service, including attempting unauthorised access,
          uploading unlawful content, interfering with the portal's operation, or using it to
          violate any applicable law.
        </p>

        <h2>5. Data &amp; Privacy</h2>
        <p>
          Your use of the Service is also governed by our Privacy Policy, which explains how we
          collect and handle personal information.
        </p>

        <h2>6. Limitation of Liability</h2>
        <p>
          The Service is provided on an "as is" and "as available" basis. To the maximum extent
          permitted by law, the Society is not liable for any indirect, incidental or
          consequential damages arising from your use of the Service.
        </p>

        <h2>7. Governing Law</h2>
        <p>
          These Terms are governed by the laws of India and the Maharashtra Co-operative
          Societies Act and bye-laws applicable to the Society. Any disputes are subject to the
          jurisdiction of the courts at Virar / Palghar, Maharashtra.
        </p>

        <h2>8. Contact</h2>
        <p>
          For questions about these Terms, contact us at
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
export class TermsComponent {
  lastUpdated = 'October 2026';
}
