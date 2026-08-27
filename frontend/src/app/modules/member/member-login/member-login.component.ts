import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MemberAuthService } from '@core/services/member-auth.service';

@Component({
  selector: 'app-member-login',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterModule,
    MatCardModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatIconModule, MatSnackBarModule,
    MatProgressSpinnerModule
  ],
  template: `
    <div class="member-login-container">
      <mat-card class="login-card">
        <div class="login-header">
          <mat-icon class="logo-icon">apartment</mat-icon>
          <h2>Member Portal</h2>
          <p>Login with your registered mobile number</p>
        </div>

        <mat-card-content>
          <!-- Step 1: Phone Number -->
          <form *ngIf="step === 'phone'" [formGroup]="phoneForm" (ngSubmit)="onSendOtp()">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Mobile Number</mat-label>
              <input matInput formControlName="phone" placeholder="Enter 10-digit mobile number"
                     maxlength="10" type="tel">
              <mat-icon matPrefix>phone</mat-icon>
              <mat-hint>Enter your registered mobile number</mat-hint>
              <mat-error *ngIf="phoneForm.get('phone')?.hasError('required')">
                Mobile number is required
              </mat-error>
              <mat-error *ngIf="phoneForm.get('phone')?.hasError('pattern')">
                Enter a valid 10-digit mobile number
              </mat-error>
            </mat-form-field>

            <div class="error-message" *ngIf="errorMessage">
              <mat-icon>error</mat-icon> {{ errorMessage }}
            </div>

            <button mat-raised-button color="primary" type="submit"
                    class="full-width action-btn"
                    [disabled]="phoneForm.invalid || loading">
              <mat-spinner *ngIf="loading" diameter="20" class="btn-spinner"></mat-spinner>
              <mat-icon *ngIf="!loading">sms</mat-icon>
              <span>{{ loading ? 'Sending OTP...' : 'Send OTP' }}</span>
            </button>
          </form>

          <!-- Step 2: OTP Verification -->
          <form *ngIf="step === 'otp'" [formGroup]="otpForm" (ngSubmit)="onVerifyOtp()">
            <div class="otp-info">
              <mat-icon color="primary">check_circle</mat-icon>
              <span>OTP sent to <strong>{{ maskedPhone }}</strong></span>
            </div>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Enter OTP</mat-label>
              <input matInput formControlName="otp" placeholder="Enter 6-digit OTP"
                     maxlength="6" type="text" autocomplete="one-time-code"
                     inputmode="numeric">
              <mat-icon matPrefix>lock</mat-icon>
              <mat-error *ngIf="otpForm.get('otp')?.hasError('required')">
                OTP is required
              </mat-error>
              <mat-error *ngIf="otpForm.get('otp')?.hasError('pattern')">
                OTP must be 6 digits
              </mat-error>
            </mat-form-field>

            <div class="error-message" *ngIf="errorMessage">
              <mat-icon>error</mat-icon> {{ errorMessage }}
            </div>

            <button mat-raised-button color="primary" type="submit"
                    class="full-width action-btn"
                    [disabled]="otpForm.invalid || loading">
              <mat-spinner *ngIf="loading" diameter="20" class="btn-spinner"></mat-spinner>
              <mat-icon *ngIf="!loading">login</mat-icon>
              <span>{{ loading ? 'Verifying...' : 'Verify & Login' }}</span>
            </button>

            <div class="resend-section">
              <button mat-button type="button" (click)="onResendOtp()"
                      [disabled]="resendCooldown > 0 || loading" color="primary">
                <mat-icon>refresh</mat-icon>
                {{ resendCooldown > 0 ? 'Resend in ' + resendCooldown + 's' : 'Resend OTP' }}
              </button>
              <button mat-button type="button" (click)="goBack()" color="accent">
                <mat-icon>arrow_back</mat-icon> Change Number
              </button>
            </div>
          </form>
        </mat-card-content>

        <div class="login-footer">
          <a routerLink="/login" class="admin-link">
            <mat-icon>admin_panel_settings</mat-icon> Admin Login
          </a>
        </div>
      </mat-card>
    </div>
  `,
  styles: [`
    .member-login-container {
      display: flex; justify-content: center; align-items: center;
      min-height: 100vh;
      background: linear-gradient(135deg, #00796b, #004d40);
    }
    .login-card {
      width: 420px; padding: 32px; border-radius: 16px;
      box-shadow: 0 8px 32px rgba(0,0,0,0.2);
    }
    .login-header { text-align: center; margin-bottom: 28px; }
    .login-header h2 { margin: 8px 0 4px; color: #00796b; font-size: 24px; }
    .login-header p { color: #666; margin: 0; font-size: 14px; }
    .logo-icon { font-size: 52px; height: 52px; width: 52px; color: #00796b; }
    .full-width { width: 100%; }
    .action-btn {
      height: 48px; font-size: 16px; margin-top: 12px;
      display: flex; align-items: center; justify-content: center; gap: 8px;
    }
    .btn-spinner { display: inline-block; }
    .error-message {
      display: flex; align-items: center; gap: 6px; color: #c62828;
      font-size: 13px; margin-bottom: 12px; padding: 8px;
      background: #ffebee; border-radius: 6px;
    }
    .error-message mat-icon { font-size: 18px; height: 18px; width: 18px; }
    .otp-info {
      display: flex; align-items: center; gap: 8px;
      padding: 12px 16px; margin-bottom: 20px;
      background: #e8f5e9; border-radius: 8px;
      font-size: 14px; color: #2e7d32;
    }
    .otp-info mat-icon { font-size: 20px; height: 20px; width: 20px; }
    .resend-section {
      display: flex; justify-content: space-between; align-items: center;
      margin-top: 12px;
    }
    .resend-section button { font-size: 13px; }
    .login-footer {
      text-align: center; margin-top: 20px; padding-top: 16px;
      border-top: 1px solid #eee;
    }
    .admin-link {
      display: inline-flex; align-items: center; gap: 4px;
      color: #666; text-decoration: none; font-size: 13px;
    }
    .admin-link:hover { color: #00796b; }
    .admin-link mat-icon { font-size: 16px; height: 16px; width: 16px; }
  `]
})
export class MemberLoginComponent {
  step: 'phone' | 'otp' = 'phone';
  phoneForm: FormGroup;
  otpForm: FormGroup;
  loading = false;
  errorMessage = '';
  maskedPhone = '';
  resendCooldown = 0;
  private cooldownInterval: any;

  constructor(
    private fb: FormBuilder,
    private memberAuth: MemberAuthService,
    private router: Router,
    private snackBar: MatSnackBar
  ) {
    // Redirect if already logged in
    if (this.memberAuth.isLoggedIn()) {
      this.router.navigate(['/member/dashboard']);
    }

    this.phoneForm = this.fb.group({
      phone: ['', [Validators.required, Validators.pattern(/^[6-9]\d{9}$/)]]
    });

    this.otpForm = this.fb.group({
      otp: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]]
    });
  }

  onSendOtp(): void {
    if (this.phoneForm.invalid) return;

    this.loading = true;
    this.errorMessage = '';
    const phone = this.phoneForm.value.phone;

    this.memberAuth.sendOtp(phone).subscribe({
      next: (res) => {
        this.loading = false;
        if (res.success) {
          this.step = 'otp';
          this.maskedPhone = phone.substring(0, 2) + '******' + phone.substring(8);
          this.startResendCooldown();
          this.snackBar.open('OTP sent to your mobile and email', 'Close', { duration: 3000 });
        }
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.error?.message || 'Failed to send OTP. Please try again.';
      }
    });
  }

  onVerifyOtp(): void {
    if (this.otpForm.invalid) return;

    this.loading = true;
    this.errorMessage = '';
    const phone = this.phoneForm.value.phone;
    const otp = this.otpForm.value.otp;

    this.memberAuth.verifyOtp(phone, otp).subscribe({
      next: (res) => {
        this.loading = false;
        if (res.success) {
          this.snackBar.open('Welcome, ' + res.data.ownerName, 'Close', { duration: 3000 });
          this.router.navigate(['/member/dashboard']);
        }
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.error?.message || 'Invalid OTP. Please try again.';
      }
    });
  }

  onResendOtp(): void {
    this.otpForm.reset();
    this.errorMessage = '';
    this.onSendOtp();
  }

  goBack(): void {
    this.step = 'phone';
    this.otpForm.reset();
    this.errorMessage = '';
    this.clearCooldown();
  }

  private startResendCooldown(): void {
    this.resendCooldown = 30;
    this.clearCooldown();
    this.cooldownInterval = setInterval(() => {
      this.resendCooldown--;
      if (this.resendCooldown <= 0) {
        this.clearCooldown();
      }
    }, 1000);
  }

  private clearCooldown(): void {
    if (this.cooldownInterval) {
      clearInterval(this.cooldownInterval);
      this.cooldownInterval = null;
    }
  }
}
