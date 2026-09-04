import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDividerModule } from '@angular/material/divider';
import { OwnerService } from '@core/services/owner.service';

@Component({
  selector: 'app-unit-form',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, MatButtonModule, MatCardModule,
    MatSnackBarModule, MatDividerModule
  ],
  template: `
    <div class="form-container">
      <mat-card>
        <mat-card-header>
          <mat-card-title>{{ isEdit ? 'Update Unit' : 'Add New Unit' }}</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="unitForm" (ngSubmit)="onSubmit()">
            <!-- Basic Info -->
            <h3 class="section-title">Basic Information</h3>
            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Unit Number *</mat-label>
                <input matInput formControlName="unitNumber" placeholder="e.g., A-101, S-01">
                <mat-error *ngIf="unitForm.get('unitNumber')?.hasError('required')">Unit number is required</mat-error>
                <mat-error *ngIf="unitForm.get('unitNumber')?.hasError('maxlength')">Unit number cannot exceed 20 characters</mat-error>
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Unit Type *</mat-label>
                <mat-select formControlName="unitType">
                  <mat-option value="FLAT">Flat</mat-option>
                  <mat-option value="SHOP">Shop</mat-option>
                </mat-select>
                <mat-error *ngIf="unitForm.get('unitType')?.hasError('required')">Unit type is required</mat-error>
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>BHK Type</mat-label>
                <mat-select formControlName="bhkType">
                  <mat-option [value]="null">-- Select --</mat-option>
                  <mat-option value="RK_1">1 RK</mat-option>
                  <mat-option value="BHK_1">1 BHK</mat-option>
                  <mat-option value="BHK_2">2 BHK</mat-option>
                  <mat-option value="BHK_3">3 BHK</mat-option>
                  <mat-option value="BHK_4">4 BHK</mat-option>
                  <mat-option value="SHOP">Shop</mat-option>
                  <mat-option value="OTHER">Other</mat-option>
                </mat-select>
              </mat-form-field>
            </div>
            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Wing</mat-label>
                <input matInput formControlName="wing" placeholder="A, B, C...">
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Floor</mat-label>
                <input matInput formControlName="floor">
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Area (sq ft)</mat-label>
                <input matInput type="number" formControlName="areaSqft">
              </mat-form-field>
            </div>

            <mat-divider></mat-divider>

            <!-- Charges Configuration -->
            <h3 class="section-title">Charges Configuration</h3>
            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Monthly Maintenance (Rs) *</mat-label>
                <input matInput type="number" formControlName="monthlyMaintenanceAmount">
                <mat-error *ngIf="unitForm.get('monthlyMaintenanceAmount')?.hasError('required')">Monthly maintenance amount is required</mat-error>
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Water Charges (Rs)</mat-label>
                <input matInput type="number" formControlName="waterCharges"
                       placeholder="Based on tank config">
                <mat-hint>1RK=550, 1BHK/2BHK=850, 3BHK=1150</mat-hint>
              </mat-form-field>
            </div>
            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Parking Type</mat-label>
                <mat-select formControlName="parkingType">
                  <mat-option value="NONE">No Parking</mat-option>
                  <mat-option value="TWO_WHEELER">Two Wheeler</mat-option>
                  <mat-option value="FOUR_WHEELER">Four Wheeler</mat-option>
                  <mat-option value="BOTH">Both (Two + Four Wheeler)</mat-option>
                </mat-select>
              </mat-form-field>
              <mat-form-field appearance="outline" *ngIf="isEdit">
                <mat-label>Occupancy Status</mat-label>
                <mat-select formControlName="occupancyStatus">
                  <mat-option value="VACANT">Vacant</mat-option>
                  <mat-option value="SELF_OCCUPIED">Self Occupied</mat-option>
                  <mat-option value="RENTED">Rented</mat-option>
                </mat-select>
                <mat-hint>Rented units are charged the non-occupancy charge</mat-hint>
              </mat-form-field>
            </div>

            <!-- BHK Water Info -->
            <div class="info-box" *ngIf="unitForm.get('bhkType')?.value">
              <strong>Water Charge (₹300/tank + ₹500 fixed):</strong>
              <span *ngIf="unitForm.get('bhkType')?.value === 'RK_1'">
                2 tanks → (2 × ₹300) + ₹500 = ₹1,100
              </span>
              <span *ngIf="unitForm.get('bhkType')?.value === 'BHK_1'">
                3 tanks → (3 × ₹300) + ₹500 = ₹1,400
              </span>
              <span *ngIf="unitForm.get('bhkType')?.value === 'BHK_2'">
                3 tanks → (3 × ₹300) + ₹500 = ₹1,400
              </span>
              <span *ngIf="unitForm.get('bhkType')?.value === 'BHK_3'">
                4 tanks → (4 × ₹300) + ₹500 = ₹1,700
              </span>
              <span *ngIf="unitForm.get('bhkType')?.value === 'BHK_4'">
                5 tanks → (5 × ₹300) + ₹500 = ₹2,000
              </span>
            </div>

            <div class="action-buttons">
              <button mat-button type="button" routerLink="/units">Cancel</button>
              <button mat-raised-button color="primary" type="submit" [disabled]="unitForm.invalid">
                {{ isEdit ? 'Update' : 'Save' }}
              </button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .form-container { padding: 24px; max-width: 900px; margin: 0 auto; }
    .form-row { display: flex; gap: 16px; flex-wrap: wrap; }
    .form-row mat-form-field { flex: 1; min-width: 200px; }
    .section-title { margin: 16px 0 8px; color: #333; font-size: 15px; }
    .action-buttons { display: flex; justify-content: flex-end; gap: 12px; margin-top: 16px; }
    mat-divider { margin: 16px 0; }
    .info-box {
      background: #e3f2fd;
      padding: 10px 14px;
      border-radius: 6px;
      font-size: 13px;
      margin: 8px 0 16px;
      color: #1565c0;
    }
  `]
})
export class UnitFormComponent implements OnInit {
  unitForm!: FormGroup;
  isEdit = false;
  unitId?: number;

  constructor(
    private fb: FormBuilder,
    private ownerService: OwnerService,
    private route: ActivatedRoute,
    private router: Router,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.unitForm = this.fb.group({
      unitNumber: ['', [Validators.required, Validators.maxLength(20)]],
      wing: [''],
      floor: [''],
      unitType: ['FLAT', Validators.required],
      bhkType: [null],
      areaSqft: [null],
      monthlyMaintenanceAmount: [null, Validators.required],
      waterCharges: [null],
      parkingType: ['NONE'],
      occupancyStatus: ['VACANT']
    });

    // Auto-suggest water charges when BHK type changes
    // Formula: (ratePerTank × tanks) + fixedChargePerUnit
    // Default: ₹300/tank + ₹500 fixed. Tanks: 1RK=2, 1BHK=3, 2BHK=3, 3BHK=4, 4BHK=5
    this.unitForm.get('bhkType')?.valueChanges.subscribe(bhk => {
      const tankMap: Record<string, number> = {
        'RK_1': 2,
        'BHK_1': 3,
        'BHK_2': 3,
        'BHK_3': 4,
        'BHK_4': 5
      };
      const ratePerTank = 300;
      const fixedCharge = 500;
      if (bhk && tankMap[bhk]) {
        const waterCharge = (ratePerTank * tankMap[bhk]) + fixedCharge;
        this.unitForm.patchValue({ waterCharges: waterCharge });
      }
    });

    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEdit = true;
      this.unitId = +id;
      this.ownerService.getUnitById(this.unitId).subscribe(res => {
        if (res.success) this.unitForm.patchValue(res.data);
      });
    }
  }

  onSubmit(): void {
    if (this.unitForm.invalid) return;
    const request = this.unitForm.value;

    if (this.isEdit) {
      this.ownerService.updateUnit(this.unitId!, request).subscribe(res => {
        if (res.success) {
          this.snackBar.open('Unit updated', 'Close', { duration: 3000 });
          this.router.navigate(['/units']);
        }
      });
    } else {
      this.ownerService.createUnit(request).subscribe(res => {
        if (res.success) {
          this.snackBar.open('Unit created', 'Close', { duration: 3000 });
          this.router.navigate(['/units']);
        }
      });
    }
  }
}
