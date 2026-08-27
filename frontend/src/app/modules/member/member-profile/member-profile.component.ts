import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDividerModule } from '@angular/material/divider';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MemberAuthService } from '@core/services/member-auth.service';

@Component({
  selector: 'app-member-profile',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    MatCardModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatSnackBarModule,
    MatDividerModule, MatChipsModule, MatProgressSpinnerModule
  ],
  template: `
    <div class="profile-page">
      <!-- Header -->
      <div class="profile-header">
        <button mat-icon-button (click)="goBack()">
          <mat-icon>arrow_back</mat-icon>
        </button>
        <h2>My Profile</h2>
      </div>

      <div *ngIf="loading" class="loading">
        <mat-spinner diameter="40"></mat-spinner>
      </div>

      <div *ngIf="!loading && profile">
        <!-- Profile Info Card -->
        <mat-card class="info-card">
          <mat-card-content>
            <div class="profile-avatar">
              <mat-icon>person</mat-icon>
            </div>
            <h3>{{ profile.fullName }}</h3>
            <p class="unit-info" *ngIf="profile.unitNumber">
              {{ profile.unitNumber }}
              <span *ngIf="profile.floor"> | Floor {{ profile.floor }}</span>
            </p>

            <mat-divider></mat-divider>

            <div class="info-row">
              <mat-icon>phone</mat-icon>
              <div>
                <span class="label">Mobile Number</span>
                <span class="value">{{ profile.maskedMobile || 'Not set' }}</span>
              </div>
            </div>

            <div class="info-row">
              <mat-icon>email</mat-icon>
              <div>
                <span class="label">Email Address</span>
                <span class="value">{{ profile.maskedEmail || 'Not set' }}</span>
              </div>
            </div>
          </mat-card-content>
        </mat-card>

        <!-- Update Request Form -->
        <mat-card class="update-card">
          <mat-card-header>
            <mat-card-title>
              <mat-icon>edit</mat-icon> Update Contact Details
            </mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <p class="update-hint" *ngIf="!profile.hasPendingRequest">
              Submit a request to update your mobile or email. Admin will review and approve.
            </p>

            <div *ngIf="profile.hasPendingRequest" class="pending-banner">
              <mat-icon>hourglass_top</mat-icon>
              <span>You have a pending update request. Please wait for admin approval.</span>
            </div>

            <form *ngIf="!profile.hasPendingRequest" (ngSubmit)="onSubmit()">
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>New Mobile Number</mat-label>
                <input matInput [(ngModel)]="newMobile" name="newMobile"
                       placeholder="Enter new 10-digit mobile" maxlength="10" type="tel">
                <mat-icon matPrefix>phone</mat-icon>
                <mat-hint>Leave blank if not changing</mat-hint>
              </mat-form-field>

              <mat-form-field appearance="outline" class="full-width">
                <mat-label>New Email Address</mat-label>
                <input matInput [(ngModel)]="newEmail" name="newEmail"
                       placeholder="Enter new email" type="email">
                <mat-icon matPrefix>email</mat-icon>
                <mat-hint>Leave blank if not changing</mat-hint>
              </mat-form-field>

              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Reason for Update</mat-label>
                <input matInput [(ngModel)]="reason" name="reason"
                       placeholder="e.g., Changed my phone number">
              </mat-form-field>

              <div class="form-actions">
                <button mat-raised-button color="primary" type="submit"
                        [disabled]="submitting || (!newMobile && !newEmail)">
                  <mat-icon>send</mat-icon>
                  {{ submitting ? 'Submitting...' : 'Submit Request' }}
                </button>
              </div>
            </form>
          </mat-card-content>
        </mat-card>

        <!-- Request History -->
        <mat-card *ngIf="profile.updateRequests?.length > 0" class="history-card">
          <mat-card-header>
            <mat-card-title>Request History</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <div *ngFor="let req of profile.updateRequests" class="request-item">
              <div class="request-header">
                <span class="request-type">{{ req.fieldType }}</span>
                <span class="request-status" [class]="'status-' + req.status.toLowerCase()">
                  {{ req.status }}
                </span>
              </div>
              <div class="request-detail" *ngIf="req.newMobileMasked">
                Mobile: {{ req.oldMobileMasked }} &rarr; {{ req.newMobileMasked }}
              </div>
              <div class="request-detail" *ngIf="req.newEmailMasked">
                Email: {{ req.oldEmailMasked }} &rarr; {{ req.newEmailMasked }}
              </div>
              <div class="request-meta">
                {{ req.createdOn | date:'dd MMM yyyy, HH:mm' }}
                <span *ngIf="req.rejectionReason"> | Reason: {{ req.rejectionReason }}</span>
              </div>
            </div>
          </mat-card-content>
        </mat-card>
      </div>
    </div>
  `,
  styles: [`
    .profile-page { max-width: 600px; margin: 0 auto; padding: 20px; }
    .profile-header { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; }
    .profile-header h2 { margin: 0; }
    .loading { display: flex; justify-content: center; padding: 60px 0; }

    .info-card { text-align: center; margin-bottom: 16px; }
    .profile-avatar {
      width: 72px; height: 72px; border-radius: 50%; background: #e3f2fd;
      display: flex; align-items: center; justify-content: center; margin: 0 auto 12px;
    }
    .profile-avatar mat-icon { font-size: 36px; height: 36px; width: 36px; color: #1976d2; }
    .info-card h3 { margin: 0 0 4px; font-size: 20px; }
    .unit-info { color: #666; font-size: 14px; margin: 0 0 16px; }

    .info-row {
      display: flex; align-items: center; gap: 12px; padding: 12px 0;
      text-align: left;
    }
    .info-row mat-icon { color: #888; }
    .info-row .label { display: block; font-size: 12px; color: #888; }
    .info-row .value { display: block; font-size: 15px; font-weight: 500; }

    .update-card, .history-card { margin-bottom: 16px; }
    .update-card mat-card-title { display: flex; align-items: center; gap: 8px; font-size: 16px; }
    .update-hint { color: #666; font-size: 13px; margin-bottom: 16px; }
    .full-width { width: 100%; }
    .form-actions { display: flex; justify-content: flex-end; margin-top: 8px; }

    .pending-banner {
      display: flex; align-items: center; gap: 8px; padding: 12px 16px;
      background: #fff3e0; border-radius: 8px; color: #e65100; font-size: 14px;
    }

    .request-item {
      padding: 12px 0; border-bottom: 1px solid #eee;
    }
    .request-item:last-child { border-bottom: none; }
    .request-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
    .request-type { font-weight: 500; font-size: 13px; color: #555; text-transform: uppercase; }
    .request-status {
      font-size: 11px; font-weight: 500; padding: 2px 8px; border-radius: 12px;
    }
    .status-pending { background: #fff3e0; color: #e65100; }
    .status-approved { background: #e8f5e9; color: #2e7d32; }
    .status-rejected { background: #ffebee; color: #c62828; }
    .request-detail { font-size: 13px; color: #333; margin-bottom: 2px; }
    .request-meta { font-size: 11px; color: #999; margin-top: 4px; }
  `]
})
export class MemberProfileComponent implements OnInit {
  profile: any = null;
  loading = true;
  submitting = false;
  newMobile = '';
  newEmail = '';
  reason = '';

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
    this.loadProfile();
  }

  loadProfile(): void {
    this.loading = true;
    this.memberAuth.getProfile().subscribe({
      next: (res) => {
        this.loading = false;
        if (res.success) this.profile = res.data;
      },
      error: () => {
        this.loading = false;
        this.snackBar.open('Failed to load profile', 'Close', { duration: 3000 });
      }
    });
  }

  onSubmit(): void {
    if (!this.newMobile && !this.newEmail) return;

    this.submitting = true;
    this.memberAuth.submitProfileUpdateRequest({
      newMobile: this.newMobile || undefined,
      newEmail: this.newEmail || undefined,
      reason: this.reason || undefined
    }).subscribe({
      next: (res) => {
        this.submitting = false;
        if (res.success) {
          this.snackBar.open(res.message, 'Close', { duration: 5000 });
          this.newMobile = '';
          this.newEmail = '';
          this.reason = '';
          this.loadProfile();
        }
      },
      error: (err) => {
        this.submitting = false;
        this.snackBar.open(err.error?.message || 'Failed to submit request', 'Close', { duration: 5000 });
      }
    });
  }

  goBack(): void {
    this.router.navigate(['/member/dashboard']);
  }
}
