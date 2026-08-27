import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '@env/environment';

@Component({
  selector: 'app-landing-page',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <!-- Header -->
    <header class="header">
      <h1>{{ societyName }}</h1>
      <div class="reg-number" *ngIf="regNumber">{{ regNumber }}</div>
      <div class="address" *ngIf="address">{{ address }}</div>
    </header>

    <!-- Main Content -->
    <main class="main">
      <h2 class="section-title">Management Committee</h2>

      <!-- Top Row: Chairman, Secretary, Treasurer -->
      <div class="top-row">
        <div class="loading" *ngIf="loading">Loading committee members...</div>
        <div *ngFor="let m of topMembers" class="member-card top-member">
          <img *ngIf="m.photoPath" [src]="apiUrl + '/files/view/' + m.photoPath"
               class="member-photo" [alt]="m.fullName">
          <div *ngIf="!m.photoPath" class="member-photo-placeholder">
            <span class="material-icons">person</span>
          </div>
          <div class="member-name">{{ m.fullName }}</div>
          <div class="member-designation">{{ m.designation }}</div>
        </div>
      </div>

      <!-- Other Members -->
      <div class="committee-grid">
        <div *ngFor="let m of otherMembers" class="member-card">
          <img *ngIf="m.photoPath" [src]="apiUrl + '/files/view/' + m.photoPath"
               class="member-photo" [alt]="m.fullName">
          <div *ngIf="!m.photoPath" class="member-photo-placeholder">
            <span class="material-icons">person</span>
          </div>
          <div class="member-name">{{ m.fullName }}</div>
          <div class="member-designation">{{ m.designation }}</div>
        </div>
      </div>

      <div *ngIf="!loading && topMembers.length === 0 && otherMembers.length === 0"
           style="text-align:center; color:#999; padding:20px;">
        No committee members found.
      </div>

      <!-- Login Section -->
      <div class="login-section">
        <p>Society members can login to access maintenance bills, payments, and more.</p>
        <a routerLink="/member-login" class="login-btn">
          <span class="material-icons">login</span>
          Member Login
        </a>
      </div>
    </main>

    <!-- Footer -->
    <footer class="footer">
      <div class="society-name">{{ societyName }}</div>
      <div>{{ address }}</div>
      <div class="copyright">&copy; {{ currentYear }} All Rights Reserved.</div>
    </footer>
  `,
  styles: [`
    :host { display: flex; flex-direction: column; min-height: 100vh; font-family: 'Roboto', sans-serif; color: #333; }

    .header {
      background: linear-gradient(135deg, #1565c0 0%, #1976d2 50%, #1e88e5 100%);
      color: white; padding: 40px 20px; text-align: center;
    }
    .header h1 { font-size: 2rem; font-weight: 700; margin-bottom: 6px; }
    .header .reg-number { font-size: 0.9rem; opacity: 0.85; }
    .header .address { font-size: 0.85rem; opacity: 0.75; margin-top: 4px; }

    .main { flex: 1; padding: 40px 20px; max-width: 1200px; margin: 0 auto; width: 100%; }

    .section-title {
      text-align: center; font-size: 1.5rem; font-weight: 500;
      color: #1565c0; margin-bottom: 30px; position: relative;
    }
    .section-title::after {
      content: ''; display: block; width: 60px; height: 3px;
      background: #1976d2; margin: 10px auto 0; border-radius: 2px;
    }

    .committee-grid { display: flex; flex-wrap: wrap; justify-content: center; gap: 24px; }

    .top-row {
      display: flex; flex-wrap: wrap; justify-content: center;
      gap: 24px; margin-bottom: 30px; width: 100%;
    }

    .member-card {
      background: white; border-radius: 12px; box-shadow: 0 2px 12px rgba(0,0,0,0.08);
      padding: 24px 20px; text-align: center; width: 220px;
      transition: transform 0.2s, box-shadow 0.2s;
    }
    .member-card:hover { transform: translateY(-4px); box-shadow: 0 6px 20px rgba(0,0,0,0.12); }
    .member-card.top-member { width: 250px; border-top: 3px solid #1976d2; }

    .member-photo {
      width: 100px; height: 100px; border-radius: 50%;
      object-fit: cover; margin-bottom: 12px; border: 3px solid #e3f2fd;
    }
    .member-photo-placeholder {
      width: 100px; height: 100px; border-radius: 50%; background: #e3f2fd;
      display: flex; align-items: center; justify-content: center; margin: 0 auto 12px;
    }
    .member-photo-placeholder .material-icons { font-size: 48px; color: #90caf9; }

    .member-name { font-size: 1rem; font-weight: 500; color: #333; margin-bottom: 4px; }
    .member-designation {
      font-size: 0.8rem; font-weight: 500; color: #1976d2;
      text-transform: uppercase; letter-spacing: 0.5px;
    }

    .login-section {
      text-align: center; margin-top: 40px; padding: 30px 20px;
      background: #f5f5f5; border-radius: 12px;
    }
    .login-section p { color: #666; margin-bottom: 16px; font-size: 0.9rem; }
    .login-btn {
      display: inline-flex; align-items: center; gap: 8px;
      background: #1976d2; color: white; padding: 12px 32px; border-radius: 6px;
      text-decoration: none; font-size: 1rem; font-weight: 500; transition: background 0.2s;
    }
    .login-btn:hover { background: #1565c0; }
    .login-btn .material-icons { font-size: 20px; }

    .footer {
      background: #263238; color: #b0bec5; text-align: center;
      padding: 24px 20px; font-size: 0.8rem;
    }
    .footer .society-name { color: white; font-weight: 500; margin-bottom: 4px; }
    .footer .copyright { margin-top: 8px; opacity: 0.7; }

    .loading { text-align: center; padding: 40px; color: #999; }

    @media (max-width: 768px) {
      .header h1 { font-size: 1.5rem; }
      .header { padding: 30px 16px; }
      .main { padding: 24px 16px; }
      .member-card, .member-card.top-member { width: 160px; padding: 16px 12px; }
      .member-photo, .member-photo-placeholder { width: 80px; height: 80px; }
      .member-photo-placeholder .material-icons { font-size: 36px; }
      .section-title { font-size: 1.2rem; }
    }
    @media (max-width: 480px) {
      .member-card, .member-card.top-member { width: 140px; }
    }
  `]
})
export class LandingPageComponent implements OnInit {
  apiUrl = environment.apiUrl;
  societyName = 'Loading...';
  regNumber = '';
  address = '';
  topMembers: any[] = [];
  otherMembers: any[] = [];
  loading = true;
  currentYear = new Date().getFullYear();

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.http.get<any>(`${this.apiUrl}/settings/public`).subscribe({
      next: (res) => {
        if (res.success && res.data) {
          const s = res.data;
          this.societyName = s.societyName || 'Society Management';
          this.regNumber = s.registrationNumber ? `Reg. No: ${s.registrationNumber}` : '';
          this.address = [s.addressLine1, s.addressLine2, s.city, s.state, s.pincode]
            .filter(Boolean).join(', ');
        }
      },
      error: () => { this.societyName = 'Society Management'; }
    });

    this.http.get<any>(`${this.apiUrl}/committee-members/public`).subscribe({
      next: (res) => {
        this.loading = false;
        if (res.success && res.data) {
          this.sortCommittee(res.data);
        }
      },
      error: () => { this.loading = false; }
    });
  }

  private sortCommittee(members: any[]): void {
    const topDesignations = ['chairman', 'secretary', 'treasurer'];

    members.forEach(m => {
      const desig = m.designation.toLowerCase();
      if (topDesignations.some(d => desig.includes(d))) {
        this.topMembers.push(m);
      } else {
        this.otherMembers.push(m);
      }
    });

    this.topMembers.sort((a, b) => {
      const order = (d: any) => {
        const dl = d.designation.toLowerCase();
        if (dl.includes('chairman')) return 0;
        if (dl.includes('secretary')) return 1;
        if (dl.includes('treasurer')) return 2;
        return 3;
      };
      return order(a) - order(b);
    });
  }
}
