import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PageShellComponent } from './page-shell.component';

@Component({
  selector: 'app-privacy',
  standalone: true,
  imports: [CommonModule, PageShellComponent],
  template: `
    <app-page-shell title="Privacy Policy" subtitle="Last updated: {{ lastUpdated }}">
      <div class="legal">
        <p>
          This Privacy Policy describes how Poonam Park View C &amp; D Wing CHS LTD
          ("the Society", "we", "us", "our") collects, uses and protects personal information
          when you use our society member portal ("Service"). By using the Service, you consent
          to the practices described here.
        </p>

        <h2>1. Information We Collect</h2>
        <ul>
          <li><strong>Account information:</strong> name, email address, phone number and role
              within the society.</li>
          <li><strong>Society &amp; unit data:</strong> unit details, owner/tenant records,
              maintenance bills and committee information entered by authorised users.</li>
          <li><strong>Payment information:</strong> payments are processed by a third-party
              payment gateway. We do not store full card or bank credentials on our servers.</li>
          <li><strong>Usage data:</strong> log data such as IP address, browser type and pages
              accessed, used to operate and improve the Service.</li>
        </ul>

        <h2>2. How We Use Information</h2>
        <ul>
          <li>To provide, operate and maintain the Service.</li>
          <li>To process maintenance payments and send receipts and notices.</li>
          <li>To respond to support requests and communicate service updates.</li>
          <li>To comply with legal and regulatory obligations.</li>
        </ul>

        <h2>3. Payment Processing</h2>
        <p>
          Online payments are handled securely through a PCI-DSS compliant third-party payment
          gateway. Your payment details are shared directly with the gateway and are subject to
          their privacy and security policies.
        </p>

        <h2>4. Data Sharing</h2>
        <p>
          We do not sell your personal information. We share data only with service providers
          (such as hosting and payment partners) who help us operate the Service, or where
          required by law.
        </p>

        <h2>5. Data Security</h2>
        <p>
          We use reasonable technical and organisational measures to protect your data against
          unauthorised access, loss or misuse. No method of transmission over the internet is
          fully secure, so we cannot guarantee absolute security.
        </p>

        <h2>6. Data Retention</h2>
        <p>
          We retain personal information for as long as your account is active or as needed to
          provide the Service and to comply with legal obligations.
        </p>

        <h2>7. Your Rights</h2>
        <p>
          You may request access to, correction of, or deletion of your personal information by
          contacting us. Some data may be retained where required for legal or accounting
          purposes.
        </p>

        <h2>8. Cookies</h2>
        <p>
          The Service may use cookies or similar technologies to keep you signed in and to
          understand usage. You can control cookies through your browser settings.
        </p>

        <h2>9. Changes to This Policy</h2>
        <p>
          We may update this Privacy Policy from time to time. Material changes will be notified
          through the Service or by email.
        </p>

        <h2>10. Contact</h2>
        <p>
          For privacy-related questions, contact us at
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
export class PrivacyComponent {
  lastUpdated = 'October 2026';
}
