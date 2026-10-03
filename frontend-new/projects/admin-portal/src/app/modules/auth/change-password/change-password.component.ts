import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { AuthService } from '@core/services/auth.service';

@Component({
  selector: 'app-change-password',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterModule, MatCardModule,
    MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule,
    MatSnackBarModule, MatProgressBarModule
  ],
  template: `
    <div class="form-container">
      <mat-card>
        <mat-card-header>
          <mat-card-title>
            <mat-icon class="title-icon">lock</mat-icon>
            Change Password
          </mat-card-title>
          <mat-card-subtitle>Update your account password</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="passwordForm" (ngSubmit)="onSubmit()">
            <!-- Current Password -->
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Current Password</mat-label>
              <input matInput [type]="hideCurrent ? 'password' : 'text'" formControlName="currentPassword">
              <mat-icon matPrefix>lock</mat-icon>
              <button mat-icon-button matSuffix (click)="hideCurrent = !hideCurrent" type="button"
                      [attr.aria-label]="hideCurrent ? 'Show current password' : 'Hide current password'">
                <mat-icon>{{ hideCurrent ? 'visibility_off' : 'visibility' }}</mat-icon>
              </button>
              <mat-error *ngIf="passwordForm.get('currentPassword')?.hasError('required')">
                Current password is required
              </mat-error>
            </mat-form-field>

            <!-- New Password -->
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>New Password</mat-label>
              <input matInput [type]="hideNew ? 'password' : 'text'" formControlName="newPassword">
              <mat-icon matPrefix>lock_reset</mat-icon>
              <button mat-icon-button matSuffix (click)="hideNew = !hideNew" type="button"
                      [attr.aria-label]="hideNew ? 'Show new password' : 'Hide new password'">
                <mat-icon>{{ hideNew ? 'visibility_off' : 'visibility' }}</mat-icon>
              </button>
              <mat-error *ngIf="passwordForm.get('newPassword')?.hasError('required')">
                New password is required
              </mat-error>
              <mat-error *ngIf="passwordForm.get('newPassword')?.hasError('minlength')">
                Must be at least 6 characters
              </mat-error>
            </mat-form-field>

            <!-- Password Strength Indicator -->
            <div class="strength-section" *ngIf="passwordForm.get('newPassword')?.value">
              <div class="strength-bar-container">
                <mat-progress-bar mode="determinate" [value]="strengthPercent"
                                  [color]="strengthPercent >= 60 ? 'primary' : 'warn'">
                </mat-progress-bar>
              </div>
              <span class="strength-label" [style.color]="strengthColor">{{ strengthLabel }}</span>
              <div class="strength-hints" *ngIf="strengthPercent < 80">
                <small>Tips: use uppercase, numbers, and special characters (&#64;#$%)</small>
              </div>
            </div>

            <!-- Confirm Password -->
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Confirm New Password</mat-label>
              <input matInput [type]="hideConfirm ? 'password' : 'text'" formControlName="confirmPassword">
              <mat-icon matPrefix>lock_reset</mat-icon>
              <button mat-icon-button matSuffix (click)="hideConfirm = !hideConfirm" type="button"
                      [attr.aria-label]="hideConfirm ? 'Show confirm password' : 'Hide confirm password'">
                <mat-icon>{{ hideConfirm ? 'visibility_off' : 'visibility' }}</mat-icon>
              </button>
              <mat-error *ngIf="passwordForm.get('confirmPassword')?.hasError('required')">
                Please confirm your new password
              </mat-error>
            </mat-form-field>

            <!-- Mismatch Error -->
            <div *ngIf="passwordForm.errors?.['mismatch'] && passwordForm.get('confirmPassword')?.touched"
                 class="error-msg">
              <mat-icon class="error-icon">error</mat-icon>
              Passwords do not match
            </div>

            <!-- Match Success -->
            <div *ngIf="!passwordForm.errors?.['mismatch'] && passwordForm.get('confirmPassword')?.touched
                        && passwordForm.get('confirmPassword')?.value"
                 class="success-msg">
              <mat-icon class="success-icon">check_circle</mat-icon>
              Passwords match
            </div>

            <div class="action-buttons">
              <button mat-button type="button" routerLink="/dashboard">Cancel</button>
              <button mat-raised-button color="primary" type="submit"
                      [disabled]="passwordForm.invalid || submitting">
                <mat-icon>save</mat-icon>
                {{ submitting ? 'Changing...' : 'Change Password' }}
              </button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .form-container { max-width: 480px; margin: 32px auto; padding: 0 16px; }
    .full-width { width: 100%; margin-bottom: 4px; }
    .title-icon { vertical-align: middle; margin-right: 8px; }
    .error-msg { color: #c62828; font-size: 13px; margin-bottom: 12px; display: flex; align-items: center; gap: 6px; }
    .error-icon { font-size: 18px; height: 18px; width: 18px; }
    .success-msg { color: #2e7d32; font-size: 13px; margin-bottom: 12px; display: flex; align-items: center; gap: 6px; }
    .success-icon { font-size: 18px; height: 18px; width: 18px; }
    .strength-section { margin-bottom: 16px; }
    .strength-bar-container { margin-bottom: 4px; }
    .strength-label { font-size: 12px; font-weight: 500; }
    .strength-hints { margin-top: 4px; }
    .strength-hints small { color: #666; }
    .action-buttons { display: flex; justify-content: flex-end; gap: 8px; margin-top: 16px; }
  `]
})
export class ChangePasswordComponent {
  passwordForm: FormGroup;
  hideCurrent = true;
  hideNew = true;
  hideConfirm = true;
  submitting = false;

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router,
    private snackBar: MatSnackBar
  ) {
    this.passwordForm = this.fb.group({
      currentPassword: ['', Validators.required],
      newPassword: ['', [Validators.required, Validators.minLength(6)]],
      confirmPassword: ['', Validators.required]
    }, { validators: this.passwordMatchValidator });
  }

  passwordMatchValidator(group: FormGroup): { [key: string]: boolean } | null {
    const newPass = group.get('newPassword')?.value;
    const confirm = group.get('confirmPassword')?.value;
    return newPass === confirm ? null : { mismatch: true };
  }

  get strengthPercent(): number {
    return this.calculateStrength(this.passwordForm.get('newPassword')?.value || '').percent;
  }

  get strengthColor(): string {
    return this.calculateStrength(this.passwordForm.get('newPassword')?.value || '').color;
  }

  get strengthLabel(): string {
    return this.calculateStrength(this.passwordForm.get('newPassword')?.value || '').label;
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
    if (this.passwordForm.invalid) return;
    this.submitting = true;

    const { currentPassword, newPassword } = this.passwordForm.value;

    this.authService.changePassword({ currentPassword, newPassword }).subscribe({
      next: (res: any) => {
        this.submitting = false;
        if (res.success) {
          this.snackBar.open('Password changed successfully. Please login again.', 'Close', { duration: 3000 });
          this.authService.logout();
        }
      },
      error: (err: any) => {
        this.submitting = false;
        this.snackBar.open(err.error?.message || 'Failed to change password', 'Close', { duration: 5000 });
      }
    });
  }
}
