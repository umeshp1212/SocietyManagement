import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

/**
 * Reusable public page shell: blue gradient header + content slot + footer with
 * the Cashfree-required policy navigation links.
 */
@Component({
  selector: 'app-page-shell',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <header class="header">
      <a routerLink="/" class="back-link">
        <span class="material-icons">arrow_back</span> Home
      </a>
      <h1>{{ title }}</h1>
      <div class="subtitle" *ngIf="subtitle">{{ subtitle }}</div>
    </header>

    <main class="main">
      <ng-content></ng-content>
    </main>

    <footer class="footer">
      <div class="links">
        <a routerLink="/products">Services &amp; Charges</a>
        <a routerLink="/contact">Contact Us</a>
        <a routerLink="/terms">Terms &amp; Conditions</a>
        <a routerLink="/refunds">Refunds &amp; Cancellations</a>
        <a routerLink="/privacy">Privacy Policy</a>
      </div>
      <div class="copyright">&copy; {{ currentYear }} All Rights Reserved.</div>
    </footer>
  `,
  styles: [`
    :host { display:flex; flex-direction:column; min-height:100vh; font-family:'Roboto',sans-serif; color:#333; }
    .header { background:linear-gradient(135deg,#1565c0 0%,#1976d2 50%,#1e88e5 100%); color:white; padding:48px 20px; text-align:center; position:relative; }
    .header h1 { font-size:2rem; font-weight:700; margin-bottom:6px; }
    .header .subtitle { font-size:0.95rem; opacity:0.85; }
    .back-link { position:absolute; left:20px; top:20px; color:white; text-decoration:none; display:inline-flex; align-items:center; gap:4px; font-size:0.9rem; opacity:0.9; }
    .back-link:hover { opacity:1; }
    .back-link .material-icons { font-size:18px; }

    .main { flex:1; padding:40px 20px; max-width:860px; margin:0 auto; width:100%; }

    .footer { background:#263238; color:#b0bec5; text-align:center; padding:24px 20px; font-size:0.8rem; margin-top:48px; }
    .footer .links { display:flex; flex-wrap:wrap; justify-content:center; gap:18px; margin-bottom:12px; }
    .footer .links a { color:#b0bec5; text-decoration:none; }
    .footer .links a:hover { color:white; }
    .footer .copyright { opacity:0.7; }

    @media (max-width:768px){ .header h1{font-size:1.5rem;} }
  `]
})
export class PageShellComponent {
  @Input() title = '';
  @Input() subtitle = '';
  currentYear = new Date().getFullYear();
}
