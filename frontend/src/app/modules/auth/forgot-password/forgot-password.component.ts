import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { environment } from '@env/environment';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterModule, MatCardModule,
    MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule,
    MatProgressSpinnerModule, MatSnackBarModule
  ],
  template: `
    <div class="auth-container">
      <mat-card class="auth-card">
        <mat-card-header>
          <mat-card-title>
            <mat-icon class="title-icon">lock_open</mat-icon>
            Forgot Password
          </mat-card-title>
          <mat-card-subtitle>Enter your registered email to receive a reset link</mat-card-subtitle>
        </mat-card-header>

        <mat-card-content>
          <!-- Request Form -->
          <form *ngIf="!emailSent" [formGroup]="forgotForm" (ngSubmit)="onSubmit()">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Email Address</mat-label>
              <input matInput type="email" formControlName="email" placeholder="Enter your registered email">
              <mat-icon matPrefix>email</mat-icon>
              <mat-error *ngIf="forgotForm.get('email')?.hasError('required')">Email is required</mat-error>
              <mat-error *ngIf="forgotForm.get('email')?.hasError('email')">Enter a valid email</mat-error>
            </mat-form-field>

            <button mat-raised-button color="primary" type="submit" class="full-width submit-btn"
                    [disabled]="forgotForm.invalid || submitting">
              <mat-spinner *ngIf="submitting" diameter="20" class="btn-spinner"></mat-spinner>
              <span *ngIf="!submitting">Send Reset Link</span>
              <span *ngIf="submitting">Sending...</span>
            </button>
          </form>

          <!-- Success State -->
          <div *ngIf="emailSent" class="success-state">
            <mat-icon class="success-icon">mark_email_read</mat-icon>
            <h3>Check Your Email</h3>
            <p>If an account with <strong>{{ forgotForm.get('email')?.value }}</strong> exists, we've sent a password reset link.</p>
            <p class="hint">The link will expire in 30 minutes. Check your spam folder if you don't see it.</p>

            <button mat-button color="primary" (click)="emailSent = false" class="resend-btn">
              <mat-icon>refresh</mat-icon> Send Again
            </button>
          </div>

          <div class="back-link">
            <a mat-button routerLink="/login">
              <mat-icon>arrow_back</mat-icon> Back to Login
            </a>
          </div>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .auth-container { display: flex; justify-content: center; align-items: center; min-height: 80vh; padding: 16px; }
    .auth-card { max-width: 420px; width: 100%; }
    .title-icon { vertical-align: middle; margin-right: 8px; }
    .full-width { width: 100%; }
    .submit-btn { height: 48px; font-size: 16px; margin-top: 8px; }
    .btn-spinner { display: inline-block; margin-right: 8px; }
    .success-state { text-align: center; padding: 24px 0; }
    .success-icon { font-size: 64px; height: 64px; width: 64px; color: #2e7d32; }
    .success-state h3 { margin: 16px 0 8px; }
    .success-state p { color: #666; }
    .hint { font-size: 13px; color: #999 !important; margin-top: 8px; }
    .resend-btn { margin-top: 16px; }
    .back-link { text-align: center; margin-top: 16px; padding-top: 16px; border-top: 1px solid #eee; }
  `]
})
export class ForgotPasswordComponent {
  forgotForm: FormGroup;
  submitting = false;
  emailSent = false;

  constructor(
    private fb: FormBuilder,
    private http: HttpClient,
    private snackBar: MatSnackBar
  ) {
    this.forgotForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]]
    });
  }

  onSubmit(): void {
    if (this.forgotForm.invalid) return;
    this.submitting = true;

    const email = this.forgotForm.get('email')?.value;

    this.http.post<any>(`${environment.apiUrl}/auth/forgot-password`, { email }).subscribe({
      next: () => {
        this.submitting = false;
        this.emailSent = true;
      },
      error: (err) => {
        this.submitting = false;
        // Still show success to prevent email enumeration
        this.emailSent = true;
      }
    });
  }
}
