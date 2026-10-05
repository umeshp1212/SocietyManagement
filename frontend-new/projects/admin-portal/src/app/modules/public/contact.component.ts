import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PageShellComponent } from './page-shell.component';

@Component({
  selector: 'app-contact',
  standalone: true,
  imports: [CommonModule, PageShellComponent],
  template: `
    <app-page-shell title="Contact Us" subtitle="We're here to help">
      <p class="lead">
        For any questions about maintenance charges, online payments, receipts or society
        matters, please reach out using the details below. We aim to respond to all queries
        within 1&ndash;2 business days.
      </p>

      <div class="contact-grid">
        <div class="contact-card">
          <span class="material-icons">business</span>
          <h3>Society Name</h3>
          <p>Poonam Park View C &amp; D Wing CHS LTD</p>
        </div>
        <div class="contact-card">
          <span class="material-icons">email</span>
          <h3>Email</h3>
          <p><a href="mailto:ppvsociety.cdwing@gmail.com">ppvsociety.cdwing&#64;gmail.com</a></p>
        </div>
        <div class="contact-card">
          <span class="material-icons">call</span>
          <h3>Phone</h3>
          <p><a href="tel:+919404493442">+91 94044 93442</a></p>
        </div>
        <div class="contact-card">
          <span class="material-icons">place</span>
          <h3>Registered Address</h3>
          <p>
            S.No. 5, 5B, 5D, 5F, 5G, Park Avenue-L5 &amp; 6,<br>
            Dongre, Naringi, Global City,<br>
            Virar (W), Maharashtra &ndash; 401303, India
          </p>
        </div>
      </div>

      <div class="hours">
        <h3>Business Hours</h3>
        <p>Monday to Saturday, 10:00 AM &ndash; 6:00 PM IST (excluding public holidays)</p>
      </div>
    </app-page-shell>
  `,
  styles: [`
    .lead { font-size:1rem; line-height:1.7; color:#444; text-align:center; margin-bottom:32px; }
    .contact-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(230px,1fr)); gap:20px; }
    .contact-card { background:white; border-radius:12px; box-shadow:0 2px 12px rgba(0,0,0,0.08); padding:24px; text-align:center; }
    .contact-card .material-icons { font-size:36px; color:#1976d2; margin-bottom:10px; }
    .contact-card h3 { font-size:1rem; margin-bottom:8px; color:#1565c0; }
    .contact-card p { font-size:0.92rem; color:#555; line-height:1.6; }
    .contact-card a { color:#1976d2; text-decoration:none; }
    .contact-card a:hover { text-decoration:underline; }
    .hours { text-align:center; margin-top:32px; background:#f5f5f5; border-radius:10px; padding:20px; }
    .hours h3 { color:#1565c0; font-size:1.05rem; margin-bottom:6px; }
    .hours p { color:#555; font-size:0.92rem; }
  `]
})
export class ContactComponent {}
