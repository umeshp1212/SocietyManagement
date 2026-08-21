import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { environment } from '@env/environment';

interface OpeningBalance {
  openingBalanceId: number;
  unitId: number;
  unitNumber: string;
  ownerName: string;
  amount: number;
  asOfDate: string;
  remarks: string;
  enteredBy: string;
  paidAmount: number;
  balanceAmount: number;
  createdOn: string;
}

interface UnitOption {
  unitId: number;
  unitNumber: string;
  ownerNames: string;
}

@Component({
  selector: 'app-opening-balance',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatCardModule, MatTableModule, MatButtonModule,
    MatIconModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatDialogModule, MatSnackBarModule, MatChipsModule, MatTooltipModule
  ],
  template: `
    <div class="container">
      <div class="page-header">
        <h2>Opening Balances (Legacy Arrears)</h2>
        <button mat-raised-button color="primary" (click)="showAddForm = !showAddForm">
          <mat-icon>{{ showAddForm ? 'close' : 'add' }}</mat-icon>
          {{ showAddForm ? 'Cancel' : 'Add Opening Balance' }}
        </button>
      </div>

      <p class="subtitle">
        Enter outstanding amounts from before the system was deployed. This gets added to unit's total dues.
      </p>

      <!-- Summary Cards -->
      <div class="summary-cards" *ngIf="summary">
        <mat-card>
          <mat-card-content>
            <div class="summary-label">Total Entries</div>
            <div class="summary-value">{{ summary.totalEntries }}</div>
          </mat-card-content>
        </mat-card>
        <mat-card>
          <mat-card-content>
            <div class="summary-label">Outstanding Entries</div>
            <div class="summary-value">{{ summary.outstandingEntries }}</div>
          </mat-card-content>
        </mat-card>
        <mat-card>
          <mat-card-content>
            <div class="summary-label">Total Outstanding</div>
            <div class="summary-value">{{ summary.totalOutstandingAmount | currency:'INR' }}</div>
          </mat-card-content>
        </mat-card>
      </div>

      <!-- Add/Edit Form -->
      <mat-card *ngIf="showAddForm" class="form-card">
        <mat-card-header>
          <mat-card-title>{{ editingId ? 'Update' : 'Add' }} Opening Balance</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div class="form-row">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Select Unit</mat-label>
              <mat-select [(ngModel)]="formData.unitId">
                <mat-option *ngFor="let unit of units" [value]="unit.unitId">
                  {{ unit.unitNumber }} - {{ unit.ownerNames || 'No owner' }}
                </mat-option>
              </mat-select>
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Outstanding Amount (Rs)</mat-label>
              <input matInput type="number" [(ngModel)]="formData.amount" placeholder="0.00">
            </mat-form-field>
          </div>

          <div class="form-row">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>As of Date</mat-label>
              <input matInput type="date" [(ngModel)]="formData.asOfDate">
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Remarks</mat-label>
              <input matInput [(ngModel)]="formData.remarks" placeholder="e.g., Arrears from April 2019 to March 2026">
            </mat-form-field>
          </div>

          <div class="action-buttons">
            <button mat-button (click)="showAddForm = false; resetForm()">Cancel</button>
            <button mat-raised-button color="primary" (click)="saveOpeningBalance()"
                    [disabled]="!formData.unitId || !formData.amount">
              <mat-icon>save</mat-icon> Save
            </button>
          </div>
        </mat-card-content>
      </mat-card>

      <!-- Table -->
      <mat-card>
        <mat-card-content>
          <table mat-table [dataSource]="balances" class="mat-elevation-z0">
            <ng-container matColumnDef="unitNumber">
              <th mat-header-cell *matHeaderCellDef>Unit</th>
              <td mat-cell *matCellDef="let b">{{ b.unitNumber }}</td>
            </ng-container>
            <ng-container matColumnDef="ownerName">
              <th mat-header-cell *matHeaderCellDef>Owner</th>
              <td mat-cell *matCellDef="let b">{{ b.ownerName || '-' }}</td>
            </ng-container>
            <ng-container matColumnDef="amount">
              <th mat-header-cell *matHeaderCellDef>Original Amount</th>
              <td mat-cell *matCellDef="let b">{{ b.amount | currency:'INR' }}</td>
            </ng-container>
            <ng-container matColumnDef="paidAmount">
              <th mat-header-cell *matHeaderCellDef>Paid</th>
              <td mat-cell *matCellDef="let b">{{ b.paidAmount | currency:'INR' }}</td>
            </ng-container>
            <ng-container matColumnDef="balanceAmount">
              <th mat-header-cell *matHeaderCellDef>Balance Due</th>
              <td mat-cell *matCellDef="let b">
                <span [class.text-warn]="b.balanceAmount > 0">{{ b.balanceAmount | currency:'INR' }}</span>
              </td>
            </ng-container>
            <ng-container matColumnDef="asOfDate">
              <th mat-header-cell *matHeaderCellDef>As of Date</th>
              <td mat-cell *matCellDef="let b">{{ b.asOfDate }}</td>
            </ng-container>
            <ng-container matColumnDef="remarks">
              <th mat-header-cell *matHeaderCellDef>Remarks</th>
              <td mat-cell *matCellDef="let b">{{ b.remarks || '-' }}</td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef>Actions</th>
              <td mat-cell *matCellDef="let b">
                <button mat-icon-button (click)="editBalance(b)" matTooltip="Edit">
                  <mat-icon>edit</mat-icon>
                </button>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
          </table>

          <div *ngIf="balances.length === 0" class="no-data">
            <mat-icon>account_balance_wallet</mat-icon>
            <p>No opening balances entered yet</p>
          </div>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .subtitle { color: #666; margin-bottom: 16px; }
    .summary-cards { display: flex; gap: 16px; margin-bottom: 20px; flex-wrap: wrap; }
    .summary-cards mat-card { flex: 1; min-width: 150px; text-align: center; }
    .summary-label { font-size: 12px; color: #666; }
    .summary-value { font-size: 1.5rem; font-weight: 500; color: #1976d2; }
    .form-card { margin-bottom: 20px; }
    .form-row { display: flex; gap: 16px; }
    .form-row mat-form-field { flex: 1; }
    .full-width { width: 100%; }
    .text-warn { color: #c62828; font-weight: 500; }
    .no-data { text-align: center; padding: 32px; color: #999; }
    .no-data mat-icon { font-size: 48px; height: 48px; width: 48px; }

    @media (max-width: 768px) {
      .form-row { flex-direction: column; gap: 0; }
      .summary-cards { flex-direction: column; }
    }
  `]
})
export class OpeningBalanceComponent implements OnInit {
  balances: OpeningBalance[] = [];
  units: UnitOption[] = [];
  summary: any = null;
  showAddForm = false;
  editingId: number | null = null;
  displayedColumns = ['unitNumber', 'ownerName', 'amount', 'paidAmount', 'balanceAmount', 'asOfDate', 'remarks', 'actions'];

  formData = { unitId: null as number | null, amount: null as number | null, asOfDate: '', remarks: '' };

  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient, private snackBar: MatSnackBar) {}

  ngOnInit(): void {
    this.loadBalances();
    this.loadUnits();
    this.loadSummary();
  }

  loadBalances(): void {
    this.http.get<any>(`${this.apiUrl}/opening-balances`).subscribe(res => {
      if (res.success) this.balances = res.data;
    });
  }

  loadUnits(): void {
    this.http.get<any>(`${this.apiUrl}/units?page=0&size=500`).subscribe(res => {
      if (res.success) this.units = res.data.content;
    });
  }

  loadSummary(): void {
    this.http.get<any>(`${this.apiUrl}/opening-balances/summary`).subscribe(res => {
      if (res.success) this.summary = res.data;
    });
  }

  saveOpeningBalance(): void {
    if (!this.formData.unitId || !this.formData.amount) return;

    this.http.post<any>(`${this.apiUrl}/opening-balances`, {
      unitId: this.formData.unitId,
      amount: this.formData.amount,
      asOfDate: this.formData.asOfDate || null,
      remarks: this.formData.remarks || null
    }).subscribe({
      next: (res) => {
        if (res.success) {
          this.snackBar.open('Opening balance saved', 'Close', { duration: 3000 });
          this.showAddForm = false;
          this.resetForm();
          this.loadBalances();
          this.loadSummary();
        }
      },
      error: (err) => {
        this.snackBar.open(err.error?.message || 'Failed to save', 'Close', { duration: 5000 });
      }
    });
  }

  editBalance(balance: OpeningBalance): void {
    this.formData = {
      unitId: balance.unitId,
      amount: balance.amount,
      asOfDate: balance.asOfDate || '',
      remarks: balance.remarks || ''
    };
    this.editingId = balance.openingBalanceId;
    this.showAddForm = true;
  }

  resetForm(): void {
    this.formData = { unitId: null, amount: null, asOfDate: '', remarks: '' };
    this.editingId = null;
  }
}
