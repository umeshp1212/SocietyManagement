import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MaintenanceService } from '@core/services/maintenance.service';

@Component({
  selector: 'app-payment-history',
  standalone: true,
  imports: [CommonModule, RouterModule, MatTableModule, MatPaginatorModule,
    MatCardModule, MatButtonModule, MatIconModule, MatChipsModule],
  template: `
    <div class="container">
      <div class="page-header">
        <h2>Payment History - Unit {{ unitId }}</h2>
        <a mat-button routerLink="/maintenance">
          <mat-icon>arrow_back</mat-icon> Back to Bills
        </a>
      </div>

      <table mat-table [dataSource]="payments" class="mat-elevation-z2">
        <ng-container matColumnDef="paymentDate">
          <th mat-header-cell *matHeaderCellDef>Date</th>
          <td mat-cell *matCellDef="let p">{{ p.paymentDate | date:'mediumDate' }}</td>
        </ng-container>
        <ng-container matColumnDef="amount">
          <th mat-header-cell *matHeaderCellDef>Amount</th>
          <td mat-cell *matCellDef="let p">{{ p.amount | currency:'INR' }}</td>
        </ng-container>
        <ng-container matColumnDef="paymentMode">
          <th mat-header-cell *matHeaderCellDef>Mode</th>
          <td mat-cell *matCellDef="let p">{{ p.paymentMode }}</td>
        </ng-container>
        <ng-container matColumnDef="transactionId">
          <th mat-header-cell *matHeaderCellDef>Transaction ID</th>
          <td mat-cell *matCellDef="let p">{{ p.transactionId || '-' }}</td>
        </ng-container>
        <ng-container matColumnDef="receiptNumber">
          <th mat-header-cell *matHeaderCellDef>Receipt No</th>
          <td mat-cell *matCellDef="let p">{{ p.receiptNumber || '-' }}</td>
        </ng-container>
        <ng-container matColumnDef="payerName">
          <th mat-header-cell *matHeaderCellDef>Payer</th>
          <td mat-cell *matCellDef="let p">{{ p.payerName || '-' }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let p">
            <span class="status-badge" [ngClass]="p.status?.toLowerCase()">{{ p.status }}</span>
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
    table { width: 100%; }
    .status-badge { padding: 4px 8px; border-radius: 12px; font-size: 12px; font-weight: 500; }
    .status-badge.success, .status-badge.completed { background: #e8f5e9; color: #2e7d32; }
    .status-badge.pending { background: #fff3e0; color: #e65100; }
    .status-badge.failed { background: #ffebee; color: #b71c1c; }
  `]
})
export class PaymentHistoryComponent implements OnInit {
  payments: any[] = [];
  displayedColumns = ['paymentDate', 'amount', 'paymentMode', 'transactionId', 'receiptNumber', 'payerName', 'status'];
  totalElements = 0;
  pageSize = 20;
  currentPage = 0;
  unitId!: number;

  constructor(private maintenanceService: MaintenanceService, private route: ActivatedRoute) {}

  ngOnInit(): void {
    this.unitId = +this.route.snapshot.paramMap.get('unitId')!;
    this.loadPayments();
  }

  loadPayments(): void {
    this.maintenanceService.getPaymentsByUnit(this.unitId, this.currentPage, this.pageSize)
      .subscribe(res => {
        if (res.success) {
          this.payments = res.data.content;
          this.totalElements = res.data.totalElements;
        }
      });
  }

  onPageChange(event: PageEvent): void {
    this.currentPage = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadPayments();
  }
}
