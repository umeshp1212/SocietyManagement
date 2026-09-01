import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MemberAuthService, MemberUnitInfo } from '@core/services/member-auth.service';

@Component({
  selector: 'app-member-tenant-register',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    MatCardModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatSelectModule,
    MatDatepickerModule, MatNativeDateModule,
    MatSnackBarModule, MatProgressSpinnerModule
  ],
  template: `
    <div class="tenant-page">
      <div class="page-header">
        <button mat-icon-button (click)="goBack()">
          <mat-icon>arrow_back</mat-icon>
        </button>
        <h2>Register a Tenant</h2>
      </div>

      <div class="info-banner">
        <mat-icon>info</mat-icon>
        <span>Fill in your tenant's details and submit. The society admin will review and approve.
          Once approved, a No Objection Certificate will be emailed to you.</span>
      </div>

      <mat-card>
        <mat-card-content>
          <form (ngSubmit)="onSubmit()" #f="ngForm">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Your Unit / Flat</mat-label>
              <mat-select [(ngModel)]="form.unitId" name="unitId" required>
                <mat-option *ngFor="let u of units" [value]="u.unitId">
                  {{ u.unitNumber }}<span *ngIf="u.wing"> - Wing {{ u.wing }}</span>
                </mat-option>
              </mat-select>
            </mat-form-field>

            <h4 class="section-title">Tenant Details</h4>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Tenant Full Name</mat-label>
              <input matInput [(ngModel)]="form.tenantName" name="tenantName" required maxlength="150">
            </mat-form-field>

            <div class="row">
              <mat-form-field appearance="outline">
                <mat-label>Contact Number</mat-label>
                <input matInput [(ngModel)]="form.contactNumber" name="contactNumber" required
                       maxlength="15" type="tel">
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Email (optional)</mat-label>
                <input matInput [(ngModel)]="form.email" name="email" type="email" maxlength="100">
                <mat-hint>If provided, tenant is CC'd on the NOC</mat-hint>
              </mat-form-field>
            </div>

            <div class="row">
              <mat-form-field appearance="outline">
                <mat-label>Aadhar Number (optional)</mat-label>
                <input matInput [(ngModel)]="form.aadharNumber" name="aadharNumber">
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>PAN (optional)</mat-label>
                <input matInput [(ngModel)]="form.panNumber" name="panNumber" maxlength="20">
              </mat-form-field>
            </div>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Permanent Address (optional)</mat-label>
              <textarea matInput [(ngModel)]="form.permanentAddress" name="permanentAddress" rows="2"></textarea>
            </mat-form-field>

            <h4 class="section-title">Agreement</h4>

            <div class="row">
              <mat-form-field appearance="outline">
                <mat-label>Agreement Start Date</mat-label>
                <input matInput [matDatepicker]="startPicker" [(ngModel)]="rentStartDate"
                       name="rentStartDate" required>
                <mat-datepicker-toggle matSuffix [for]="startPicker"></mat-datepicker-toggle>
                <mat-datepicker #startPicker></mat-datepicker>
                <mat-hint>Non-occupancy charge applies from this date</mat-hint>
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Agreement End Date (optional)</mat-label>
                <input matInput [matDatepicker]="endPicker" [(ngModel)]="rentEndDate" name="rentEndDate">
                <mat-datepicker-toggle matSuffix [for]="endPicker"></mat-datepicker-toggle>
                <mat-datepicker #endPicker></mat-datepicker>
              </mat-form-field>
            </div>

            <div class="row">
              <mat-form-field appearance="outline">
                <mat-label>Monthly Rent (optional)</mat-label>
                <input matInput [(ngModel)]="form.monthlyRentAmount" name="monthlyRentAmount" type="number">
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Security Deposit (optional)</mat-label>
                <input matInput [(ngModel)]="form.securityDeposit" name="securityDeposit" type="number">
              </mat-form-field>
            </div>

            <div class="form-actions">
              <button mat-raised-button color="primary" type="submit"
                      [disabled]="submitting || !f.form.valid || !form.unitId">
                <mat-icon>send</mat-icon>
                {{ submitting ? 'Submitting...' : 'Submit for Approval' }}
              </button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .tenant-page { max-width: 640px; margin: 0 auto; padding: 20px; }
    .page-header { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
    .page-header h2 { margin: 0; }
    .info-banner {
      display: flex; align-items: flex-start; gap: 8px; padding: 12px 16px;
      background: #e3f2fd; border-radius: 8px; color: #0d47a1; font-size: 13px;
      margin-bottom: 16px;
    }
    .info-banner mat-icon { flex-shrink: 0; }
    .section-title { margin: 8px 0 4px; font-size: 14px; color: #555; }
    .full-width { width: 100%; }
    .row { display: flex; gap: 12px; }
    .row mat-form-field { flex: 1; }
    .form-actions { display: flex; justify-content: flex-end; margin-top: 8px; }
    @media (max-width: 560px) { .row { flex-direction: column; gap: 0; } }
  `]
})
export class MemberTenantRegisterComponent implements OnInit {
  units: MemberUnitInfo[] = [];
  submitting = false;

  rentStartDate: Date | null = null;
  rentEndDate: Date | null = null;

  form: any = {
    unitId: null,
    tenantName: '',
    contactNumber: '',
    email: '',
    aadharNumber: '',
    panNumber: '',
    permanentAddress: '',
    monthlyRentAmount: null,
    securityDeposit: null
  };

  constructor(
    private memberAuth: MemberAuthService,
    private router: Router,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    if (!this.memberAuth.isLoggedIn()) {
      this.router.navigate(['/member-login']);
      return;
    }
    const member = this.memberAuth.getCurrentMember();
    this.units = member?.units || [];
    const selected = this.memberAuth.getSelectedUnit();
    this.form.unitId = selected?.unitId ?? (this.units[0]?.unitId ?? null);
  }

  onSubmit(): void {
    if (!this.form.unitId || !this.form.tenantName || !this.form.contactNumber || !this.rentStartDate) {
      this.snackBar.open('Please fill in the required fields.', 'Close', { duration: 3000 });
      return;
    }

    const payload = {
      ...this.form,
      email: this.form.email || undefined,
      aadharNumber: this.form.aadharNumber || undefined,
      panNumber: this.form.panNumber || undefined,
      permanentAddress: this.form.permanentAddress || undefined,
      monthlyRentAmount: this.form.monthlyRentAmount || undefined,
      securityDeposit: this.form.securityDeposit || undefined,
      rentStartDate: this.toIsoDate(this.rentStartDate),
      rentEndDate: this.rentEndDate ? this.toIsoDate(this.rentEndDate) : undefined
    };

    this.submitting = true;
    this.memberAuth.submitTenantRegistration(payload).subscribe({
      next: (res) => {
        this.submitting = false;
        if (res.success) {
          this.snackBar.open(res.message || 'Tenant registration submitted for approval.',
            'Close', { duration: 5000 });
          this.router.navigate(['/member/dashboard']);
        }
      },
      error: (err) => {
        this.submitting = false;
        this.snackBar.open(err.error?.message || 'Failed to submit tenant registration.',
          'Close', { duration: 5000 });
      }
    });
  }

  private toIsoDate(d: Date): string {
    // Local date -> yyyy-MM-dd (avoids timezone shift from toISOString)
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  goBack(): void {
    this.router.navigate(['/member/dashboard']);
  }
}
