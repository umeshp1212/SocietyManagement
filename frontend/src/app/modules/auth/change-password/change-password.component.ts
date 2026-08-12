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
import { AuthService } from '@core/services/auth.service';

@Component({
  selector: 'app-change-password',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterModule, MatCardModule,
    MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule, MatSnackBarModule
  ],
  template: `
    <div class="form-container">
      <mat-card>
        <mat-card-header>
          <mat-card-title>Change Password</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="passwordForm" (ngSubmit)="onSubmit()">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Current Password *</mat-label>
              <input matInput type="password" formControlName="currentPassword">
              <mat-icon matPrefix>lock</mat-icon>
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>New Password *</mat-label>
              <input matInput type="password" formControlName="newPassword">
              <mat-icon matPrefix>lock_reset</mat-icon>
              <mat-hint>Minimum 6 characters</mat-hint>
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Confirm New Password *</mat-label>
              <input matInput type="password" formControlName="confirmPassword">
              <mat-icon matPrefix>lock_reset</mat-icon>
            </mat-form-field>

            <div *ngIf="passwordForm.errors?.['mismatch']" class="error-msg">
              Passwords do not match
            </div>

            <div class="action-buttons">
              <button mat-button type="button" routerLink="/dashboard">Cancel</button>
              <button mat-raised-button color="primary" type="submit"
                      [disabled]="passwordForm.invalid">
                Change Password
              </button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .full-width { width: 100%; }
    .error-msg { color: #c62828; font-size: 13px; margin-bottom: 12px; }
  `]
})
export class ChangePasswordComponent {
  passwordForm: FormGroup;

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

  onSubmit(): void {
    if (this.passwordForm.invalid) return;

    const { currentPassword, newPassword } = this.passwordForm.value;

    this.authService.changePassword({ currentPassword, newPassword }).subscribe({
      next: (res: any) => {
        if (res.success) {
          this.snackBar.open('Password changed successfully. Please login again.', 'Close', { duration: 3000 });
          this.authService.logout();
        }
      },
      error: (err: any) => {
        this.snackBar.open(err.error?.message || 'Failed to change password', 'Close', { duration: 5000 });
      }
    });
  }
}
