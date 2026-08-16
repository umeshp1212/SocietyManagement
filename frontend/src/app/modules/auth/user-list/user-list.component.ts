import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { environment } from '@env/environment';

@Component({
  selector: 'app-user-list',
  standalone: true,
  imports: [
    CommonModule, RouterModule, FormsModule, ReactiveFormsModule, MatTableModule, MatPaginatorModule,
    MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule,
    MatChipsModule, MatTooltipModule, MatSnackBarModule, MatDialogModule
  ],
  template: `
    <div class="container">
      <div class="page-header">
        <h2>User Management</h2>
        <div style="display: flex; gap: 8px;">
          <a mat-raised-button routerLink="/users/roles-permissions">
            <mat-icon>security</mat-icon> Roles & Permissions
          </a>
          <a mat-raised-button color="primary" routerLink="/users/add">
            <mat-icon>person_add</mat-icon> Add User
          </a>
        </div>
      </div>

      <div class="search-bar">
        <mat-form-field appearance="outline">
          <mat-label>Search</mat-label>
          <input matInput [(ngModel)]="searchTerm" (keyup.enter)="loadUsers()"
                 placeholder="Name, Username, Email, Phone">
          <mat-icon matSuffix>search</mat-icon>
        </mat-form-field>
      </div>

      <table mat-table [dataSource]="users" class="mat-elevation-z2">
        <ng-container matColumnDef="username">
          <th mat-header-cell *matHeaderCellDef>Username</th>
          <td mat-cell *matCellDef="let u">{{ u.username }}</td>
        </ng-container>
        <ng-container matColumnDef="fullName">
          <th mat-header-cell *matHeaderCellDef>Full Name</th>
          <td mat-cell *matCellDef="let u">{{ u.fullName }}</td>
        </ng-container>
        <ng-container matColumnDef="email">
          <th mat-header-cell *matHeaderCellDef>Email</th>
          <td mat-cell *matCellDef="let u">{{ u.email || '-' }}</td>
        </ng-container>
        <ng-container matColumnDef="roles">
          <th mat-header-cell *matHeaderCellDef>Roles</th>
          <td mat-cell *matCellDef="let u">
            <mat-chip-set>
              <mat-chip *ngFor="let role of u.roles" class="role-chip">{{ role }}</mat-chip>
            </mat-chip-set>
          </td>
        </ng-container>
        <ng-container matColumnDef="isActive">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let u">
            <span class="status-badge" [ngClass]="u.isActive ? 'active' : 'inactive'">
              {{ u.isActive ? 'Active' : 'Inactive' }}
            </span>
          </td>
        </ng-container>
        <ng-container matColumnDef="lastLogin">
          <th mat-header-cell *matHeaderCellDef>Last Login</th>
          <td mat-cell *matCellDef="let u">{{ u.lastLogin | date:'dd-MM-yyyy HH:mm' }}</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef>Actions</th>
          <td mat-cell *matCellDef="let u">
            <a mat-icon-button [routerLink]="['/users/edit', u.userId]" matTooltip="Edit">
              <mat-icon>edit</mat-icon>
            </a>
            <button mat-icon-button (click)="openResetPasswordDialog(u)" matTooltip="Reset Password">
              <mat-icon>lock_reset</mat-icon>
            </button>
            <button mat-icon-button (click)="toggleStatus(u)" [color]="u.isActive ? 'warn' : 'primary'"
                    [matTooltip]="u.isActive ? 'Deactivate' : 'Activate'">
              <mat-icon>{{ u.isActive ? 'block' : 'check_circle' }}</mat-icon>
            </button>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
      </table>

      <mat-paginator [length]="totalElements" [pageSize]="pageSize"
        [pageSizeOptions]="[10, 20, 50]" (page)="onPageChange($event)">
      </mat-paginator>
    </div>
  `,
  styles: [`
    .role-chip { font-size: 11px !important; min-height: 24px !important; }
  `]
})
export class UserListComponent implements OnInit {
  users: any[] = [];
  displayedColumns = ['username', 'fullName', 'email', 'roles', 'isActive', 'lastLogin', 'actions'];
  totalElements = 0;
  pageSize = 20;
  currentPage = 0;
  searchTerm = '';

  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient, private snackBar: MatSnackBar, private dialog: MatDialog) {}

  ngOnInit(): void { this.loadUsers(); }

  loadUsers(): void {
    let params = new HttpParams()
      .set('page', this.currentPage)
      .set('size', this.pageSize);
    if (this.searchTerm) params = params.set('search', this.searchTerm);

    this.http.get<any>(`${this.apiUrl}/users`, { params }).subscribe(res => {
      if (res.success) {
        this.users = res.data.content;
        this.totalElements = res.data.totalElements;
      }
    });
  }

  toggleStatus(user: any): void {
    this.http.patch<any>(`${this.apiUrl}/users/${user.userId}/toggle-status`, {}).subscribe(res => {
      if (res.success) {
        this.snackBar.open(
          `User ${res.data.isActive ? 'activated' : 'deactivated'}`, 'Close', { duration: 3000 });
        this.loadUsers();
      }
    });
  }

  onPageChange(event: PageEvent): void {
    this.currentPage = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadUsers();
  }

  openResetPasswordDialog(user: any): void {
    const dialogRef = this.dialog.open(ResetPasswordDialogComponent, {
      width: '420px',
      data: { userId: user.userId, username: user.username, fullName: user.fullName }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result === 'success') {
        this.snackBar.open(`Password reset for ${user.fullName}`, 'Close', { duration: 3000 });
      }
    });
  }
}


// ===== Reset Password Dialog Component =====
import { Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

@Component({
  selector: 'app-reset-password-dialog',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatIconModule, MatDialogModule
  ],
  template: `
    <h2 mat-dialog-title>Reset Password</h2>
    <mat-dialog-content>
      <p class="dialog-subtitle">Reset password for <strong>{{ data.fullName }}</strong> ({{ data.username }})</p>

      <form [formGroup]="resetForm">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>New Password</mat-label>
          <input matInput [type]="hidePassword ? 'password' : 'text'" formControlName="newPassword">
          <mat-icon matPrefix>lock</mat-icon>
          <button mat-icon-button matSuffix (click)="hidePassword = !hidePassword" type="button">
            <mat-icon>{{ hidePassword ? 'visibility_off' : 'visibility' }}</mat-icon>
          </button>
          <mat-hint>Minimum 6 characters</mat-hint>
          <mat-error *ngIf="resetForm.get('newPassword')?.hasError('required')">Password is required</mat-error>
          <mat-error *ngIf="resetForm.get('newPassword')?.hasError('minlength')">Minimum 6 characters</mat-error>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Confirm Password</mat-label>
          <input matInput [type]="hideConfirm ? 'password' : 'text'" formControlName="confirmPassword">
          <mat-icon matPrefix>lock_reset</mat-icon>
          <button mat-icon-button matSuffix (click)="hideConfirm = !hideConfirm" type="button">
            <mat-icon>{{ hideConfirm ? 'visibility_off' : 'visibility' }}</mat-icon>
          </button>
          <mat-error *ngIf="resetForm.get('confirmPassword')?.hasError('required')">Confirm password is required</mat-error>
        </mat-form-field>

        <div *ngIf="resetForm.errors?.['mismatch'] && resetForm.get('confirmPassword')?.touched" class="error-msg">
          Passwords do not match
        </div>

        <!-- Password Strength Indicator -->
        <div class="password-strength" *ngIf="resetForm.get('newPassword')?.value">
          <div class="strength-bar">
            <div class="strength-fill" [style.width]="strengthPercent + '%'"
                 [style.background-color]="strengthColor"></div>
          </div>
          <span class="strength-label" [style.color]="strengthColor">{{ strengthLabel }}</span>
        </div>

        <button mat-button type="button" color="accent" (click)="generatePassword()" class="generate-btn">
          <mat-icon>autorenew</mat-icon> Generate Strong Password
        </button>
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-raised-button color="warn" (click)="onReset()"
              [disabled]="resetForm.invalid || submitting">
        {{ submitting ? 'Resetting...' : 'Reset Password' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .full-width { width: 100%; margin-bottom: 8px; }
    .dialog-subtitle { color: #666; margin-bottom: 16px; }
    .error-msg { color: #c62828; font-size: 13px; margin-bottom: 12px; }
    .password-strength { margin-bottom: 16px; }
    .strength-bar { height: 4px; background: #e0e0e0; border-radius: 2px; overflow: hidden; }
    .strength-fill { height: 100%; transition: width 0.3s, background-color 0.3s; border-radius: 2px; }
    .strength-label { font-size: 12px; margin-top: 4px; display: block; }
    .generate-btn { margin-bottom: 8px; }
  `]
})
export class ResetPasswordDialogComponent {
  resetForm: FormGroup;
  hidePassword = true;
  hideConfirm = true;
  submitting = false;

  constructor(
    private fb: FormBuilder,
    private http: HttpClient,
    private snackBar: MatSnackBar,
    private dialogRef: MatDialogRef<ResetPasswordDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { userId: number; username: string; fullName: string }
  ) {
    this.resetForm = this.fb.group({
      newPassword: ['', [Validators.required, Validators.minLength(6)]],
      confirmPassword: ['', Validators.required]
    }, { validators: this.passwordMatchValidator });
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

  generatePassword(): void {
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@#$%&*!';
    let password = '';
    // Ensure at least one of each type
    password += 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'[Math.floor(Math.random() * 26)];
    password += 'abcdefghijklmnopqrstuvwxyz'[Math.floor(Math.random() * 26)];
    password += '0123456789'[Math.floor(Math.random() * 10)];
    password += '@#$%&*!'[Math.floor(Math.random() * 7)];
    for (let i = 4; i < 12; i++) {
      password += chars[Math.floor(Math.random() * chars.length)];
    }
    // Shuffle
    password = password.split('').sort(() => Math.random() - 0.5).join('');
    this.resetForm.patchValue({ newPassword: password, confirmPassword: password });
    this.hidePassword = false;
    this.hideConfirm = false;
  }

  onReset(): void {
    if (this.resetForm.invalid) return;
    this.submitting = true;

    const { newPassword } = this.resetForm.value;
    this.http.post<any>(`${environment.apiUrl}/users/${this.data.userId}/reset-password`, {
      newPassword
    }).subscribe({
      next: (res) => {
        this.submitting = false;
        if (res.success) {
          this.dialogRef.close('success');
        }
      },
      error: (err) => {
        this.submitting = false;
        this.snackBar.open(err.error?.message || 'Failed to reset password', 'Close', { duration: 5000 });
      }
    });
  }
}
