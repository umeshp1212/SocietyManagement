import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatTabsModule } from '@angular/material/tabs';
import { MaintenanceService } from '@core/services/maintenance.service';

@Component({
  selector: 'app-payment-history',
  standalone: true,
  imports: [CommonModule, RouterModule, MatTableModule, MatPaginatorModule,
    MatCardModule, MatButtonModule, MatIconModule, MatChipsModule, MatTabsModule],
  template: `
    <div class="container">
      <div class="page-header">
        <h2>Payment History - Unit {{ unitId }}</h2>
        <a mat-button routerLink="/maintenance">
          <mat-icon>arrow_back</mat-icon> Back to Bills
        </a>
      </div>

      <mat-tab-group>
        <mat-tab label="Payments">
          <div class="tab-body">
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
        </mat-tab>

        <mat-tab label="Audit Ledger">
          <div class="tab-body">
            <table mat-table [dataSource]="ledger" class="mat-elevation-z2" *ngIf="ledger.length > 0">
              <ng-container matColumnDef="performedOn">
                <th mat-header-cell *matHeaderCellDef>When</th>
                <td mat-cell *matCellDef="let e">{{ e.performedOn | date:'medium' }}</td>
              </ng-container>
              <ng-container matColumnDef="entryType">
                <th mat-header-cell *matHeaderCellDef>Event</th>
                <td mat-cell *matCellDef="let e">{{ formatEntryType(e.entryType) }}</td>
              </ng-container>
              <ng-container matColumnDef="billId">
                <th mat-header-cell *matHeaderCellDef>Bill</th>
                <td mat-cell *matCellDef="let e">
                  <a *ngIf="e.billId" [routerLink]="['/maintenance/bill', e.billId]">#{{ e.billId }}</a>
                  <span *ngIf="!e.billId">-</span>
                </td>
              </ng-container>
              <ng-container matColumnDef="amount">
                <th mat-header-cell *matHeaderCellDef>Amount</th>
                <td mat-cell *matCellDef="let e" [class.negative]="e.amount < 0">{{ e.amount | currency:'INR' }}</td>
              </ng-container>
              <ng-container matColumnDef="balanceAfter">
                <th mat-header-cell *matHeaderCellDef>Balance After</th>
                <td mat-cell *matCellDef="let e">{{ e.balanceAfter | currency:'INR' }}</td>
              </ng-container>
              <ng-container matColumnDef="source">
                <th mat-header-cell *matHeaderCellDef>Source</th>
                <td mat-cell *matCellDef="let e">{{ e.source }}</td>
              </ng-container>
              <ng-container matColumnDef="performedBy">
                <th mat-header-cell *matHeaderCellDef>By</th>
                <td mat-cell *matCellDef="let e">{{ e.performedBy }}</td>
              </ng-container>
              <ng-container matColumnDef="reason">
                <th mat-header-cell *matHeaderCellDef>Reason</th>
                <td mat-cell *matCellDef="let e">{{ e.reason || '-' }}</td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="ledgerColumns"></tr>
              <tr mat-row *matRowDef="let row; columns: ledgerColumns;"></tr>
            </table>
            <p *ngIf="ledger.length === 0" class="no-data">No ledger entries for this unit.</p>
          </div>
        </mat-tab>
      </mat-tab-group>
    </div>
  `,
  styles: [`
    .container { padding: 24px; }
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .tab-body { padding-top: 16px; }
    table { width: 100%; }
    .status-badge { padding: 4px 8px; border-radius: 12px; font-size: 12px; font-weight: 500; }
    .status-badge.success, .status-badge.completed, .status-badge.verified { background: #e8f5e9; color: #2e7d32; }
    .status-badge.pending { background: #fff3e0; color: #e65100; }
    .status-badge.failed { background: #ffebee; color: #b71c1c; }
    .status-badge.reversed { background: #eceff1; color: #455a64; }
    .negative { color: #c62828; }
    .no-data { text-align: center; color: #666; padding: 24px; }
  `]
})
export class PaymentHistoryComponent implements OnInit {
  payments: any[] = [];
  displayedColumns = ['paymentDate', 'amount', 'paymentMode', 'transactionId', 'receiptNumber', 'payerName', 'status'];
  ledger: any[] = [];
  ledgerColumns = ['performedOn', 'entryType', 'billId', 'amount', 'balanceAfter', 'source', 'performedBy', 'reason'];
  totalElements = 0;
  pageSize = 20;
  currentPage = 0;
  unitId!: number;

  constructor(private maintenanceService: MaintenanceService, private route: ActivatedRoute) {}

  ngOnInit(): void {
    this.unitId = +this.route.snapshot.paramMap.get('unitId')!;
    this.loadPayments();
    this.loadLedger();
  }

  loadLedger(): void {
    this.maintenanceService.getLedgerByUnit(this.unitId).subscribe(res => {
      if (res.success) {
        this.ledger = res.data || [];
      }
    });
  }

  formatEntryType(t: string): string {
    switch (t) {
      case 'BILL_GENERATED': return 'Bill Generated';
      case 'PAYMENT_APPLIED': return 'Payment Applied';
      case 'PAYMENT_REVERSED': return 'Payment Reversed';
      default: return t;
    }
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
