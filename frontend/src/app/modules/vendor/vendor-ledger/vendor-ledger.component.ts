import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { VendorService } from '@core/services/vendor.service';
import { VendorLedger, LedgerEntry } from '@core/models/vendor.model';

@Component({
  selector: 'app-vendor-ledger',
  standalone: true,
  imports: [
    CommonModule, RouterModule, FormsModule, MatTableModule, MatButtonModule,
    MatIconModule, MatCardModule, MatFormFieldModule, MatInputModule,
    MatDatepickerModule, MatNativeDateModule, MatChipsModule, MatProgressSpinnerModule
  ],
  template: `
    <div class="container">
      <div class="page-header">
        <h2>Vendor Ledger</h2>
        <a mat-raised-button routerLink="/vendors">
          <mat-icon>arrow_back</mat-icon> Back to Vendors
        </a>
      </div>

      <mat-card *ngIf="ledger" class="vendor-info-card">
        <mat-card-content>
          <div class="vendor-summary">
            <div>
              <strong>Vendor:</strong> {{ ledger.vendorName }}
            </div>
            <div>
              <strong>Total Payments:</strong>
              <span class="total-amount">&#8377; {{ ledger.totalAmount | number:'1.2-2' }}</span>
            </div>
            <div>
              <strong>Transactions:</strong> {{ ledger.entries.length }}
            </div>
          </div>
        </mat-card-content>
      </mat-card>

      <mat-card class="filter-card">
        <mat-card-content>
          <div class="filter-row">
            <mat-form-field appearance="outline">
              <mat-label>Start Date</mat-label>
              <input matInput [matDatepicker]="startPicker" [(ngModel)]="startDate">
              <mat-datepicker-toggle matSuffix [for]="startPicker"></mat-datepicker-toggle>
              <mat-datepicker #startPicker></mat-datepicker>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>End Date</mat-label>
              <input matInput [matDatepicker]="endPicker" [(ngModel)]="endDate">
              <mat-datepicker-toggle matSuffix [for]="endPicker"></mat-datepicker-toggle>
              <mat-datepicker #endPicker></mat-datepicker>
            </mat-form-field>

            <button mat-raised-button color="primary" (click)="loadLedger()">
              <mat-icon>filter_list</mat-icon> Filter
            </button>
            <button mat-button (click)="clearFilter()">
              <mat-icon>clear</mat-icon> Clear
            </button>
            <button mat-raised-button color="accent" (click)="downloadPdf()"
                    [disabled]="!ledger || ledger.entries.length === 0">
              <mat-icon>picture_as_pdf</mat-icon> Download PDF
            </button>
          </div>
        </mat-card-content>
      </mat-card>

      <div *ngIf="loading" class="loading-spinner">
        <mat-spinner diameter="40"></mat-spinner>
      </div>

      <table mat-table [dataSource]="ledger?.entries || []" class="mat-elevation-z2" *ngIf="!loading">
        <ng-container matColumnDef="voucherDate">
          <th mat-header-cell *matHeaderCellDef>Date</th>
          <td mat-cell *matCellDef="let entry">{{ entry.voucherDate | date:'dd/MM/yyyy' }}</td>
        </ng-container>

        <ng-container matColumnDef="voucherNumber">
          <th mat-header-cell *matHeaderCellDef>Voucher No</th>
          <td mat-cell *matCellDef="let entry">
            <a [routerLink]="['/vouchers', entry.voucherId]">{{ entry.voucherNumber }}</a>
          </td>
        </ng-container>

        <ng-container matColumnDef="voucherType">
          <th mat-header-cell *matHeaderCellDef>Type</th>
          <td mat-cell *matCellDef="let entry">
            <span class="type-badge" [ngClass]="entry.voucherType.toLowerCase()">
              {{ entry.voucherType }}
            </span>
          </td>
        </ng-container>

        <ng-container matColumnDef="category">
          <th mat-header-cell *matHeaderCellDef>Category</th>
          <td mat-cell *matCellDef="let entry">{{ entry.category?.replace('_', ' ') }}</td>
        </ng-container>

        <ng-container matColumnDef="description">
          <th mat-header-cell *matHeaderCellDef>Description</th>
          <td mat-cell *matCellDef="let entry">{{ entry.description }}</td>
        </ng-container>

        <ng-container matColumnDef="paymentMode">
          <th mat-header-cell *matHeaderCellDef>Payment Mode</th>
          <td mat-cell *matCellDef="let entry">{{ entry.paymentMode || '-' }}</td>
        </ng-container>

        <ng-container matColumnDef="amount">
          <th mat-header-cell *matHeaderCellDef>Amount</th>
          <td mat-cell *matCellDef="let entry" class="amount-cell">
            &#8377; {{ entry.amount | number:'1.2-2' }}
          </td>
        </ng-container>

        <ng-container matColumnDef="runningTotal">
          <th mat-header-cell *matHeaderCellDef>Running Total</th>
          <td mat-cell *matCellDef="let entry" class="amount-cell running-total">
            &#8377; {{ entry.runningTotal | number:'1.2-2' }}
          </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>

        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell no-data" [attr.colspan]="displayedColumns.length">
            No transactions found for this vendor.
          </td>
        </tr>
      </table>
    </div>
  `,
  styles: [`
    .vendor-info-card { margin-bottom: 16px; }
    .vendor-summary {
      display: flex;
      gap: 32px;
      align-items: center;
      flex-wrap: wrap;
    }
    .total-amount {
      font-size: 1.2em;
      font-weight: 600;
      color: #1976d2;
    }
    .filter-card { margin-bottom: 16px; }
    .filter-row {
      display: flex;
      gap: 16px;
      align-items: center;
      flex-wrap: wrap;
    }
    .filter-row mat-form-field { width: 180px; }
    .loading-spinner {
      display: flex;
      justify-content: center;
      padding: 40px;
    }
    .amount-cell { text-align: right; font-family: monospace; }
    .running-total { font-weight: 600; }
    .type-badge {
      padding: 2px 8px;
      border-radius: 4px;
      font-size: 0.8em;
      font-weight: 500;
    }
    .type-badge.payment { background: #ffecb3; color: #f57c00; }
    .type-badge.receipt { background: #c8e6c9; color: #388e3c; }
    .type-badge.journal { background: #e1bee7; color: #7b1fa2; }
    .no-data {
      text-align: center;
      padding: 24px;
      color: #666;
    }
  `]
})
export class VendorLedgerComponent implements OnInit {
  ledger: VendorLedger | null = null;
  loading = false;
  vendorId!: number;
  startDate: Date | null = null;
  endDate: Date | null = null;
  displayedColumns = [
    'voucherDate', 'voucherNumber', 'voucherType', 'category',
    'description', 'paymentMode', 'amount', 'runningTotal'
  ];

  constructor(
    private route: ActivatedRoute,
    private vendorService: VendorService
  ) {}

  ngOnInit(): void {
    this.vendorId = +this.route.snapshot.paramMap.get('id')!;
    this.loadLedger();
  }

  loadLedger(): void {
    this.loading = true;
    const startStr = this.startDate ? this.formatDate(this.startDate) : undefined;
    const endStr = this.endDate ? this.formatDate(this.endDate) : undefined;

    this.vendorService.getVendorLedger(this.vendorId, startStr, endStr).subscribe({
      next: (res) => {
        if (res.success) {
          this.ledger = res.data;
        }
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      }
    });
  }

  clearFilter(): void {
    this.startDate = null;
    this.endDate = null;
    this.loadLedger();
  }

  downloadPdf(): void {
    const startStr = this.startDate ? this.formatDate(this.startDate) : undefined;
    const endStr = this.endDate ? this.formatDate(this.endDate) : undefined;

    this.vendorService.downloadLedgerPdf(this.vendorId, startStr, endStr).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `vendor-ledger-${this.vendorId}.pdf`;
        link.click();
        window.URL.revokeObjectURL(url);
      },
      error: () => {
        // handle error silently
      }
    });
  }

  private formatDate(date: Date): string {
    return date.toISOString().split('T')[0];
  }
}
