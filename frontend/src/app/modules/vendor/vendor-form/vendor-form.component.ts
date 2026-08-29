import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { VendorService } from '@core/services/vendor.service';
import { VendorCategoryService } from '@core/services/vendor-category.service';

@Component({
  selector: 'app-vendor-form',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, MatButtonModule, MatCardModule,
    MatDatepickerModule, MatNativeDateModule, MatIconModule, MatSnackBarModule
  ],
  template: `
    <div class="form-container">
      <mat-card>
        <mat-card-header>
          <mat-card-title>{{ isEdit ? 'Update Vendor' : 'Add New Vendor' }}</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="vendorForm" (ngSubmit)="onSubmit()">

            <!-- Basic Information -->
            <h4>Basic Information</h4>
            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Vendor Name *</mat-label>
                <input matInput formControlName="vendorName">
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Category *</mat-label>
                <mat-select formControlName="categoryId">
                  <mat-option *ngFor="let cat of categories" [value]="cat.value">
                    {{ cat.label }}
                  </mat-option>
                </mat-select>
              </mat-form-field>
            </div>

            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Contact Person</mat-label>
                <input matInput formControlName="contactPerson">
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Phone *</mat-label>
                <input matInput formControlName="phone">
              </mat-form-field>
            </div>

            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Email</mat-label>
                <input matInput formControlName="email" type="email">
              </mat-form-field>
            </div>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Address</mat-label>
              <textarea matInput formControlName="address" rows="2"></textarea>
            </mat-form-field>

            <!-- Tax & Bank Details -->
            <h4>Tax & Bank Details</h4>
            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>PAN Number</mat-label>
                <input matInput formControlName="panNumber">
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>GST Number</mat-label>
                <input matInput formControlName="gstNumber">
              </mat-form-field>
            </div>

            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Bank Account Number</mat-label>
                <input matInput formControlName="bankAccountNumber">
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Bank IFSC</mat-label>
                <input matInput formControlName="bankIfsc">
              </mat-form-field>
            </div>

            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Bank Name</mat-label>
                <input matInput formControlName="bankName">
              </mat-form-field>
            </div>

            <!-- Contract Details -->
            <h4>Contract / Agreement Details</h4>
            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Agreement Start Date</mat-label>
                <input matInput [matDatepicker]="startPicker" formControlName="agreementStartDate">
                <mat-datepicker-toggle matSuffix [for]="startPicker"></mat-datepicker-toggle>
                <mat-datepicker #startPicker></mat-datepicker>
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Agreement End Date</mat-label>
                <input matInput [matDatepicker]="endPicker" formControlName="agreementEndDate">
                <mat-datepicker-toggle matSuffix [for]="endPicker"></mat-datepicker-toggle>
                <mat-datepicker #endPicker></mat-datepicker>
              </mat-form-field>
            </div>

            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Contracted Amount (Rs)</mat-label>
                <input matInput type="number" formControlName="contractedAmount">
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Payment Frequency</mat-label>
                <mat-select formControlName="paymentFrequency">
                  <mat-option value="MONTHLY">Monthly</mat-option>
                  <mat-option value="QUARTERLY">Quarterly</mat-option>
                  <mat-option value="HALF_YEARLY">Half Yearly</mat-option>
                  <mat-option value="ANNUAL">Annual</mat-option>
                  <mat-option value="ONE_TIME">One Time</mat-option>
                </mat-select>
              </mat-form-field>
            </div>

            <!-- Status (only in edit mode) -->
            <div class="form-row" *ngIf="isEdit">
              <mat-form-field appearance="outline">
                <mat-label>Status *</mat-label>
                <mat-select formControlName="status">
                  <mat-option value="ACTIVE">Active</mat-option>
                  <mat-option value="INACTIVE">Inactive</mat-option>
                  <mat-option value="BLACKLISTED">Blacklisted</mat-option>
                </mat-select>
              </mat-form-field>
            </div>

            <div class="action-buttons">
              <button mat-button type="button" routerLink="/vendors">Cancel</button>
              <button mat-raised-button color="primary" type="submit"
                      [disabled]="vendorForm.invalid">
                {{ isEdit ? 'Update Vendor' : 'Add Vendor' }}
              </button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .full-width { width: 100%; }
    h4 { color: #1976d2; margin: 20px 0 10px; border-bottom: 1px solid #e0e0e0; padding-bottom: 5px; }
  `]
})
export class VendorFormComponent implements OnInit {
  vendorForm!: FormGroup;
  isEdit = false;
  vendorId?: number;

  categories: { value: number; label: string }[] = [];

  constructor(
    private fb: FormBuilder,
    private vendorService: VendorService,
    private vendorCategoryService: VendorCategoryService,
    private route: ActivatedRoute,
    private router: Router,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.vendorForm = this.fb.group({
      vendorName: ['', [Validators.required, Validators.maxLength(200)]],
      categoryId: [null, Validators.required],
      contactPerson: [''],
      phone: ['', [Validators.required, Validators.maxLength(15)]],
      email: ['', Validators.email],
      address: [''],
      panNumber: [''],
      gstNumber: [''],
      bankAccountNumber: [''],
      bankIfsc: [''],
      bankName: [''],
      agreementStartDate: [null],
      agreementEndDate: [null],
      contractedAmount: [null],
      paymentFrequency: ['MONTHLY'],
      status: ['ACTIVE']
    });

    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEdit = true;
      this.vendorId = +id;
      this.loadVendor();
    }

    // Load vendor categories from API
    this.vendorCategoryService.getActiveCategories().subscribe(res => {
      if (res.success) {
        this.categories = res.data.map(c => ({ value: c.categoryId, label: c.name }));
      }
    });
  }

  loadVendor(): void {
    this.vendorService.getVendorById(this.vendorId!).subscribe(res => {
      if (res.success) {
        const v = res.data;
        this.vendorForm.patchValue({
          ...v,
          agreementStartDate: v.agreementStartDate ? new Date(v.agreementStartDate) : null,
          agreementEndDate: v.agreementEndDate ? new Date(v.agreementEndDate) : null
        });
      }
    });
  }

  onSubmit(): void {
    if (this.vendorForm.invalid) return;

    const formValue = this.vendorForm.value;
    const request = {
      ...formValue,
      agreementStartDate: formValue.agreementStartDate ? this.formatDate(formValue.agreementStartDate) : null,
      agreementEndDate: formValue.agreementEndDate ? this.formatDate(formValue.agreementEndDate) : null
    };

    if (this.isEdit) {
      this.vendorService.updateVendor(this.vendorId!, request).subscribe({
        next: (res) => {
          if (res.success) {
            this.snackBar.open('Vendor updated successfully', 'Close', { duration: 3000 });
            this.router.navigate(['/vendors']);
          }
        },
        error: (err) => this.snackBar.open(err.error?.message || 'Update failed', 'Close', { duration: 5000 })
      });
    } else {
      this.vendorService.createVendor(request).subscribe({
        next: (res) => {
          if (res.success) {
            this.snackBar.open('Vendor added successfully', 'Close', { duration: 3000 });
            this.router.navigate(['/vendors']);
          }
        },
        error: (err) => this.snackBar.open(err.error?.message || 'Creation failed', 'Close', { duration: 5000 })
      });
    }
  }

  private formatDate(date: Date): string {
    if (!date) return '';
    const d = new Date(date);
    return d.toISOString().split('T')[0];
  }
}
