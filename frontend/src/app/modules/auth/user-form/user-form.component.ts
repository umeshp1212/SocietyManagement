import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { environment } from '@env/environment';

@Component({
  selector: 'app-user-form',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, MatButtonModule, MatCardModule,
    MatCheckboxModule, MatSnackBarModule
  ],
  template: `
    <div class="form-container">
      <mat-card>
        <mat-card-header>
          <mat-card-title>{{ isEdit ? 'Edit User' : 'Create New User' }}</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="userForm" (ngSubmit)="onSubmit()">
            <div class="form-row" *ngIf="!isEdit">
              <mat-form-field appearance="outline">
                <mat-label>Username *</mat-label>
                <input matInput formControlName="username">
                <mat-error *ngIf="userForm.get('username')?.hasError('required')">Username is required</mat-error>
                <mat-error *ngIf="userForm.get('username')?.hasError('minlength')">Username must be at least 4 characters</mat-error>
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Password *</mat-label>
                <input matInput type="password" formControlName="password">
                <mat-error *ngIf="userForm.get('password')?.hasError('required')">Password is required</mat-error>
                <mat-error *ngIf="userForm.get('password')?.hasError('minlength')">Password must be at least 6 characters</mat-error>
              </mat-form-field>
            </div>

            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Full Name *</mat-label>
                <input matInput formControlName="fullName">
                <mat-error *ngIf="userForm.get('fullName')?.hasError('required')">Full name is required</mat-error>
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Email</mat-label>
                <input matInput formControlName="email" type="email">
                <mat-error *ngIf="userForm.get('email')?.hasError('email')">Enter a valid email address</mat-error>
              </mat-form-field>
            </div>

            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Phone</mat-label>
                <input matInput formControlName="phone" maxlength="10" inputmode="numeric">
                <mat-error *ngIf="userForm.get('phone')?.hasError('pattern')">Enter a valid 10-digit mobile number starting with 6-9</mat-error>
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Roles *</mat-label>
                <mat-select formControlName="roles" multiple>
                  <mat-option *ngFor="let role of availableRoles" [value]="role.roleName">
                    {{ role.displayName }}
                  </mat-option>
                </mat-select>
                <mat-error *ngIf="userForm.get('roles')?.hasError('required')">At least one role is required</mat-error>
              </mat-form-field>
            </div>

            <div class="action-buttons">
              <button mat-button type="button" routerLink="/users">Cancel</button>
              <button mat-raised-button color="primary" type="submit"
                      [disabled]="userForm.invalid">
                {{ isEdit ? 'Update User' : 'Create User' }}
              </button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>
    </div>
  `
})
export class UserFormComponent implements OnInit {
  userForm!: FormGroup;
  isEdit = false;
  userId?: number;
  availableRoles: any[] = [];

  private apiUrl = environment.apiUrl;

  constructor(
    private fb: FormBuilder,
    private http: HttpClient,
    private route: ActivatedRoute,
    private router: Router,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.userForm = this.fb.group({
      username: ['', [Validators.required, Validators.minLength(4)]],
      password: ['', [Validators.required, Validators.minLength(6)]],
      fullName: ['', Validators.required],
      email: ['', Validators.email],
      phone: ['', [Validators.pattern(/^[6-9]\d{9}$/)]],
      roles: [[], Validators.required]
    });

    // Load available roles
    this.http.get<any>(`${this.apiUrl}/users/roles`).subscribe(res => {
      if (res.success) this.availableRoles = res.data;
    });

    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEdit = true;
      this.userId = +id;
      this.userForm.get('username')?.clearValidators();
      this.userForm.get('password')?.clearValidators();
      this.userForm.get('username')?.updateValueAndValidity();
      this.userForm.get('password')?.updateValueAndValidity();
      this.loadUser();
    }
  }

  loadUser(): void {
    this.http.get<any>(`${this.apiUrl}/users/${this.userId}`).subscribe(res => {
      if (res.success) {
        this.userForm.patchValue({
          fullName: res.data.fullName,
          email: res.data.email,
          phone: res.data.phone,
          roles: res.data.roles
        });
      }
    });
  }

  onSubmit(): void {
    if (this.userForm.invalid) return;

    const formValue = this.userForm.value;

    if (this.isEdit) {
      const updateReq = {
        fullName: formValue.fullName,
        email: formValue.email,
        phone: formValue.phone,
        roles: formValue.roles
      };
      this.http.put<any>(`${this.apiUrl}/users/${this.userId}`, updateReq).subscribe({
        next: (res) => {
          if (res.success) {
            this.snackBar.open('User updated', 'Close', { duration: 3000 });
            this.router.navigate(['/users']);
          }
        },
        error: (err) => this.snackBar.open(err.error?.message || 'Failed', 'Close', { duration: 5000 })
      });
    } else {
      this.http.post<any>(`${this.apiUrl}/users`, formValue).subscribe({
        next: (res) => {
          if (res.success) {
            this.snackBar.open('User created', 'Close', { duration: 3000 });
            this.router.navigate(['/users']);
          }
        },
        error: (err) => this.snackBar.open(err.error?.message || 'Failed', 'Close', { duration: 5000 })
      });
    }
  }
}
