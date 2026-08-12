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
import { MatChipsModule } from '@angular/material/chips';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MaintenanceService } from '@core/services/maintenance.service';
import { OwnerService } from '@core/services/owner.service';

@Component({
  selector: 'app-penalty-management',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, MatButtonModule, MatCardModule,
    MatIconModule, MatTableModule, MatChipsModule, MatSnackBarModule
  ],
  template: `
    <div class="container">
      <div class="page-header">
        <h2>Penalty / Fine Management</h2>
        <a mat-raised-button routerLink="/maintenance">
          <mat-icon>arrow_back</mat-icon> Back to Bills
        </a>
      </div>

      <!-- Add Penalty Form -->
      <mat-card class="form-card">
        <mat-card-header>
          <mat-card-title>Add Penalty</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="penaltyForm" (ngSubmit)="onSubmit()">
            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Select Unit *</mat-label>
                <mat-select formControlName="unitId">
                  <mat-option *ngFor="let unit of units" [value]="unit.unitId">
                    {{ unit.unitNumber }}
                  </mat-option>
                </mat-select>
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>Category *</mat-label>
                <mat-select formControlName="category">
                  <mat-option value="WATER_WASTAGE">Water Wastage</mat-option>
                  <mat-option value="WRONG_PARKING">Wrong Parking</mat-option>
                  <mat-option value="NOISE_COMPLAINT">Noise Complaint</mat-option>
                  <mat-option value="RULE_VIOLATION">Rule Violation</mat-option>
                  <mat-option value="DAMAGE_TO_PROPERTY">Damage to Property</mat-option>
                  <mat-option value="OTHER">Other</mat-option>
                </mat-select>
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>Amount (₹) *</mat-label>
                <input matInput type="number" formControlName="amount">
              </mat-form-field>
            </div>

            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Bill Month *</mat-label>
                <mat-select formControlName="billMonth">
                  <mat-option *ngFor="let m of months" [value]="m.value">{{ m.label }}</mat-option>
                </mat-select>
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>Bill Year *</mat-label>
                <input matInput type="number" formControlName="billYear">
              </mat-form-field>
            </div>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Reason *</mat-label>
              <textarea matInput formControlName="reason" rows="2"
                        placeholder="e.g., Water tank overflow due to negligence"></textarea>
            </mat-form-field>

            <div class="action-buttons">
              <button mat-raised-button color="warn" type="submit" [disabled]="penaltyForm.invalid">
                <mat-icon>gavel</mat-icon> Impose Penalty
              </button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>

      <!-- Pending Penalties Table -->
      <mat-card>
        <mat-card-header>
          <mat-card-title>Pending Penalties</mat-card-title>
          <mat-card-subtitle>These will be included in the next bill generation</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <table mat-table [dataSource]="penalties" class="mat-elevation-z1">
            <ng-container matColumnDef="unitNumber">
              <th mat-header-cell *matHeaderCellDef>Unit</th>
              <td mat-cell *matCellDef="let p">{{ p.unitNumber }}</td>
            </ng-container>
            <ng-container matColumnDef="category">
              <th mat-header-cell *matHeaderCellDef>Category</th>
              <td mat-cell *matCellDef="let p">{{ p.category?.replace('_', ' ') }}</td>
            </ng-container>
            <ng-container matColumnDef="reason">
              <th mat-header-cell *matHeaderCellDef>Reason</th>
              <td mat-cell *matCellDef="let p">{{ p.reason }}</td>
            </ng-container>
            <ng-container matColumnDef="amount">
              <th mat-header-cell *matHeaderCellDef>Amount</th>
              <td mat-cell *matCellDef="let p" class="amount-cell">₹ {{ p.amount | number:'1.0-0' }}</td>
            </ng-container>
            <ng-container matColumnDef="billMonth">
              <th mat-header-cell *matHeaderCellDef>For Month</th>
              <td mat-cell *matCellDef="let p">{{ p.billMonth }}/{{ p.billYear }}</td>
            </ng-container>
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>Status</th>
              <td mat-cell *matCellDef="let p">
                <span class="status-badge" [ngClass]="p.status.toLowerCase()">{{ p.status }}</span>
              </td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef>Actions</th>
              <td mat-cell *matCellDef="let p">
                <button mat-icon-button color="warn" (click)="cancelPenalty(p.penaltyId)"
                        *ngIf="p.status === 'PENDING'">
                  <mat-icon>cancel</mat-icon>
                </button>
              </td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>

            <tr class="mat-row" *matNoDataRow>
              <td class="mat-cell" [attr.colspan]="displayedColumns.length" style="text-align:center; padding:24px; color:#666;">
                No pending penalties.
              </td>
            </tr>
          </table>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .form-card { margin-bottom: 24px; }
    .form-row { display: flex; gap: 16px; flex-wrap: wrap; }
    .form-row mat-form-field { flex: 1; min-width: 180px; }
    .full-width { width: 100%; }
    .action-buttons { display: flex; justify-content: flex-end; margin-top: 8px; }
    .amount-cell { font-weight: 600; font-family: monospace; }
    .status-badge { padding: 2px 8px; border-radius: 4px; font-size: 0.8em; }
    .status-badge.pending { background: #fff3e0; color: #e65100; }
    .status-badge.billed { background: #e8f5e9; color: #2e7d32; }
    .status-badge.cancelled { background: #fce4ec; color: #c62828; }
  `]
})
export class PenaltyManagementComponent implements OnInit {
  penaltyForm!: FormGroup;
  penalties: any[] = [];
  units: any[] = [];
  displayedColumns = ['unitNumber', 'category', 'reason', 'amount', 'billMonth', 'status', 'actions'];
  months = [
    { value: 1, label: 'January' }, { value: 2, label: 'February' },
    { value: 3, label: 'March' }, { value: 4, label: 'April' },
    { value: 5, label: 'May' }, { value: 6, label: 'June' },
    { value: 7, label: 'July' }, { value: 8, label: 'August' },
    { value: 9, label: 'September' }, { value: 10, label: 'October' },
    { value: 11, label: 'November' }, { value: 12, label: 'December' }
  ];

  constructor(
    private fb: FormBuilder,
    private maintenanceService: MaintenanceService,
    private ownerService: OwnerService,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    const now = new Date();
    this.penaltyForm = this.fb.group({
      unitId: [null, Validators.required],
      category: ['WATER_WASTAGE', Validators.required],
      amount: [null, [Validators.required, Validators.min(1)]],
      reason: ['', Validators.required],
      billMonth: [now.getMonth() + 1, Validators.required],
      billYear: [now.getFullYear(), Validators.required]
    });

    this.ownerService.getAllUnits(0, 300).subscribe(res => {
      if (res.success) this.units = res.data.content;
    });

    this.loadPenalties();
  }

  loadPenalties(): void {
    this.maintenanceService.getPendingPenalties().subscribe(res => {
      if (res.success) this.penalties = res.data;
    });
  }

  onSubmit(): void {
    if (this.penaltyForm.invalid) return;

    this.maintenanceService.addPenalty(this.penaltyForm.value).subscribe({
      next: (res) => {
        if (res.success) {
          this.snackBar.open('Penalty imposed successfully', 'Close', { duration: 3000 });
          this.penaltyForm.patchValue({ amount: null, reason: '' });
          this.loadPenalties();
        }
      },
      error: (err) => {
        this.snackBar.open(err.error?.message || 'Failed to add penalty', 'Close', { duration: 5000 });
      }
    });
  }

  cancelPenalty(penaltyId: number): void {
    this.maintenanceService.cancelPenalty(penaltyId).subscribe({
      next: (res) => {
        if (res.success) {
          this.snackBar.open('Penalty cancelled', 'Close', { duration: 3000 });
          this.loadPenalties();
        }
      }
    });
  }
}
