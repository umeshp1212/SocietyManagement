import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-products',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <header class="header">
      <a routerLink="/" class="back-link">
        <span class="material-icons">arrow_back</span> Home
      </a>
      <h1>Services &amp; Charges</h1>
      <div class="subtitle">Poonam Park View C &amp; D Wing CHS LTD &mdash; member maintenance collection</div>
    </header>

    <main class="main">
      <section class="intro">
        <p>
          Poonam Park View C &amp; D Wing Co-operative Housing Society Ltd. is a registered
          co-operative housing society. This portal allows our members to view and pay their
          society maintenance charges and other applicable contributions online. All charges
          are billed and collected from members in Indian Rupees (INR, &#8377;). This portal is
          used only for collection of society dues from its own members and not for any
          commercial sale of goods or services to the public.
        </p>
      </section>

      <h2 class="section-title">What Members Can Do</h2>
      <div class="services-grid">
        <div class="service-card">
          <span class="material-icons">receipt_long</span>
          <h3>View Maintenance Bills</h3>
          <p>See monthly / quarterly maintenance bills raised for your flat, including dues and
             due dates.</p>
        </div>
        <div class="service-card">
          <span class="material-icons">account_balance_wallet</span>
          <h3>Pay Online</h3>
          <p>Pay society maintenance charges and other contributions securely online in INR
             through the payment gateway.</p>
        </div>
        <div class="service-card">
          <span class="material-icons">description</span>
          <h3>Download Receipts</h3>
          <p>Get digital payment receipts and view your payment history at any time.</p>
        </div>
        <div class="service-card">
          <span class="material-icons">groups</span>
          <h3>Society Information</h3>
          <p>View managing committee details and important society notices.</p>
        </div>
      </div>

      <h2 class="section-title">Maintenance Charges (INR)</h2>
      <p class="pricing-note">
        Maintenance and other charges are fixed by the society in its general body / managing
        committee meetings as per bye-laws. All amounts are in Indian Rupees (&#8377;). The
        actual amount payable for your flat is shown on your bill after you log in. The heads
        below are indicative.
      </p>

      <div class="pricing-grid">
        <div class="price-card">
          <div class="plan-name">Monthly Maintenance</div>
          <div class="plan-price">As per bill</div>
          <div class="plan-sub">Billed per flat</div>
          <ul>
            <li>Service &amp; common area charges</li>
            <li>Water charges</li>
            <li>Sinking fund &amp; repair fund</li>
            <li>Other charges as per bye-laws</li>
          </ul>
        </div>

        <div class="price-card featured">
          <div class="badge">Payable Online</div>
          <div class="plan-name">How Billing Works</div>
          <div class="plan-price">&#8377; INR</div>
          <div class="plan-sub">Shown after login</div>
          <ul>
            <li>Bill generated for your flat</li>
            <li>Exact amount visible in your account</li>
            <li>Pay the billed amount online</li>
            <li>Receipt issued on successful payment</li>
          </ul>
        </div>

        <div class="price-card">
          <div class="plan-name">Other Contributions</div>
          <div class="plan-price">As applicable</div>
          <div class="plan-sub">If levied by society</div>
          <ul>
            <li>Festival / event contributions</li>
            <li>Special repair contributions</li>
            <li>Arrears / interest on late payment</li>
            <li>As approved by the committee</li>
          </ul>
        </div>
      </div>

      <div class="annual-note">
        <strong>Note:</strong> This portal is for collecting maintenance charges and
        contributions from members of Poonam Park View C &amp; D Wing CHS Ltd. only. The exact
        amount payable is always displayed on your bill after login. For any clarification on
        your charges, please contact the society office.
      </div>

      <div class="cta">
        <a routerLink="/contact" class="btn">Contact the Society</a>
      </div>
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

    .main { flex:1; padding:40px 20px; max-width:1100px; margin:0 auto; width:100%; }
    .intro p { font-size:1rem; line-height:1.7; color:#444; max-width:860px; margin:0 auto; text-align:center; }

    .section-title { text-align:center; font-size:1.5rem; font-weight:500; color:#1565c0; margin:48px 0 24px; }
    .section-title::after { content:''; display:block; width:60px; height:3px; background:#1976d2; margin:10px auto 0; border-radius:2px; }

    .services-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(230px,1fr)); gap:20px; }
    .service-card { background:white; border-radius:12px; box-shadow:0 2px 12px rgba(0,0,0,0.08); padding:24px; text-align:center; }
    .service-card .material-icons { font-size:40px; color:#1976d2; margin-bottom:12px; }
    .service-card h3 { font-size:1.05rem; margin-bottom:8px; color:#333; }
    .service-card p { font-size:0.9rem; color:#666; line-height:1.6; }

    .pricing-note { text-align:center; color:#666; font-size:0.9rem; margin-bottom:24px; }
    .pricing-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(260px,1fr)); gap:24px; align-items:start; }
    .price-card { background:white; border-radius:12px; box-shadow:0 2px 12px rgba(0,0,0,0.08); padding:28px 24px; text-align:center; position:relative; border-top:3px solid #e3f2fd; }
    .price-card.featured { border-top:3px solid #1976d2; box-shadow:0 6px 24px rgba(25,118,210,0.18); }
    .badge { position:absolute; top:-12px; left:50%; transform:translateX(-50%); background:#1976d2; color:white; font-size:0.72rem; padding:4px 14px; border-radius:12px; text-transform:uppercase; letter-spacing:0.5px; }
    .plan-name { font-size:1.1rem; font-weight:600; color:#1565c0; margin-bottom:8px; }
    .plan-price { font-size:1.8rem; font-weight:700; color:#333; }
    .plan-price span { font-size:0.85rem; font-weight:400; color:#888; }
    .plan-sub { font-size:0.85rem; color:#888; margin:6px 0 16px; }
    .price-card ul { list-style:none; text-align:left; padding:0; }
    .price-card ul li { font-size:0.9rem; color:#555; padding:7px 0 7px 24px; position:relative; border-bottom:1px solid #f0f0f0; }
    .price-card ul li::before { content:'check'; font-family:'Material Icons'; position:absolute; left:0; color:#43a047; font-size:16px; }

    .annual-note { background:#f5f5f5; border-radius:10px; padding:18px 22px; margin:28px auto 0; max-width:860px; text-align:center; font-size:0.92rem; color:#555; }
    .cta { text-align:center; margin-top:36px; }
    .btn { background:#1976d2; color:white; padding:12px 32px; border-radius:6px; text-decoration:none; font-weight:500; display:inline-block; }
    .btn:hover { background:#1565c0; }

    .footer { background:#263238; color:#b0bec5; text-align:center; padding:24px 20px; font-size:0.8rem; margin-top:48px; }
    .footer .links { display:flex; flex-wrap:wrap; justify-content:center; gap:18px; margin-bottom:12px; }
    .footer .links a { color:#b0bec5; text-decoration:none; }
    .footer .links a:hover { color:white; }
    .footer .copyright { opacity:0.7; }

    @media (max-width:768px){ .header h1{font-size:1.5rem;} }
  `]
})
export class ProductsComponent {
  currentYear = new Date().getFullYear();
}
