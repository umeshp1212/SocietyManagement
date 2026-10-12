import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { AuthService } from '@core/services/auth.service';

/** Username/password login for a security guard. */
@Component({
  selector: 'gate-login',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    MatCardModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatProgressSpinnerModule, MatSnackBarModule
  ],
  template: `
    <div class="login-page">
      <mat-card class="login-card">
        <div class="brand">
          <mat-icon>security</mat-icon>
          <h1>Gate Security</h1>
          <p>Sign in to register visitors &amp; deliveries</p>
        </div>
        <form (ngSubmit)="login()" #f="ngForm">
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Username</mat-label>
            <input matInput name="username" [(ngModel)]="username" required autocomplete="username">
            <mat-icon matPrefix>person</mat-icon>
          </mat-form-field>

          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Password</mat-label>
            <input matInput name="password" type="password" [(ngModel)]="password" required
                   autocomplete="current-password">
            <mat-icon matPrefix>lock</mat-icon>
          </mat-form-field>

          <button mat-raised-button color="primary" type="submit" class="full-width"
                  [disabled]="loading || !username || !password">
            <mat-icon *ngIf="!loading">login</mat-icon>
            <mat-spinner *ngIf="loading" diameter="20"></mat-spinner>
            <span>{{ loading ? 'Signing in...' : 'Sign In' }}</span>
          </button>
        </form>
      </mat-card>
    </div>
  `,
  styles: [`
    .login-page { min-height: 100vh; display: flex; align-items: center; justify-content: center;
      padding: 16px; background: linear-gradient(135deg, #37474f, #546e7a); }
    .login-card { width: 100%; max-width: 380px; padding: 24px; border-radius: 14px; }
    .brand { text-align: center; margin-bottom: 16px; }
    .brand mat-icon { font-size: 48px; height: 48px; width: 48px; color: #37474f; }
    .brand h1 { margin: 8px 0 2px; font-size: 22px; }
    .brand p { margin: 0; color: #78909c; font-size: 13px; }
    .full-width { width: 100%; }
    button.full-width { display: flex; align-items: center; justify-content: center; gap: 8px; height: 44px; }
  `]
})
export class GateLoginComponent {
  username = '';
  password = '';
  loading = false;

  constructor(
    private authService: AuthService,
    private router: Router,
    private snackBar: MatSnackBar
  ) {}

  login(): void {
    if (!this.username || !this.password) {
      return;
    }
    this.loading = true;
    this.authService.login(this.username.trim(), this.password).subscribe({
      next: (res: any) => {
        this.loading = false;
        if (res.success && this.authService.isGuardLoggedIn()) {
          this.router.navigate(['/gate']);
        } else {
          this.snackBar.open('This account is not authorised for the gate (needs the Security role).',
            'Close', { duration: 5000 });
        }
      },
      error: (err: any) => {
        this.loading = false;
        this.snackBar.open(err.error?.message || 'Login failed. Check your credentials.',
          'Close', { duration: 4000 });
      }
    });
  }
}
