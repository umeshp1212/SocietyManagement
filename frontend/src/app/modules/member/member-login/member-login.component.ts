import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MemberAuthService } from '@core/services/member-auth.service';
import { environment } from '@env/environment';

@Component({
  selector: 'app-member-login',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterModule,
    MatCardModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatIconModule, MatSnackBarModule,
    MatProgressSpinnerModule, MatSelectModule, MatAutocompleteModule
  ],
  template: `
    <div class="member-login-container">
      <mat-card class="login-card">
        <div class="login-header">
          <mat-icon class="logo-icon">apartment</mat-icon>
          <h2>Member Portal</h2>
          <p *ngIf="step === 'phone' || step === 'otp'">Login with your registered mobile number</p>
          <p *ngIf="step === 'register' || step === 'register-otp'">Register your details</p>
          <p *ngIf="step === 'register-done'">Registration submitted</p>
        </div>

        <mat-card-content>
          <!-- ==================== LOGIN FLOW ==================== -->

          <!-- Step 1: Phone Number -->
          <form *ngIf="step === 'phone'" [formGroup]="phoneForm" (ngSubmit)="onSendOtp()">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Mobile Number</mat-label>
              <input matInput formControlName="phone" placeholder="Enter 10-digit mobile number"
                     maxlength="10" type="tel">
              <mat-icon matPrefix>phone</mat-icon>
              <mat-hint>Enter your registered mobile number</mat-hint>
              <mat-error *ngIf="phoneForm.get('phone')?.hasError('required')">Mobile number is required</mat-error>
              <mat-error *ngIf="phoneForm.get('phone')?.hasError('pattern')">Enter a valid 10-digit number</mat-error>
            </mat-form-field>

            <div class="error-message" *ngIf="errorMessage && !showRegisterLink">
              <mat-icon>error</mat-icon> {{ errorMessage }}
            </div>

            <!-- Show register link when phone not found -->
            <div class="register-prompt" *ngIf="showRegisterLink">
              <p>First time here? Register your email and mobile to get started.</p>
              <button mat-stroked-button color="primary" type="button" (click)="goToRegister()">
                <mat-icon>person_add</mat-icon> Register Now
              </button>
            </div>

            <button mat-raised-button color="primary" type="submit"
                    class="full-width action-btn"
                    *ngIf="!showRegisterLink"
                    [disabled]="phoneForm.invalid || loading">
              <mat-spinner *ngIf="loading" diameter="20" class="btn-spinner"></mat-spinner>
              <mat-icon *ngIf="!loading">sms</mat-icon>
              <span>{{ loading ? 'Sending OTP...' : 'Send OTP' }}</span>
            </button>
          </form>

          <!-- Step 2: OTP Verification (Login) -->
          <form *ngIf="step === 'otp'" [formGroup]="otpForm" (ngSubmit)="onVerifyOtp()">
            <div class="otp-info">
              <mat-icon color="primary">check_circle</mat-icon>
              <span><strong>{{ maskedPhone }}</strong></span>
            </div>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Enter OTP</mat-label>
              <input matInput formControlName="otp" placeholder="Enter 6-digit OTP"
                     maxlength="6" type="text" autocomplete="one-time-code" inputmode="numeric">
              <mat-icon matPrefix>lock</mat-icon>
              <mat-error *ngIf="otpForm.get('otp')?.hasError('required')">OTP is required</mat-error>
              <mat-error *ngIf="otpForm.get('otp')?.hasError('pattern')">OTP must be 6 digits</mat-error>
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

          <!-- ==================== REGISTRATION FLOW ==================== -->

          <!-- Register Step 1: Email + Mobile + Unit -->
          <form *ngIf="step === 'register'" [formGroup]="registerForm" (ngSubmit)="onRegisterSendOtp()">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Select Your Flat/Unit</mat-label>
              <input matInput formControlName="unitSearch" [matAutocomplete]="unitAuto"
                     placeholder="Type to search (e.g., D-105)">
              <mat-icon matPrefix>apartment</mat-icon>
              <mat-autocomplete #unitAuto="matAutocomplete" [displayWith]="displayUnit"
                                (optionSelected)="onUnitSelected($event)">
                <mat-option *ngFor="let u of filteredUnits" [value]="u">
                  {{ u.unitNumber }} <span *ngIf="u.wing">({{ u.wing }} Wing)</span>
                </mat-option>
              </mat-autocomplete>
              <mat-error *ngIf="registerForm.get('unitId')?.hasError('required')">Please select your unit</mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Email Address</mat-label>
              <input matInput formControlName="email" placeholder="Enter your email" type="email">
              <mat-icon matPrefix>email</mat-icon>
              <mat-error *ngIf="registerForm.get('email')?.hasError('required')">Email is required</mat-error>
              <mat-error *ngIf="registerForm.get('email')?.hasError('email')">Enter a valid email</mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Mobile Number</mat-label>
              <input matInput formControlName="mobile" placeholder="Enter 10-digit mobile number"
                     maxlength="10" type="tel">
              <mat-icon matPrefix>phone</mat-icon>
              <mat-error *ngIf="registerForm.get('mobile')?.hasError('required')">Mobile is required</mat-error>
              <mat-error *ngIf="registerForm.get('mobile')?.hasError('pattern')">Enter a valid 10-digit number</mat-error>
            </mat-form-field>

            <div class="error-message" *ngIf="errorMessage">
              <mat-icon>error</mat-icon> {{ errorMessage }}
            </div>

            <button mat-raised-button color="primary" type="submit"
                    class="full-width action-btn"
                    [disabled]="registerForm.invalid || loading">
              <mat-spinner *ngIf="loading" diameter="20" class="btn-spinner"></mat-spinner>
              <mat-icon *ngIf="!loading">email</mat-icon>
              <span>{{ loading ? 'Sending OTP...' : 'Send OTP to Email' }}</span>
            </button>

            <div class="back-link">
              <button mat-button type="button" (click)="goBackToLogin()">
                <mat-icon>arrow_back</mat-icon> Back to Login
              </button>
            </div>
          </form>

          <!-- Register Step 2: Verify Email OTP -->
          <form *ngIf="step === 'register-otp'" [formGroup]="otpForm" (ngSubmit)="onRegisterVerifyOtp()">
            <div class="otp-info">
              <mat-icon color="primary">check_circle</mat-icon>
              <span>OTP sent to <strong>{{ regMaskedEmail }}</strong></span>
            </div>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Enter OTP</mat-label>
              <input matInput formControlName="otp" placeholder="Enter 6-digit OTP"
                     maxlength="6" type="text" autocomplete="one-time-code" inputmode="numeric">
              <mat-icon matPrefix>lock</mat-icon>
            </mat-form-field>

            <div class="error-message" *ngIf="errorMessage">
              <mat-icon>error</mat-icon> {{ errorMessage }}
            </div>

            <button mat-raised-button color="primary" type="submit"
                    class="full-width action-btn"
                    [disabled]="otpForm.invalid || loading">
              <mat-spinner *ngIf="loading" diameter="20" class="btn-spinner"></mat-spinner>
              <mat-icon *ngIf="!loading">verified</mat-icon>
              <span>{{ loading ? 'Verifying...' : 'Verify & Submit' }}</span>
            </button>

            <div class="back-link">
              <button mat-button type="button" (click)="step = 'register'; errorMessage = ''">
                <mat-icon>arrow_back</mat-icon> Change Details
              </button>
            </div>
          </form>

          <!-- Register Step 3: Done / Pending -->
          <div *ngIf="step === 'register-done'" class="done-state">
            <mat-icon class="done-icon">hourglass_top</mat-icon>
            <h3>Registration Submitted</h3>
            <p>Your request has been submitted for admin approval.</p>
            <p class="done-detail">
              Once approved, you can login using your mobile number <strong>{{ registerForm.value.mobile }}</strong>.
            </p>
            <button mat-raised-button color="primary" (click)="goBackToLogin()">
              <mat-icon>arrow_back</mat-icon> Back to Login
            </button>
          </div>
        </mat-card-content>

        <div class="login-footer" *ngIf="step !== 'register-done'">
          <a routerLink="/" class="admin-link">
            <mat-icon>home</mat-icon> Back to Home
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

    .register-prompt {
      background: #e3f2fd; border-radius: 8px; padding: 14px 16px;
      margin-bottom: 12px; text-align: center;
    }
    .register-prompt p { color: #1565c0; font-size: 13px; margin: 0 0 10px; }
    .back-link { text-align: center; margin-top: 12px; }

    .done-state { text-align: center; padding: 20px 0; }
    .done-icon { font-size: 56px; height: 56px; width: 56px; color: #e65100; }
    .done-state h3 { color: #e65100; margin: 8px 0; }
    .done-state p { color: #666; margin: 4px 0; font-size: 14px; }
    .done-detail { margin-top: 12px !important; }
  `]
})
export class MemberLoginComponent {
  step: 'phone' | 'otp' | 'register' | 'register-otp' | 'register-done' = 'phone';
  phoneForm: FormGroup;
  otpForm: FormGroup;
  registerForm: FormGroup;
  loading = false;
  errorMessage = '';
  maskedPhone = '';
  regMaskedEmail = '';
  showRegisterLink = false;
  units: any[] = [];
  filteredUnits: any[] = [];
  resendCooldown = 0;
  private cooldownInterval: any;

  constructor(
    private fb: FormBuilder,
    private memberAuth: MemberAuthService,
    private http: HttpClient,
    private router: Router,
    private snackBar: MatSnackBar
  ) {
    if (this.memberAuth.isLoggedIn()) {
      this.router.navigate(['/member/dashboard']);
    }

    this.phoneForm = this.fb.group({
      phone: ['', [Validators.required, Validators.pattern(/^[6-9]\d{9}$/)]]
    });

    this.otpForm = this.fb.group({
      otp: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]]
    });

    this.registerForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      mobile: ['', [Validators.required, Validators.pattern(/^[6-9]\d{9}$/)]],
      unitId: [null, Validators.required],
      unitSearch: ['']
    });

    // Filter units as user types
    this.registerForm.get('unitSearch')?.valueChanges.subscribe(val => {
      if (typeof val === 'string') {
        const search = val.toLowerCase();
        this.filteredUnits = this.units.filter(u =>
          u.unitNumber.toLowerCase().includes(search) ||
          (u.wing && u.wing.toLowerCase().includes(search))
        );
      }
    });
  }

  // ==================== LOGIN FLOW ====================

  onSendOtp(): void {
    if (this.phoneForm.invalid) return;
    this.loading = true;
    this.errorMessage = '';
    this.showRegisterLink = false;

    this.memberAuth.sendOtp(this.phoneForm.value.phone).subscribe({
      next: (res) => {
        this.loading = false;
        if (res.success) {
          this.step = 'otp';
          this.maskedPhone = res.data || '';
          this.startResendCooldown();
          this.snackBar.open(res.message || 'OTP sent', 'Close', { duration: 3000 });
        }
      },
      error: (err) => {
        this.loading = false;
        const msg = err.error?.message || '';
        this.errorMessage = msg || 'Failed to send OTP.';
        // Show register link if phone not found
        if (msg.toLowerCase().includes('no member found') || msg.toLowerCase().includes('not found') || msg.toLowerCase().includes('register')) {
          this.showRegisterLink = true;
        }
      }
    });
  }

  onVerifyOtp(): void {
    if (this.otpForm.invalid) return;
    this.loading = true;
    this.errorMessage = '';

    this.memberAuth.verifyOtp(this.phoneForm.value.phone, this.otpForm.value.otp).subscribe({
      next: (res) => {
        this.loading = false;
        if (res.success) {
          this.snackBar.open('Welcome, ' + res.data.ownerName, 'Close', { duration: 3000 });
          this.router.navigate(['/member/dashboard']);
        }
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.error?.message || 'Invalid OTP.';
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

  // ==================== REGISTRATION FLOW ====================

  goToRegister(): void {
    this.step = 'register';
    this.errorMessage = '';
    this.showRegisterLink = false;
    this.registerForm.patchValue({ mobile: this.phoneForm.value.phone });
    // Load units for dropdown
    if (this.units.length === 0) {
      this.http.get<any>(`${environment.apiUrl}/member/auth/register/units`).subscribe({
        next: (res) => {
          if (res.success) {
            this.units = res.data;
            this.filteredUnits = res.data;
          }
        }
      });
    }
  }

  displayUnit = (unit: any): string => {
    return unit ? `${unit.unitNumber}${unit.wing ? ' (' + unit.wing + ' Wing)' : ''}` : '';
  };

  onUnitSelected(event: any): void {
    const unit = event.option.value;
    this.registerForm.patchValue({ unitId: unit.unitId });
  }

  goBackToLogin(): void {
    this.step = 'phone';
    this.errorMessage = '';
    this.showRegisterLink = false;
  }

  onRegisterSendOtp(): void {
    if (this.registerForm.invalid) return;
    this.loading = true;
    this.errorMessage = '';

    const { email, mobile, unitId } = this.registerForm.value;

    this.http.post<any>(`${environment.apiUrl}/member/auth/register/send-otp`, { email, mobile, unitId }).subscribe({
      next: (res) => {
        this.loading = false;
        if (res.success) {
          this.step = 'register-otp';
          this.otpForm.reset();
          // Mask email for display
          const parts = email.split('@');
          this.regMaskedEmail = parts[0].substring(0, 2) + '****@' + parts[1];
          this.snackBar.open(res.message || 'OTP sent to email', 'Close', { duration: 3000 });
        }
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.error?.message || 'Failed to send OTP.';
      }
    });
  }

  onRegisterVerifyOtp(): void {
    if (this.otpForm.invalid) return;
    this.loading = true;
    this.errorMessage = '';

    const { email, mobile, unitId } = this.registerForm.value;
    const otp = this.otpForm.value.otp;

    this.http.post<any>(`${environment.apiUrl}/member/auth/register/verify-otp`, { email, mobile, otp, unitId }).subscribe({
      next: (res) => {
        this.loading = false;
        if (res.success) {
          this.step = 'register-done';
          this.snackBar.open(res.message, 'Close', { duration: 5000 });
        }
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.error?.message || 'OTP verification failed.';
      }
    });
  }

  // ==================== HELPERS ====================

  private startResendCooldown(): void {
    this.resendCooldown = 30;
    this.clearCooldown();
    this.cooldownInterval = setInterval(() => {
      this.resendCooldown--;
      if (this.resendCooldown <= 0) this.clearCooldown();
    }, 1000);
  }

  private clearCooldown(): void {
    if (this.cooldownInterval) {
      clearInterval(this.cooldownInterval);
      this.cooldownInterval = null;
    }
  }
}
