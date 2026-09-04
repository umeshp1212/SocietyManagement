import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { OwnerService } from '@core/services/owner.service';

@Component({
  selector: 'app-owner-form',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterModule, MatFormFieldModule,
    MatInputModule, MatButtonModule, MatCardModule, MatSnackBarModule
  ],
  template: `
    <div class="form-container">
      <mat-card>
        <mat-card-header>
          <mat-card-title>{{ isEdit ? 'Update Owner' : 'Add New Owner' }}</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="ownerForm" (ngSubmit)="onSubmit()">
            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Full Name *</mat-label>
                <input matInput formControlName="fullName">
                <mat-error *ngIf="ownerForm.get('fullName')?.hasError('required')">Full name is required</mat-error>
                <mat-error *ngIf="ownerForm.get('fullName')?.hasError('maxlength')">Full name cannot exceed 150 characters</mat-error>
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Contact Number *</mat-label>
                <input matInput formControlName="contactNumber" maxlength="10" inputmode="numeric">
                <mat-error *ngIf="ownerForm.get('contactNumber')?.hasError('required')">Contact number is required</mat-error>
                <mat-error *ngIf="ownerForm.get('contactNumber')?.hasError('pattern')">Enter a valid 10-digit mobile number starting with 6-9</mat-error>
              </mat-form-field>
            </div>

            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Email</mat-label>
                <input matInput formControlName="email" type="email">
                <mat-error *ngIf="ownerForm.get('email')?.hasError('email')">Enter a valid email address</mat-error>
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Alternate Number</mat-label>
                <input matInput formControlName="alternateNumber" maxlength="10" inputmode="numeric">
                <mat-error *ngIf="ownerForm.get('alternateNumber')?.hasError('pattern')">Enter a valid 10-digit mobile number starting with 6-9</mat-error>
              </mat-form-field>
            </div>

            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Aadhar Number</mat-label>
                <input matInput formControlName="aadharNumber">
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>PAN Number</mat-label>
                <input matInput formControlName="panNumber">
              </mat-form-field>
            </div>

            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Occupation</mat-label>
                <input matInput formControlName="occupation">
              </mat-form-field>
            </div>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Permanent Address</mat-label>
              <textarea matInput formControlName="permanentAddress" rows="3"></textarea>
            </mat-form-field>

            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Emergency Contact Name</mat-label>
                <input matInput formControlName="emergencyContactName">
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Emergency Contact Phone</mat-label>
                <input matInput formControlName="emergencyContactPhone" maxlength="10" inputmode="numeric">
                <mat-error *ngIf="ownerForm.get('emergencyContactPhone')?.hasError('pattern')">Enter a valid 10-digit mobile number starting with 6-9</mat-error>
              </mat-form-field>
            </div>

            <div class="action-buttons">
              <button mat-button type="button" routerLink="/owners">Cancel</button>
              <button mat-raised-button color="primary" type="submit"
                      [disabled]="ownerForm.invalid">
                {{ isEdit ? 'Update' : 'Save' }}
              </button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`.full-width { width: 100%; }`]
})
export class OwnerFormComponent implements OnInit {
  ownerForm!: FormGroup;
  isEdit = false;
  ownerId?: number;

  constructor(
    private fb: FormBuilder,
    private ownerService: OwnerService,
    private route: ActivatedRoute,
    private router: Router,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    const mobilePattern = /^[6-9]\d{9}$/;
    this.ownerForm = this.fb.group({
      fullName: ['', [Validators.required, Validators.maxLength(150)]],
      contactNumber: ['', [Validators.required, Validators.pattern(mobilePattern)]],
      alternateNumber: ['', [Validators.pattern(mobilePattern)]],
      email: ['', [Validators.email]],
      aadharNumber: [''],
      panNumber: ['', [Validators.maxLength(20)]],
      permanentAddress: [''],
      occupation: [''],
      emergencyContactName: [''],
      emergencyContactPhone: ['', [Validators.pattern(mobilePattern)]]
    });

    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEdit = true;
      this.ownerId = +id;
      this.loadOwner();
    }
  }

  loadOwner(): void {
    this.ownerService.getOwnerById(this.ownerId!).subscribe(res => {
      if (res.success) {
        this.ownerForm.patchValue(res.data);
      }
    });
  }

  onSubmit(): void {
    if (this.ownerForm.invalid) return;

    const request = this.ownerForm.value;

    if (this.isEdit) {
      this.ownerService.updateOwner(this.ownerId!, request).subscribe(res => {
        if (res.success) {
          this.snackBar.open('Owner updated successfully', 'Close', { duration: 3000 });
          this.router.navigate(['/owners']);
        }
      });
    } else {
      this.ownerService.createOwner(request).subscribe(res => {
        if (res.success) {
          this.snackBar.open('Owner added successfully', 'Close', { duration: 3000 });
          this.router.navigate(['/owners']);
        }
      });
    }
  }
}
