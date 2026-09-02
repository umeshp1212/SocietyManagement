import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MaintenanceService } from '@core/services/maintenance.service';
import { AuthService } from '@core/services/auth.service';

@Component({
  selector: 'app-water-charge-config',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, MatButtonModule, MatCardModule,
    MatIconModule, MatTableModule, MatSnackBarModule, MatDividerModule,
    MatProgressSpinnerModule
  ],
  template: `
    <div class="container">
      <div class="page-header">
        <h2>Water Charge Configuration</h2>
        <a mat-raised-button routerLink="/maintenance/charge-config">
          <mat-icon>arrow_back</mat-icon> Back to Charges
        </a>
      </div>

      <mat-card>
        <mat-card-header>
          <mat-card-title>Water Source &amp; Pricing</mat-card-title>
          <mat-card-subtitle>
            Configure how water charges are calculated for maintenance bills
          </mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="configForm" (ngSubmit)="onSubmit()">

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Water Source</mat-label>
              <mat-select formControlName="waterSource">
                <mat-option value="PRIVATE_TANKER">Private Tanker (Vendor)</mat-option>
                <mat-option value="MUNICIPAL">Municipal Corporation</mat-option>
              </mat-select>
              <mat-hint>Current: {{ configForm.get('waterSource')?.value === 'PRIVATE_TANKER' ? 'Society orders water tankers from vendor' : 'Municipal corporation supplies water' }}</mat-hint>
            </mat-form-field>

            <!-- PRIVATE TANKER FIELDS -->
            <div *ngIf="configForm.get('waterSource')?.value === 'PRIVATE_TANKER'" class="section">
              <h3 class="section-title">Private Tanker Pricing</h3>
              <p class="hint-text">Formula: (Rate Per Tank x Number of Tanks) + Fixed Charge Per Flat</p>

              <div class="form-row">
                <mat-form-field appearance="outline">
                  <mat-label>Rate Per Tank (₹)</mat-label>
                  <input matInput type="number" formControlName="ratePerTank">
                  <mat-hint>e.g., ₹300 per tank</mat-hint>
                </mat-form-field>

                <mat-form-field appearance="outline">
                  <mat-label>Fixed Charge Per Flat (₹)</mat-label>
                  <input matInput type="number" formControlName="fixedChargePerUnit">
                  <mat-hint>e.g., ₹500 fixed per flat/month</mat-hint>
                </mat-form-field>
              </div>

              <mat-divider></mat-divider>
              <h3 class="section-title">Tank Allocation Per BHK Type</h3>

              <div class="form-row">
                <mat-form-field appearance="outline">
                  <mat-label>1 RK (Tanks)</mat-label>
                  <input matInput type="number" formControlName="tanksRk1">
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>1 BHK (Tanks)</mat-label>
                  <input matInput type="number" formControlName="tanksBhk1">
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>2 BHK (Tanks)</mat-label>
                  <input matInput type="number" formControlName="tanksBhk2">
                </mat-form-field>
              </div>
              <div class="form-row">
                <mat-form-field appearance="outline">
                  <mat-label>3 BHK (Tanks)</mat-label>
                  <input matInput type="number" formControlName="tanksBhk3">
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>4 BHK (Tanks)</mat-label>
                  <input matInput type="number" formControlName="tanksBhk4">
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Shop (Tanks)</mat-label>
                  <input matInput type="number" formControlName="tanksShop">
                </mat-form-field>
              </div>
            </div>

            <!-- MUNICIPAL FIELDS -->
            <div *ngIf="configForm.get('waterSource')?.value === 'MUNICIPAL'" class="section">
              <h3 class="section-title">Municipal Water Tax</h3>
              <p class="hint-text">Municipal corporation charges society-level water tax, which is split across all flats.</p>

              <div class="form-row">
                <mat-form-field appearance="outline">
                  <mat-label>Total Monthly Municipal Tax (₹)</mat-label>
                  <input matInput type="number" formControlName="municipalTaxAmount">
                  <mat-hint>Total amount charged to society per month</mat-hint>
                </mat-form-field>

                <mat-form-field appearance="outline">
                  <mat-label>Split Method</mat-label>
                  <mat-select formControlName="municipalSplitType">
                    <mat-option value="EQUAL">Equal (divide equally among all flats)</mat-option>
                    <mat-option value="BHK_BASED">BHK Based (proportional to tank allocation)</mat-option>
                  </mat-select>
                </mat-form-field>
              </div>

              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Additional Surcharge Per Flat (₹)</mat-label>
                <input matInput type="number" formControlName="municipalSurchargePerUnit">
                <mat-hint>Extra fixed charge per flat (e.g., pump maintenance, motor electricity)</mat-hint>
              </mat-form-field>

              <mat-divider></mat-divider>
              <h3 class="section-title">Tank Allocation (for BHK-Based Split)</h3>
              <p class="hint-text">Used to calculate proportional share when split method is BHK Based.</p>

              <div class="form-row">
                <mat-form-field appearance="outline">
                  <mat-label>1 RK (Tanks)</mat-label>
                  <input matInput type="number" formControlName="tanksRk1">
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>1 BHK (Tanks)</mat-label>
                  <input matInput type="number" formControlName="tanksBhk1">
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>2 BHK (Tanks)</mat-label>
                  <input matInput type="number" formControlName="tanksBhk2">
                </mat-form-field>
              </div>
              <div class="form-row">
                <mat-form-field appearance="outline">
                  <mat-label>3 BHK (Tanks)</mat-label>
                  <input matInput type="number" formControlName="tanksBhk3">
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>4 BHK (Tanks)</mat-label>
                  <input matInput type="number" formControlName="tanksBhk4">
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Shop (Tanks)</mat-label>
                  <input matInput type="number" formControlName="tanksShop">
                </mat-form-field>
              </div>
            </div>

            <div class="action-buttons">
              <button mat-raised-button color="accent" type="button" (click)="previewCharges()"
                      [disabled]="configForm.invalid || loadingPreview">
                <mat-icon>preview</mat-icon> Preview Charges
              </button>
              <button mat-raised-button color="primary" type="submit"
                      *ngIf="canManage()" [disabled]="configForm.invalid">
                <mat-icon>save</mat-icon> Save Configuration
              </button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>

      <!-- PREVIEW TABLE -->
      <mat-card *ngIf="previewData.length > 0" class="preview-card">
        <mat-card-header>
          <mat-card-title>Water Charge Preview</mat-card-title>
          <mat-card-subtitle>Estimated charges per unit based on current configuration</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <div *ngIf="loadingPreview" class="loading-spinner">
            <mat-spinner diameter="30"></mat-spinner>
          </div>
          <table mat-table [dataSource]="previewData" class="mat-elevation-z1" *ngIf="!loadingPreview">
            <ng-container matColumnDef="unitNumber">
              <th mat-header-cell *matHeaderCellDef>Unit No</th>
              <td mat-cell *matCellDef="let row">{{ row.unitNumber }}</td>
            </ng-container>
            <ng-container matColumnDef="bhkType">
              <th mat-header-cell *matHeaderCellDef>BHK Type</th>
              <td mat-cell *matCellDef="let row">{{ row.bhkType }}</td>
            </ng-container>
            <ng-container matColumnDef="tanks">
              <th mat-header-cell *matHeaderCellDef>Tanks</th>
              <td mat-cell *matCellDef="let row">{{ row.tanks }}</td>
            </ng-container>
            <ng-container matColumnDef="waterCharge">
              <th mat-header-cell *matHeaderCellDef>Water Charge (₹)</th>
              <td mat-cell *matCellDef="let row" class="amount-cell">
                &#8377; {{ row.waterCharge | number:'1.2-2' }}
              </td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="previewColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: previewColumns;"></tr>
          </table>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .full-width { width: 100%; }
    .section { margin-top: 16px; }
    .section-title { margin: 16px 0 8px; color: #333; font-size: 15px; font-weight: 500; }
    .hint-text { font-size: 13px; color: #666; margin-bottom: 12px; }
    .form-row { display: flex; gap: 16px; flex-wrap: wrap; }
    .form-row mat-form-field { flex: 1; min-width: 160px; }
    .action-buttons { display: flex; justify-content: flex-end; gap: 12px; margin-top: 24px; }
    .preview-card { margin-top: 24px; }
    .amount-cell { text-align: right; font-family: monospace; font-weight: 600; }
    .loading-spinner { display: flex; justify-content: center; padding: 20px; }
  `]
})
export class WaterChargeConfigComponent implements OnInit {
  configForm!: FormGroup;
  previewData: any[] = [];
  previewColumns = ['unitNumber', 'bhkType', 'tanks', 'waterCharge'];
  loadingPreview = false;

  constructor(
    private fb: FormBuilder,
    private maintenanceService: MaintenanceService,
    private snackBar: MatSnackBar,
    private authService: AuthService
  ) {}

  canManage(): boolean {
    return this.authService.hasPermission('MAINTENANCE_CONFIG');
  }

  ngOnInit(): void {
    this.configForm = this.fb.group({
      waterSource: ['PRIVATE_TANKER', Validators.required],
      ratePerTank: [300],
      fixedChargePerUnit: [500],
      tanksRk1: [2],
      tanksBhk1: [3],
      tanksBhk2: [3],
      tanksBhk3: [4],
      tanksBhk4: [5],
      tanksShop: [1],
      municipalTaxAmount: [null],
      municipalSplitType: ['EQUAL'],
      municipalSurchargePerUnit: [0]
    });

    // Load existing config
    this.maintenanceService.getWaterChargeConfig().subscribe(res => {
      if (res.success && res.data) {
        this.configForm.patchValue(res.data);
      }
    });
  }

  onSubmit(): void {
    if (this.configForm.invalid) return;

    this.maintenanceService.saveWaterChargeConfig(this.configForm.value).subscribe({
      next: (res) => {
        if (res.success) {
          this.snackBar.open('Water charge configuration saved successfully', 'Close', { duration: 3000 });
        }
      },
      error: (err) => {
        this.snackBar.open(err.error?.message || 'Failed to save configuration', 'Close', { duration: 5000 });
      }
    });
  }

  previewCharges(): void {
    // Save first, then preview
    this.loadingPreview = true;
    this.maintenanceService.saveWaterChargeConfig(this.configForm.value).subscribe({
      next: () => {
        this.maintenanceService.previewWaterCharges().subscribe({
          next: (res) => {
            if (res.success) {
              this.previewData = res.data;
            }
            this.loadingPreview = false;
          },
          error: () => { this.loadingPreview = false; }
        });
      },
      error: () => { this.loadingPreview = false; }
    });
  }
}
