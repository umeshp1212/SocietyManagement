import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatChipsModule } from '@angular/material/chips';
import { MaintenanceService } from '@core/services/maintenance.service';
import { AuthService } from '@core/services/auth.service';

@Component({
  selector: 'app-bill-list',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, MatTableModule, MatPaginatorModule,
    MatCardModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatSelectModule, MatInputModule, MatChipsModule],
  template: `
    <div class="container">
      <div class="page-header">
        <h2>Maintenance Bills</h2>
        <div class="header-actions">
          <a mat-button routerLink="/maintenance/penalties">
            <mat-icon>gavel</mat-icon> Penalties
          </a>
          <a mat-button routerLink="/maintenance/opening-balances">
            <mat-icon>account_balance_wallet</mat-icon> Opening Balances
          </a>
          <a mat-button routerLink="/maintenance/suspense">
            <mat-icon>help_outline</mat-icon> Suspense
          </a>
          <a mat-button routerLink="/maintenance/charge-config"
             *ngIf="hasPermission('MAINTENANCE_CONFIG')">
            <mat-icon>settings</mat-icon> Charge Config
          </a>
          <button mat-raised-button color="accent" (click)="downloadAllPdf()">
            <mat-icon>download</mat-icon> Download All Bills PDF
          </button>
          <a mat-raised-button color="primary" routerLink="/maintenance/generate"
             *ngIf="hasPermission('MAINTENANCE_CREATE')">
            <mat-icon>add</mat-icon> Generate Bills
          </a>
        </div>
      </div>

      <div class="filter-bar">
        <mat-form-field appearance="outline">
          <mat-label>Month</mat-label>
          <mat-select [(ngModel)]="selectedMonth">
            <mat-option *ngFor="let m of months; let i = index" [value]="i + 1">{{ m }}</mat-option>
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Year</mat-label>
          <input matInput type="number" [(ngModel)]="selectedYear">
        </mat-form-field>
        <button mat-raised-button color="accent" (click)="loadData()">
          <mat-icon>search</mat-icon> Load
        </button>
      </div>

      <div class="summary-cards" *ngIf="summary">
        <mat-card>
          <mat-card-content>
            <div class="summary-label">Total Billed</div>
            <div class="summary-value">{{ summary.totalBilled | currency:'INR' }}</div>
          </mat-card-content>
        </mat-card>
        <mat-card>
          <mat-card-content>
            <div class="summary-label">Total Collected</div>
            <div class="summary-value">{{ summary.totalCollected | currency:'INR' }}</div>
          </mat-card-content>
        </mat-card>
        <mat-card>
          <mat-card-content>
            <div class="summary-label">Outstanding</div>
            <div class="summary-value">{{ summary.totalOutstanding | currency:'INR' }}</div>
          </mat-card-content>
        </mat-card>
        <mat-card>
          <mat-card-content>
            <div class="summary-label">Paid</div>
            <div class="summary-value">{{ summary.paidCount }}</div>
          </mat-card-content>
        </mat-card>
        <mat-card>
          <mat-card-content>
            <div class="summary-label">Unpaid</div>
            <div class="summary-value">{{ summary.unpaidCount }}</div>
          </mat-card-content>
        </mat-card>
      </div>

      <table mat-table [dataSource]="bills" class="mat-elevation-z2">
        <ng-container matColumnDef="unitNumber">
          <th mat-header-cell *matHeaderCellDef>Unit No</th>
          <td mat-cell *matCellDef="let bill">{{ bill.unitNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="ownerName">
          <th mat-header-cell *matHeaderCellDef>Owner</th>
          <td mat-cell *matCellDef="let bill">{{ bill.ownerName }}</td>
        </ng-container>
        <ng-container matColumnDef="amount">
          <th mat-header-cell *matHeaderCellDef>Charges</th>
          <td mat-cell *matCellDef="let bill">{{ bill.amount | currency:'INR' }}</td>
        </ng-container>
        <ng-container matColumnDef="arrears">
          <th mat-header-cell *matHeaderCellDef>Arrears</th>
          <td mat-cell *matCellDef="let bill">{{ (bill.previousArrears || 0) + (bill.interestOnArrears || 0) | currency:'INR' }}</td>
        </ng-container>
        <ng-container matColumnDef="totalAmount">
          <th mat-header-cell *matHeaderCellDef>Total</th>
          <td mat-cell *matCellDef="let bill">{{ bill.totalAmount | currency:'INR' }}</td>
        </ng-container>
        <ng-container matColumnDef="paidAmount">
          <th mat-header-cell *matHeaderCellDef>Paid</th>
          <td mat-cell *matCellDef="let bill">{{ bill.paidAmount | currency:'INR' }}</td>
        </ng-container>
        <ng-container matColumnDef="balance">
          <th mat-header-cell *matHeaderCellDef>Balance</th>
          <td mat-cell *matCellDef="let bill">{{ bill.balanceAmount | currency:'INR' }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let bill">
            <span class="status-badge" [ngClass]="bill.status?.toLowerCase()">{{ bill.status }}</span>
          </td>
        </ng-container>
        <ng-container matColumnDef="dueDate">
          <th mat-header-cell *matHeaderCellDef>Due Date</th>
          <td mat-cell *matCellDef="let bill">{{ bill.dueDate | date:'mediumDate' }}</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef>Actions</th>
          <td mat-cell *matCellDef="let bill">
            <a mat-icon-button [routerLink]="['/maintenance/bill', bill.billId]" matTooltip="View">
              <mat-icon>visibility</mat-icon>
            </a>
            <button mat-icon-button (click)="downloadBillPdf(bill.billId)" matTooltip="Download PDF">
              <mat-icon>download</mat-icon>
            </button>
            <button mat-icon-button (click)="shareWhatsApp(bill.billId)" matTooltip="WhatsApp">
              <mat-icon>share</mat-icon>
            </button>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
      </table>
      <mat-paginator [length]="totalElements" [pageSize]="pageSize" [pageSizeOptions]="[10,20,50]"
        (page)="onPageChange($event)"></mat-paginator>
    </div>
  `,
  styles: [`
    .container { padding: 24px; }
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .header-actions { display: flex; gap: 8px; align-items: center; }
    .filter-bar { display: flex; gap: 16px; align-items: center; margin-bottom: 16px; }
    .filter-bar mat-form-field { width: 150px; }
    .summary-cards { display: flex; gap: 16px; margin-bottom: 24px; flex-wrap: wrap; }
    .summary-cards mat-card { flex: 1; min-width: 150px; text-align: center; }
    .summary-label { font-size: 12px; color: #666; text-transform: uppercase; }
    .summary-value { font-size: 24px; font-weight: 600; margin-top: 4px; }
    table { width: 100%; }
    .status-badge { padding: 4px 8px; border-radius: 12px; font-size: 12px; font-weight: 500; }
    .status-badge.paid { background: #e8f5e9; color: #2e7d32; }
    .status-badge.unpaid { background: #fbe9e7; color: #c62828; }
    .status-badge.partially_paid { background: #fff3e0; color: #e65100; }
    .status-badge.overdue { background: #ffebee; color: #b71c1c; }
  `]
})
export class BillListComponent implements OnInit {
  bills: any[] = [];
  summary: any = null;
  displayedColumns = ['unitNumber', 'ownerName', 'amount', 'arrears', 'totalAmount', 'paidAmount', 'balance', 'status', 'dueDate', 'actions'];
  totalElements = 0;
  pageSize = 20;
  currentPage = 0;
  selectedMonth: number;
  selectedYear: number;
  months = ['January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December'];

  constructor(private maintenanceService: MaintenanceService, private authService: AuthService) {
    const now = new Date();
    this.selectedMonth = now.getMonth() + 1;
    this.selectedYear = now.getFullYear();
  }

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.loadBills();
    this.loadSummary();
  }

  loadBills(): void {
    this.maintenanceService.getBillsByMonth(this.selectedMonth, this.selectedYear, this.currentPage, this.pageSize)
      .subscribe(res => {
        if (res.success) {
          this.bills = res.data.content;
          this.totalElements = res.data.totalElements;
        }
      });
  }

  loadSummary(): void {
    this.maintenanceService.getCollectionSummary(this.selectedMonth, this.selectedYear)
      .subscribe(res => {
        if (res.success) {
          this.summary = res.data;
        }
      });
  }

  onPageChange(event: PageEvent): void {
    this.currentPage = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadBills();
  }

  generatePaymentLink(billId: number): void {
    this.maintenanceService.generatePaymentLink(billId).subscribe(res => {
      if (res.success && res.data?.paymentLink) {
        navigator.clipboard.writeText(res.data.paymentLink);
      }
    });
  }

  shareWhatsApp(billId: number): void {
    this.maintenanceService.getWhatsAppLink(billId).subscribe(res => {
      if (res.success && res.data?.whatsappLink) {
        window.open(res.data.whatsappLink, '_blank');
      }
    });
  }

  downloadBillPdf(billId: number): void {
    this.maintenanceService.downloadBillPdf(billId);
  }

  downloadAllPdf(): void {
    this.maintenanceService.downloadBulkBillsPdf(this.selectedMonth, this.selectedYear);
  }

  hasPermission(permission: string): boolean { return this.authService.hasPermission(permission); }
}
