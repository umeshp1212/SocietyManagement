import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { AuthService } from '@core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatCardModule, MatFormFieldModule,
    MatInputModule, MatButtonModule, MatIconModule, MatSnackBarModule
  ],
  template: `
    <div class="login-container">
      <mat-card class="login-card">
        <div class="login-header">
          <mat-icon class="logo-icon">apartment</mat-icon>
          <h2>Society Management</h2>
          <p>Sign in to your account</p>
        </div>

        <mat-card-content>
          <form [formGroup]="loginForm" (ngSubmit)="onLogin()">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Username</mat-label>
              <input matInput formControlName="username" placeholder="Enter username">
              <mat-icon matPrefix>person</mat-icon>
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Password</mat-label>
              <input matInput [type]="hidePassword ? 'password' : 'text'"
                     formControlName="password" placeholder="Enter password">
              <mat-icon matPrefix>lock</mat-icon>
              <button mat-icon-button matSuffix type="button"
                      (click)="hidePassword = !hidePassword">
                <mat-icon>{{ hidePassword ? 'visibility_off' : 'visibility' }}</mat-icon>
              </button>
            </mat-form-field>

            <div class="error-message" *ngIf="errorMessage">
              <mat-icon>error</mat-icon> {{ errorMessage }}
            </div>

            <button mat-raised-button color="primary" type="submit"
                    class="full-width login-btn"
                    [disabled]="loginForm.invalid || loading">
              <mat-icon *ngIf="!loading">login</mat-icon>
              <span *ngIf="loading">Signing in...</span>
              <span *ngIf="!loading">Sign In</span>
            </button>
          </form>
        </mat-card-content>

        <div class="login-footer">
          <p>Default: <strong>admin</strong> / <strong>Admin&#64;123</strong></p>
        </div>
      </mat-card>
    </div>
  `,
  styles: [`
    .login-container {
      display: flex; justify-content: center; align-items: center;
      min-height: 100vh; background: linear-gradient(135deg, #1976d2, #1565c0);
    }
    .login-card { width: 400px; padding: 32px; border-radius: 12px; }
    .login-header { text-align: center; margin-bottom: 24px; }
    .login-header h2 { margin: 8px 0 4px; color: #1976d2; }
    .login-header p { color: #666; margin: 0; }
    .logo-icon { font-size: 48px; height: 48px; width: 48px; color: #1976d2; }
    .full-width { width: 100%; }
    .login-btn { height: 48px; font-size: 16px; margin-top: 8px; }
    .error-message { display: flex; align-items: center; gap: 6px; color: #c62828;
                     font-size: 13px; margin-bottom: 12px; }
    .error-message mat-icon { font-size: 18px; height: 18px; width: 18px; }
    .login-footer { text-align: center; margin-top: 16px; font-size: 12px; color: #999; }
    .login-footer strong { color: #1976d2; }
  `]
})
export class LoginComponent {
  loginForm: FormGroup;
  hidePassword = true;
  loading = false;
  errorMessage = '';

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router,
    private snackBar: MatSnackBar
  ) {
    // Redirect if already logged in
    if (this.authService.isLoggedIn()) {
      this.router.navigate(['/dashboard']);
    }

    this.loginForm = this.fb.group({
      username: ['', Validators.required],
      password: ['', Validators.required]
    });
  }

  onLogin(): void {
    if (this.loginForm.invalid) return;

    this.loading = true;
    this.errorMessage = '';

    this.authService.login(this.loginForm.value).subscribe({
      next: (res) => {
        this.loading = false;
        if (res.success) {
          this.snackBar.open('Welcome, ' + res.data.fullName, 'Close', { duration: 3000 });
          this.router.navigate(['/dashboard']);
        }
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.error?.message || 'Login failed. Please try again.';
      }
    });
  }
}
