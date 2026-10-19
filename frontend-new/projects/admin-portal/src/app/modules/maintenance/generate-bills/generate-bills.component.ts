import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { HttpClient } from '@angular/common/http';
import { environment } from '@env/environment';
import { MaintenanceService } from '@core/services/maintenance.service';
import { AuthService } from '@core/services/auth.service';
import { BackButtonComponent } from '@shared/components/back-button';
import { SearchableSelectComponent } from '@shared/components/searchable-select';

interface UnitOption {
  unitId: number;
  unitNumber: string;
  primaryOwnerName?: string;
  allOwnerNames?: string;
}

interface AdjustmentRow {
  unitId: number | null;
  amount: number | null;
}

@Component({
  selector: 'app-generate-bills',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, MatCardModule, MatButtonModule,
    MatIconModule, MatFormFieldModule, MatSelectModule, MatInputModule, MatCheckboxModule,
    BackButtonComponent, SearchableSelectComponent],
  template: `
    <div class="container">
      <app-back-button link="/maintenance" label="Back to Bills"></app-back-button>
      <div class="page-header">
        <h2>Generate Maintenance Bills</h2>
      </div>

      <mat-card *ngIf="!canManage()" class="no-access-card">
        <mat-card-content>
          <mat-icon color="warn">lock</mat-icon>
          <p>You don't have permission to generate maintenance bills.</p>
        </mat-card-content>
      </mat-card>

      <mat-card *ngIf="canManage()">
        <mat-card-content>
          <form class="generate-form" (ngSubmit)="generate()">
            <mat-form-field appearance="outline">
              <mat-label>Month</mat-label>
              <mat-select [(ngModel)]="selectedMonth" name="month" required>
                <mat-option *ngFor="let m of months; let i = index" [value]="i + 1">{{ m }}</mat-option>
              </mat-select>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Year</mat-label>
              <input matInput type="number" [(ngModel)]="selectedYear" name="year" required>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Due Day of Month</mat-label>
              <input matInput type="number" [(ngModel)]="dueDay" name="dueDay" min="1" max="28">
            </mat-form-field>

            <mat-checkbox [(ngModel)]="regenerate" name="regenerate" color="warn">
              Regenerate existing unpaid bills (recalculate with current charges)
            </mat-checkbox>
            <p class="hint-text" *ngIf="regenerate">
              This will delete all UNPAID bills for the selected month and regenerate them
              using the latest unit charges and charge configuration. Bills that are already
              PAID or PARTIALLY PAID will not be affected.
            </p>

            <!-- Optional per-unit "Less: Adjustment" credits/waivers applied to this run -->
            <div class="adjustments">
              <div class="adjustments-header">
                <span>Adjustments (optional)</span>
                <button mat-stroked-button type="button" (click)="addAdjustment()">
                  <mat-icon>add</mat-icon> Add adjustment
                </button>
              </div>
              <p class="adj-hint" *ngIf="adjustmentRows.length">
                A "Less: Adjustment" credit/waiver is subtracted from the selected unit's Total Due in this run.
              </p>
              <div class="adj-row" *ngFor="let row of adjustmentRows; let i = index">
                <app-searchable-select
                  [(ngModel)]="row.unitId"
                  [name]="'adjUnit' + i"
                  [ngModelOptions]="{ standalone: true }"
                  label="Unit"
                  placeholder="Search unit or owner..."
                  [options]="units"
                  valueKey="unitId"
                  [labelWith]="unitLabel">
                </app-searchable-select>
                <mat-form-field appearance="outline">
                  <mat-label>Adjustment (Rs)</mat-label>
                  <input matInput type="number" min="0" [(ngModel)]="row.amount"
                         [ngModelOptions]="{ standalone: true }" placeholder="0.00">
                </mat-form-field>
                <button mat-icon-button color="warn" type="button" (click)="removeAdjustment(i)" aria-label="Remove">
                  <mat-icon>delete</mat-icon>
                </button>
              </div>
            </div>

            <button mat-raised-button color="primary" type="submit" [disabled]="loading">
              <mat-icon>receipt_long</mat-icon>
              {{ regenerate ? 'Regenerate Bills' : 'Generate Bills' }}
            </button>
          </form>
        </mat-card-content>
      </mat-card>

      <mat-card *ngIf="result" class="result-card">
        <mat-card-content>
          <div class="result-icon">
            <mat-icon color="primary">check_circle</mat-icon>
          </div>
          <h3>Bills Generated Successfully</h3>
          <p *ngIf="result.billsGenerated > 0">New Bills Generated: <strong>{{ result.billsGenerated }}</strong></p>
          <p *ngIf="result.billsRegenerated > 0">Bills Regenerated: <strong>{{ result.billsRegenerated }}</strong></p>
          <p *ngIf="result.message">{{ result.message }}</p>
          <a mat-raised-button color="accent" routerLink="/maintenance">View Bills</a>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .container { padding: 24px; max-width: 600px; }
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .generate-form { display: flex; flex-direction: column; gap: 12px; }
    .generate-form mat-form-field { width: 100%; }
    .adjustments { border: 1px dashed #c7c7c7; border-radius: 6px; padding: 12px; }
    .adjustments-header { display: flex; justify-content: space-between; align-items: center; font-weight: 500; }
    .adj-hint { font-size: 12px; color: #666; margin: 8px 0; }
    .adj-row { display: flex; gap: 8px; align-items: center; }
    .adj-row app-searchable-select { flex: 2 1 0; min-width: 0; }
    .adj-row mat-form-field { flex: 1 1 0; min-width: 0; }
    .result-card { margin-top: 24px; text-align: center; }
    .result-icon mat-icon { font-size: 48px; width: 48px; height: 48px; }
    .hint-text { font-size: 12px; color: #e65100; background: #fff3e0; padding: 8px 12px; border-radius: 4px; margin: 0; }
  `]
})
export class GenerateBillsComponent implements OnInit {
  selectedMonth: number;
  selectedYear: number;
  dueDay = 10;
  regenerate = false;
  loading = false;
  result: any = null;
  months = ['January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December'];

  units: UnitOption[] = [];
  adjustmentRows: AdjustmentRow[] = [];

  /** Display label for a unit option in the searchable dropdown. */
  readonly unitLabel = (u: UnitOption): string =>
    `${u.unitNumber} - ${u.allOwnerNames || u.primaryOwnerName || 'No owner'}`;

  private apiUrl = environment.apiUrl;

  constructor(private maintenanceService: MaintenanceService, private authService: AuthService,
              private http: HttpClient) {
    const now = new Date();
    this.selectedMonth = now.getMonth() + 1;
    this.selectedYear = now.getFullYear();
  }

  ngOnInit(): void {
    if (this.canManage()) {
      this.http.get<any>(`${this.apiUrl}/units?page=0&size=500`).subscribe(res => {
        if (res.success) this.units = res.data.content;
      });
    }
  }

  canManage(): boolean {
    return this.authService.hasPermission('MAINTENANCE_CREATE');
  }

  addAdjustment(): void {
    this.adjustmentRows.push({ unitId: null, amount: null });
  }

  removeAdjustment(index: number): void {
    this.adjustmentRows.splice(index, 1);
  }

  /** Build the { unitId: amount } map from valid rows (unit selected + positive amount). */
  private buildAdjustments(): { [unitId: number]: number } {
    const map: { [unitId: number]: number } = {};
    for (const row of this.adjustmentRows) {
      const amount = Number(row.amount);
      if (row.unitId != null && amount > 0) {
        map[row.unitId] = amount;
      }
    }
    return map;
  }

  generate(): void {
    if (!this.canManage()) { return; }
    this.loading = true;
    this.result = null;
    const adjustments = this.buildAdjustments();
    this.maintenanceService.generateBills(this.selectedMonth, this.selectedYear, this.dueDay,
        this.regenerate, adjustments)
      .subscribe({
        next: res => {
          if (res.success) {
            this.result = res.data;
          }
          this.loading = false;
        },
        error: () => { this.loading = false; }
      });
  }
}
