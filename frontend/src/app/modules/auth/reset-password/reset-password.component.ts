import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { environment } from '@env/environment';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterModule, MatCardModule,
    MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule,
    MatProgressSpinnerModule, MatProgressBarModule, MatSnackBarModule
  ],
  template: `
    <div class="auth-container">
      <mat-card class="auth-card">
        <!-- Loading State -->
        <div *ngIf="validating" class="loading-state">
          <mat-spinner diameter="40"></mat-spinner>
          <p>Validating reset link...</p>
        </div>

        <!-- Invalid Token -->
        <div *ngIf="!validating && !tokenValid && !resetSuccess" class="error-state">
          <mat-icon class="error-icon">link_off</mat-icon>
          <h3>Invalid or Expired Link</h3>
          <p>This password reset link is no longer valid. It may have expired or already been used.</p>
          <button mat-raised-button color="primary" routerLink="/forgot-password">
            <mat-icon>refresh</mat-icon> Request New Link
          </button>
          <div class="back-link">
            <a mat-button routerLink="/login">
              <mat-icon>arrow_back</mat-icon> Back to Login
            </a>
          </div>
        </div>

        <!-- Reset Form -->
        <div *ngIf="!validating && tokenValid && !resetSuccess">
          <mat-card-header>
            <mat-card-title>
              <mat-icon class="title-icon">lock_reset</mat-icon>
              Set New Password
            </mat-card-title>
            <mat-card-subtitle>Enter your new password below</mat-card-subtitle>
          </mat-card-header>

          <mat-card-content>
            <form [formGroup]="resetForm" (ngSubmit)="onSubmit()">
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>New Password</mat-label>
                <input matInput [type]="hidePassword ? 'password' : 'text'" formControlName="newPassword">
                <mat-icon matPrefix>lock</mat-icon>
                <button mat-icon-button matSuffix (click)="hidePassword = !hidePassword" type="button">
                  <mat-icon>{{ hidePassword ? 'visibility_off' : 'visibility' }}</mat-icon>
                </button>
                <mat-error *ngIf="resetForm.get('newPassword')?.hasError('required')">Password is required</mat-error>
                <mat-error *ngIf="resetForm.get('newPassword')?.hasError('minlength')">Minimum 6 characters</mat-error>
              </mat-form-field>

              <!-- Password Strength -->
              <div class="strength-section" *ngIf="resetForm.get('newPassword')?.value">
                <mat-progress-bar mode="determinate" [value]="strengthPercent"
                                  [color]="strengthPercent >= 60 ? 'primary' : 'warn'">
                </mat-progress-bar>
                <span class="strength-label" [style.color]="strengthColor">{{ strengthLabel }}</span>
              </div>

              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Confirm New Password</mat-label>
                <input matInput [type]="hideConfirm ? 'password' : 'text'" formControlName="confirmPassword">
                <mat-icon matPrefix>lock_reset</mat-icon>
                <button mat-icon-button matSuffix (click)="hideConfirm = !hideConfirm" type="button">
                  <mat-icon>{{ hideConfirm ? 'visibility_off' : 'visibility' }}</mat-icon>
                </button>
                <mat-error *ngIf="resetForm.get('confirmPassword')?.hasError('required')">
                  Please confirm your password
                </mat-error>
              </mat-form-field>

              <div *ngIf="resetForm.errors?.['mismatch'] && resetForm.get('confirmPassword')?.touched"
                   class="error-msg">
                <mat-icon class="err-icon">error</mat-icon> Passwords do not match
              </div>

              <button mat-raised-button color="primary" type="submit" class="full-width submit-btn"
                      [disabled]="resetForm.invalid || submitting">
                <mat-spinner *ngIf="submitting" diameter="20" class="btn-spinner"></mat-spinner>
                <span *ngIf="!submitting">Reset Password</span>
                <span *ngIf="submitting">Resetting...</span>
              </button>
            </form>
          </mat-card-content>
        </div>

        <!-- Success State -->
        <div *ngIf="resetSuccess" class="success-state">
          <mat-icon class="success-icon">check_circle</mat-icon>
          <h3>Password Reset Successful</h3>
          <p>Your password has been updated. You can now login with your new password.</p>
          <button mat-raised-button color="primary" routerLink="/login">
            <mat-icon>login</mat-icon> Go to Login
          </button>
        </div>
      </mat-card>
    </div>
  `,
  styles: [`
    .auth-container { display: flex; justify-content: center; align-items: center; min-height: 80vh; padding: 16px; }
    .auth-card { max-width: 440px; width: 100%; padding: 24px; }
    .title-icon { vertical-align: middle; margin-right: 8px; }
    .full-width { width: 100%; }
    .submit-btn { height: 48px; font-size: 16px; margin-top: 12px; }
    .btn-spinner { display: inline-block; margin-right: 8px; }
    .loading-state { text-align: center; padding: 48px 0; }
    .loading-state p { color: #666; margin-top: 16px; }
    .error-state { text-align: center; padding: 32px 0; }
    .error-icon { font-size: 64px; height: 64px; width: 64px; color: #d32f2f; }
    .error-state h3 { margin: 16px 0 8px; }
    .error-state p { color: #666; margin-bottom: 24px; }
    .success-state { text-align: center; padding: 32px 0; }
    .success-icon { font-size: 64px; height: 64px; width: 64px; color: #2e7d32; }
    .success-state h3 { margin: 16px 0 8px; }
    .success-state p { color: #666; margin-bottom: 24px; }
    .error-msg { color: #c62828; font-size: 13px; margin-bottom: 12px; display: flex; align-items: center; gap: 6px; }
    .err-icon { font-size: 18px; height: 18px; width: 18px; }
    .strength-section { margin-bottom: 16px; }
    .strength-label { font-size: 12px; font-weight: 500; margin-top: 4px; display: block; }
    .back-link { margin-top: 16px; }
  `]
})
export class ResetPasswordComponent implements OnInit {
  resetForm: FormGroup;
  token = '';
  validating = true;
  tokenValid = false;
  resetSuccess = false;
  submitting = false;
  hidePassword = true;
  hideConfirm = true;

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private http: HttpClient,
    private snackBar: MatSnackBar
  ) {
    this.resetForm = this.fb.group({
      newPassword: ['', [Validators.required, Validators.minLength(6)]],
      confirmPassword: ['', Validators.required]
    }, { validators: this.passwordMatchValidator });
  }

  ngOnInit(): void {
    this.token = this.route.snapshot.queryParams['token'] || '';

    if (!this.token) {
      this.validating = false;
      this.tokenValid = false;
      return;
    }

    // Validate token
    this.http.get<any>(`${environment.apiUrl}/auth/reset-password/validate`, {
      params: { token: this.token }
    }).subscribe({
      next: (res) => {
        this.validating = false;
        this.tokenValid = res.success;
      },
      error: () => {
        this.validating = false;
        this.tokenValid = false;
      }
    });
  }

  passwordMatchValidator(group: FormGroup): { [key: string]: boolean } | null {
    const pass = group.get('newPassword')?.value;
    const confirm = group.get('confirmPassword')?.value;
    return pass === confirm ? null : { mismatch: true };
  }

  get strengthPercent(): number {
    return this.calculateStrength(this.resetForm.get('newPassword')?.value || '').percent;
  }

  get strengthColor(): string {
    return this.calculateStrength(this.resetForm.get('newPassword')?.value || '').color;
  }

  get strengthLabel(): string {
    return this.calculateStrength(this.resetForm.get('newPassword')?.value || '').label;
  }

  calculateStrength(password: string): { percent: number; color: string; label: string } {
    let score = 0;
    if (password.length >= 6) score++;
    if (password.length >= 8) score++;
    if (/[A-Z]/.test(password)) score++;
    if (/[0-9]/.test(password)) score++;
    if (/[^A-Za-z0-9]/.test(password)) score++;

    if (score <= 1) return { percent: 20, color: '#d32f2f', label: 'Weak' };
    if (score === 2) return { percent: 40, color: '#f57c00', label: 'Fair' };
    if (score === 3) return { percent: 60, color: '#fbc02d', label: 'Good' };
    if (score === 4) return { percent: 80, color: '#689f38', label: 'Strong' };
    return { percent: 100, color: '#2e7d32', label: 'Very Strong' };
  }

  onSubmit(): void {
    if (this.resetForm.invalid) return;
    this.submitting = true;

    const { newPassword } = this.resetForm.value;

    this.http.post<any>(`${environment.apiUrl}/auth/reset-password`, {
      token: this.token,
      newPassword
    }).subscribe({
      next: (res) => {
        this.submitting = false;
        if (res.success) {
          this.resetSuccess = true;
        }
      },
      error: (err) => {
        this.submitting = false;
        this.snackBar.open(
          err.error?.message || 'Failed to reset password. Token may have expired.',
          'Close', { duration: 5000 }
        );
      }
    });
  }
}
