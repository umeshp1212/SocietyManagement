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
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MemberAuthService, MemberUnitInfo } from '@core/services/member-auth.service';

@Component({
  selector: 'app-member-apply-noc',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    MatCardModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatSelectModule,
    MatSnackBarModule, MatProgressSpinnerModule
  ],
  template: `
    <div class="noc-page">
      <div class="page-header">
        <button mat-icon-button (click)="goBack()"><mat-icon>arrow_back</mat-icon></button>
        <h2>Apply for NOC</h2>
      </div>

      <div class="info-banner">
        <mat-icon>info</mat-icon>
        <span>Choose the type of No Objection Certificate you need and provide the details.
          The society admin will review and, once approved, the certificate will be emailed to you.</span>
      </div>

      <mat-card>
        <mat-card-content>
          <form (ngSubmit)="onSubmit()" #f="ngForm">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>NOC Type</mat-label>
              <mat-select [(ngModel)]="form.nocTypeId" name="nocTypeId" required
                          (selectionChange)="onTypeChange()">
                <mat-option *ngFor="let t of nocTypes" [value]="t.nocTypeId">{{ t.name }}</mat-option>
              </mat-select>
              <mat-hint *ngIf="selectedTypeDesc">{{ selectedTypeDesc }}</mat-hint>
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width" *ngIf="units.length > 0">
              <mat-label>Unit / Flat (optional)</mat-label>
              <mat-select [(ngModel)]="form.unitId" name="unitId">
                <mat-option [value]="null">-- None --</mat-option>
                <mat-option *ngFor="let u of units" [value]="u.unitId">
                  {{ u.unitNumber }}<span *ngIf="u.wing"> - Wing {{ u.wing }}</span>
                </mat-option>
              </mat-select>
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Addressed To (optional)</mat-label>
              <input matInput [(ngModel)]="form.addressee" name="addressee"
                     placeholder="e.g. HDFC Bank Ltd.">
              <mat-hint>Bank / authority the certificate should be addressed to</mat-hint>
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Details / Purpose</mat-label>
              <textarea matInput [(ngModel)]="form.details" name="details" rows="4"
                        placeholder="Describe the purpose, e.g. loan account number, reason for name change, etc."></textarea>
            </mat-form-field>

            <div class="form-actions">
              <button mat-raised-button color="primary" type="submit"
                      [disabled]="submitting || !form.nocTypeId">
                <mat-icon>send</mat-icon>
                {{ submitting ? 'Submitting...' : 'Submit Request' }}
              </button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>

      <!-- My Requests -->
      <mat-card class="history-card" *ngIf="myRequests.length > 0">
        <mat-card-header><mat-card-title>My NOC Requests</mat-card-title></mat-card-header>
        <mat-card-content>
          <div *ngFor="let r of myRequests" class="req-item">
            <div class="req-head">
              <span class="req-type">{{ r.nocTypeName }}</span>
              <span class="chip" [class]="'status-' + (r.status || '').toLowerCase()">{{ r.status }}</span>
            </div>
            <div class="req-meta">
              <span *ngIf="r.addressee">To: {{ r.addressee }} | </span>
              {{ r.createdOn | date:'dd MMM yyyy, HH:mm' }}
              <span *ngIf="r.rejectionReason"> | Reason: {{ r.rejectionReason }}</span>
            </div>
          </div>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .noc-page { max-width: 640px; margin: 0 auto; padding: 20px; }
    .page-header { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
    .page-header h2 { margin: 0; }
    .info-banner { display: flex; align-items: flex-start; gap: 8px; padding: 12px 16px;
      background: #e3f2fd; border-radius: 8px; color: #0d47a1; font-size: 13px; margin-bottom: 16px; }
    .info-banner mat-icon { flex-shrink: 0; }
    .full-width { width: 100%; }
    .form-actions { display: flex; justify-content: flex-end; margin-top: 8px; }
    .history-card { margin-top: 16px; }
    .req-item { padding: 10px 0; border-bottom: 1px solid #eee; }
    .req-item:last-child { border-bottom: none; }
    .req-head { display: flex; justify-content: space-between; align-items: center; }
    .req-type { font-weight: 500; }
    .req-meta { font-size: 12px; color: #999; margin-top: 2px; }
    .chip { font-size: 11px; font-weight: 500; padding: 2px 8px; border-radius: 12px; }
    .status-pending { background: #fff3e0; color: #e65100; }
    .status-approved { background: #e8f5e9; color: #2e7d32; }
    .status-rejected { background: #ffebee; color: #c62828; }
  `]
})
export class MemberApplyNocComponent implements OnInit {
  nocTypes: any[] = [];
  units: MemberUnitInfo[] = [];
  myRequests: any[] = [];
  submitting = false;
  selectedTypeDesc = '';

  form: any = { nocTypeId: null, unitId: null, addressee: '', details: '' };

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
    this.units = this.memberAuth.getCurrentMember()?.units || [];
    this.loadTypes();
    this.loadMyRequests();
  }

  loadTypes(): void {
    this.memberAuth.getNocTypes().subscribe({
      next: (res) => { if (res.success) this.nocTypes = res.data || []; }
    });
  }

  loadMyRequests(): void {
    this.memberAuth.getMyNocRequests().subscribe({
      next: (res) => { if (res.success) this.myRequests = res.data || []; }
    });
  }

  onTypeChange(): void {
    const t = this.nocTypes.find(x => x.nocTypeId === this.form.nocTypeId);
    this.selectedTypeDesc = t?.description || '';
  }

  onSubmit(): void {
    if (!this.form.nocTypeId) {
      this.snackBar.open('Please select a NOC type.', 'Close', { duration: 3000 });
      return;
    }
    const payload = {
      nocTypeId: this.form.nocTypeId,
      unitId: this.form.unitId || undefined,
      addressee: this.form.addressee || undefined,
      details: this.form.details || undefined
    };
    this.submitting = true;
    this.memberAuth.submitNocRequest(payload).subscribe({
      next: (res) => {
        this.submitting = false;
        if (res.success) {
          this.snackBar.open(res.message || 'NOC request submitted.', 'Close', { duration: 5000 });
          this.form = { nocTypeId: null, unitId: null, addressee: '', details: '' };
          this.selectedTypeDesc = '';
          this.loadMyRequests();
        }
      },
      error: (err) => {
        this.submitting = false;
        this.snackBar.open(err.error?.message || 'Failed to submit NOC request.', 'Close', { duration: 5000 });
      }
    });
  }

  goBack(): void {
    this.router.navigate(['/member/dashboard']);
  }
}
