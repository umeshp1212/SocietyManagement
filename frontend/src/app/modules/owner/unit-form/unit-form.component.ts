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
import { MaintenanceService } from '@core/services/maintenance.service';
import { BackButtonComponent } from '@shared/components/back-button';

@Component({
  selector: 'app-unit-form',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, MatButtonModule, MatCardModule,
    MatSnackBarModule, MatDividerModule, BackButtonComponent
  ],
  template: `
    <div class="form-container">
      <app-back-button link="/units" label="Back to Units"></app-back-button>
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
                       [readonly]="hasActiveWaterConfig"
                       placeholder="Manual amount (used only if no water config)">
                <mat-hint *ngIf="waterConfigLoaded && hasActiveWaterConfig">
                  Auto-calculated from Water Charge Configuration. Not editable while a config is active.
                </mat-hint>
                <mat-hint *ngIf="waterConfigLoaded && !hasActiveWaterConfig">
                  No active Water Charge Configuration. Enter a manual amount for this unit.
                </mat-hint>
              </mat-form-field>
            </div>
            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Two Wheeler Slots</mat-label>
                <input matInput type="number" formControlName="twoWheelerCount" min="0" step="1">
                <mat-hint>No. of bike parkings. Billed as (two-wheeler rate x count)</mat-hint>
                <mat-error *ngIf="unitForm.get('twoWheelerCount')?.hasError('min')">Cannot be negative</mat-error>
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Four Wheeler Slots</mat-label>
                <input matInput type="number" formControlName="fourWheelerCount" min="0" step="1">
                <mat-hint>No. of car parkings. Billed as (four-wheeler rate x count)</mat-hint>
                <mat-error *ngIf="unitForm.get('fourWheelerCount')?.hasError('min')">Cannot be negative</mat-error>
              </mat-form-field>
            </div>
            <div class="form-row" *ngIf="isEdit">
              <mat-form-field appearance="outline">
                <mat-label>Occupancy Status</mat-label>
                <mat-select formControlName="occupancyStatus">
                  <mat-option value="VACANT">Vacant</mat-option>
                  <mat-option value="SELF_OCCUPIED">Self Occupied</mat-option>
                  <mat-option value="RENTED">Rented</mat-option>
                </mat-select>
                <mat-hint>Rented units are charged the non-occupancy charge</mat-hint>
              </mat-form-field>
            </div>

            <!-- Live Water Charge Info (from active Water Charge Configuration) -->
            <div class="info-box" *ngIf="waterInfo">
              {{ waterInfo }}
            </div>
            <div class="info-box warn" *ngIf="waterConfigLoaded && !hasActiveWaterConfig">
              No active Water Charge Configuration found. Bills will use the manual Water Charges
              amount entered above. Configure it under Maintenance &rsaquo; Charges &rsaquo; Water Charges.
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
    .info-box.warn { background: #fff3e0; color: #e65100; }
  `]
})
export class UnitFormComponent implements OnInit {
  unitForm!: FormGroup;
  isEdit = false;
  unitId?: number;

  waterConfig: any = null;
  waterConfigLoaded = false;
  hasActiveWaterConfig = false;
  waterInfo = '';

  constructor(
    private fb: FormBuilder,
    private ownerService: OwnerService,
    private maintenanceService: MaintenanceService,
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
      twoWheelerCount: [0, [Validators.min(0)]],
      fourWheelerCount: [0, [Validators.min(0)]],
      occupancyStatus: ['VACANT']
    });

    // Recompute the water charge from the live config whenever the BHK type changes.
    this.unitForm.get('bhkType')?.valueChanges.subscribe(() => this.recomputeWaterCharge());

    // Load the active water charge configuration so the water field reflects reality
    // (read-only + auto-calculated) instead of hardcoded numbers.
    this.maintenanceService.getWaterChargeConfig().subscribe({
      next: (res) => {
        this.waterConfig = res.success ? res.data : null;
        this.hasActiveWaterConfig = !!(this.waterConfig && this.waterConfig.isActive !== false);
        this.waterConfigLoaded = true;
        this.recomputeWaterCharge();
      },
      error: () => {
        this.waterConfig = null;
        this.hasActiveWaterConfig = false;
        this.waterConfigLoaded = true;
      }
    });

    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEdit = true;
      this.unitId = +id;
      this.ownerService.getUnitById(this.unitId).subscribe(res => {
        if (res.success) {
          this.unitForm.patchValue(res.data);
          this.recomputeWaterCharge();
        }
      });
    }
  }

  /** Tank allocation for a BHK type, read from the active config (no hardcoded map). */
  private tanksForBhk(bhk: string | null): number | null {
    const c = this.waterConfig;
    if (!bhk || !c) return null;
    switch (bhk) {
      case 'RK_1': return c.tanksRk1 ?? null;
      case 'BHK_1': return c.tanksBhk1 ?? null;
      case 'BHK_2': return c.tanksBhk2 ?? null;
      case 'BHK_3': return c.tanksBhk3 ?? null;
      case 'BHK_4': return c.tanksBhk4 ?? null;
      case 'SHOP': return c.tanksShop ?? null;
      case 'OTHER': return c.tanksBhk1 ?? null;
      default: return null;
    }
  }

  /**
   * Derive the Water Charges amount + info text from the active config.
   * Only PRIVATE_TANKER has a per-unit formula derivable from BHK alone; MUNICIPAL depends
   * on a society-wide split, so we don't guess it here.
   */
  private recomputeWaterCharge(): void {
    this.waterInfo = '';

    if (!this.hasActiveWaterConfig || !this.waterConfig) {
      return; // No active config -> manual entry allowed.
    }

    const bhk = this.unitForm.get('bhkType')?.value;

    if (this.waterConfig.waterSource === 'PRIVATE_TANKER') {
      const tanks = this.tanksForBhk(bhk);
      const rate = Number(this.waterConfig.ratePerTank) || 0;
      const fixed = Number(this.waterConfig.fixedChargePerUnit) || 0;
      if (tanks != null) {
        const amount = (rate * tanks) + fixed;
        this.unitForm.patchValue({ waterCharges: amount }, { emitEvent: false });
        this.waterInfo = `Water Charge (Private Tanker): ${tanks} tank(s) → (${tanks} × ₹${rate}) + ₹${fixed} fixed = ₹${amount.toLocaleString('en-IN')}`;
      } else {
        this.waterInfo = 'Select a BHK type to auto-calculate the water charge from the active configuration.';
      }
    } else if (this.waterConfig.waterSource === 'MUNICIPAL') {
      this.waterInfo = 'Water Charge (Municipal): calculated at bill generation by splitting the municipal tax across all active units. The exact per-unit amount appears on the generated bill.';
    }
  }

  onSubmit(): void {
    if (this.unitForm.invalid) return;
    const request = this.unitForm.getRawValue();

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
