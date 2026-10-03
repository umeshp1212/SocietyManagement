import { Component, OnInit } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe, TitleCasePipe } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { animate, state, style, transition, trigger } from '@angular/animations';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MemberAuthService, MemberDashboard, MemberUnitInfo } from '@core/services/member-auth.service';
import { MemberPaymentDialogComponent } from '../member-payment-dialog/member-payment-dialog.component';

@Component({
  selector: 'app-member-dashboard',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterModule,
    MatCardModule, MatButtonModule, MatIconModule, MatTableModule,
    MatChipsModule, MatDividerModule, MatProgressSpinnerModule,
    MatSnackBarModule, MatSelectModule, MatFormFieldModule,
    MatInputModule, MatTabsModule, MatTooltipModule, MatDialogModule,
    CurrencyPipe, DatePipe, TitleCasePipe
  ],
  animations: [
    trigger('detailExpand', [
      state('collapsed,void', style({ height: '0px', minHeight: '0' })),
      state('expanded', style({ height: '*' })),
      transition('expanded <=> collapsed', animate('225ms cubic-bezier(0.4, 0.0, 0.2, 1)')),
    ]),
  ],
  template: `
    <div class="member-dashboard">
      <!-- Header -->
      <div class="dashboard-header">
        <div class="header-left">
          <mat-icon class="header-icon">apartment</mat-icon>
          <div>
            <h2>Welcome, {{ dashboard?.ownerName }}</h2>
            <p *ngIf="selectedUnit">
              {{ selectedUnit.wing }} Wing - Flat {{ selectedUnit.unitNumber }}
              <span *ngIf="selectedUnit.floor">, Floor {{ selectedUnit.floor }}</span>
            </p>
          </div>
        </div>
        <div class="header-right">
          <!-- Unit Selector (if multiple units) -->
          <mat-form-field *ngIf="units.length > 1" appearance="outline" class="unit-select">
            <mat-label>Select Flat</mat-label>
            <mat-select [(value)]="selectedUnit" (selectionChange)="onUnitChange($event.value)">
              <mat-option *ngFor="let unit of units" [value]="unit">
                {{ unit.wing }}-{{ unit.unitNumber }}
              </mat-option>
            </mat-select>
          </mat-form-field>
          <button mat-icon-button routerLink="/member/register-tenant" matTooltip="Register a Tenant" color="primary">
            <mat-icon>person_add</mat-icon>
          </button>
          <button mat-icon-button routerLink="/member/apply-noc" matTooltip="Apply for NOC" color="primary">
            <mat-icon>description</mat-icon>
          </button>
          <button mat-icon-button routerLink="/member/profile" matTooltip="My Profile" color="primary">
            <mat-icon>account_circle</mat-icon>
          </button>
          <button mat-icon-button (click)="logout()" matTooltip="Logout" color="warn">
            <mat-icon>logout</mat-icon>
          </button>
        </div>
      </div>

      <!-- Loading -->
      <div class="loading-container" *ngIf="loading">
        <mat-spinner diameter="40"></mat-spinner>
        <p>Loading your dashboard...</p>
      </div>

      <div *ngIf="!loading && dashboard">
        <!-- Discount Banner -->
        <div class="discount-banner" *ngIf="dashboard.discountEnabled && dashboard.discountEligible">
          <mat-icon>local_offer</mat-icon>
          <div class="discount-content">
            <strong>{{ dashboard.discountPercent }}% OFF</strong> — {{ dashboard.discountMessage }}
            <span class="discount-detail">Pay online within {{ dashboard.discountDueDays }} days of bill date</span>
          </div>
        </div>

        <!-- Summary Cards -->
        <div class="summary-cards">
          <mat-card class="summary-card outstanding">
            <mat-icon>account_balance_wallet</mat-icon>
            <div class="card-content">
              <span class="card-label">Total Outstanding</span>
              <span class="card-value">{{ dashboard.totalOutstanding | currency:'INR':'symbol':'1.0-0' }}</span>
              <span class="card-sub">{{ dashboard.outstandingBillCount }} pending bill(s)</span>
            </div>
          </mat-card>

          <mat-card class="summary-card paid">
            <mat-icon>check_circle</mat-icon>
            <div class="card-content">
              <span class="card-label">Total Paid</span>
              <span class="card-value">{{ dashboard.totalPaid | currency:'INR':'symbol':'1.0-0' }}</span>
              <span class="card-sub">All time payments</span>
            </div>
          </mat-card>

          <mat-card class="summary-card action" *ngIf="dashboard.totalOutstanding > 0">
            <mat-icon>payment</mat-icon>
            <div class="card-content">
              <span class="card-label">Pay Now</span>
              <button mat-raised-button color="primary" (click)="openPaymentDialog()">
                <mat-icon>credit_card</mat-icon> Pay Online
              </button>
            </div>
          </mat-card>

          <mat-card class="summary-card action no-due" *ngIf="dashboard.totalOutstanding <= 0">
            <mat-icon>celebration</mat-icon>
            <div class="card-content">
              <span class="card-label">No Dues</span>
              <span class="card-sub">All payments are up to date</span>
            </div>
          </mat-card>
        </div>

        <!-- Tabs: Outstanding Bills / Payment History -->
        <mat-card class="detail-card">
          <mat-tab-group animationDuration="200ms">
            <!-- Outstanding Bills Tab -->
            <mat-tab>
              <ng-template mat-tab-label>
                <mat-icon>receipt_long</mat-icon>&nbsp;Outstanding Bills
                <span class="badge" *ngIf="dashboard.outstandingBillCount > 0">
                  {{ dashboard.outstandingBillCount }}
                </span>
              </ng-template>

              <div class="tab-content">
                <div *ngIf="dashboard.outstandingBills.length === 0" class="empty-state">
                  <mat-icon>check_circle_outline</mat-icon>
                  <p>No outstanding bills. You're all caught up!</p>
                </div>

                <table mat-table [dataSource]="dashboard.outstandingBills" multiTemplateDataRows
                       *ngIf="dashboard.outstandingBills.length > 0" class="full-width">

                  <ng-container matColumnDef="period">
                    <th mat-header-cell *matHeaderCellDef>Period</th>
                    <td mat-cell *matCellDef="let bill">{{ bill.billPeriod }}</td>
                  </ng-container>

                  <ng-container matColumnDef="totalAmount">
                    <th mat-header-cell *matHeaderCellDef>Bill Amount</th>
                    <td mat-cell *matCellDef="let bill">
                      {{ bill.totalAmount | currency:'INR':'symbol':'1.0-0' }}
                    </td>
                  </ng-container>

                  <ng-container matColumnDef="paidAmount">
                    <th mat-header-cell *matHeaderCellDef>Paid</th>
                    <td mat-cell *matCellDef="let bill">
                      {{ (bill.paidAmount || 0) | currency:'INR':'symbol':'1.0-0' }}
                    </td>
                  </ng-container>

                  <ng-container matColumnDef="balanceAmount">
                    <th mat-header-cell *matHeaderCellDef>Balance</th>
                    <td mat-cell *matCellDef="let bill" class="balance-col">
                      {{ bill.balanceAmount | currency:'INR':'symbol':'1.0-0' }}
                    </td>
                  </ng-container>

                  <ng-container matColumnDef="status">
                    <th mat-header-cell *matHeaderCellDef>Status</th>
                    <td mat-cell *matCellDef="let bill">
                      <span class="status-chip" [class]="'status-' + bill.status.toLowerCase()">
                        {{ bill.status | titlecase }}
                      </span>
                    </td>
                  </ng-container>

                  <ng-container matColumnDef="action">
                    <th mat-header-cell *matHeaderCellDef>Action</th>
                    <td mat-cell *matCellDef="let bill">
                      <button mat-mini-fab color="primary"
                              (click)="openPaymentDialog(bill); $event.stopPropagation()"
                              matTooltip="Pay this bill">
                        <mat-icon>payment</mat-icon>
                      </button>
                    </td>
                  </ng-container>

                  <!-- Expanded detail row -->
                  <ng-container matColumnDef="expandedDetail">
                    <td mat-cell *matCellDef="let bill" [attr.colspan]="billColumns.length">
                      <div class="bill-detail"
                           [@detailExpand]="bill === expandedBill ? 'expanded' : 'collapsed'">
                        <div class="bill-breakdown" *ngIf="bill === expandedBill">

                          <!-- Line Items -->
                          <div class="breakdown-section" *ngIf="bill.lineItems?.length">
                            <h4>Current Month Charges</h4>
                            <div class="breakdown-row" *ngFor="let item of bill.lineItems">
                              <span class="breakdown-label">{{ item.chargeName }}</span>
                              <span class="breakdown-value">{{ item.amount | currency:'INR':'symbol':'1.0-0' }}</span>
                            </div>
                            <div class="breakdown-row subtotal">
                              <span class="breakdown-label">Subtotal</span>
                              <span class="breakdown-value">{{ bill.amount | currency:'INR':'symbol':'1.0-0' }}</span>
                            </div>
                          </div>

                          <!-- Arrears & Interest -->
                          <div class="breakdown-section"
                               *ngIf="(bill.previousArrears && bill.previousArrears > 0)
                                   || (bill.interestOnArrears && bill.interestOnArrears > 0)">
                            <h4>Arrears & Interest</h4>
                            <div class="breakdown-row" *ngIf="bill.previousArrears > 0">
                              <span class="breakdown-label">Principal Outstanding (Arrears)</span>
                              <span class="breakdown-value arrears">{{ bill.previousArrears | currency:'INR':'symbol':'1.0-0' }}</span>
                            </div>
                            <div class="breakdown-row" *ngIf="bill.interestOnArrears > 0">
                              <span class="breakdown-label">Interest on Arrears</span>
                              <span class="breakdown-value arrears">{{ bill.interestOnArrears | currency:'INR':'symbol':'1.0-0' }}</span>
                            </div>
                          </div>

                          <!-- Total Summary -->
                          <div class="breakdown-section summary">
                            <div class="breakdown-row total">
                              <span class="breakdown-label">Total Bill</span>
                              <span class="breakdown-value">{{ bill.totalAmount | currency:'INR':'symbol':'1.0-0' }}</span>
                            </div>
                            <div class="breakdown-row" *ngIf="bill.paidAmount > 0">
                              <span class="breakdown-label">Paid</span>
                              <span class="breakdown-value paid">- {{ bill.paidAmount | currency:'INR':'symbol':'1.0-0' }}</span>
                            </div>
                            <div class="breakdown-row total">
                              <span class="breakdown-label">Balance Due</span>
                              <span class="breakdown-value balance">{{ bill.balanceAmount | currency:'INR':'symbol':'1.0-0' }}</span>
                            </div>
                          </div>

                          <div class="bill-meta">
                            <span *ngIf="bill.billDate">Bill Date: {{ bill.billDate | date:'dd MMM yyyy' }}</span>
                            <span *ngIf="bill.dueDate">Due Date: {{ bill.dueDate | date:'dd MMM yyyy' }}</span>
                            <button mat-stroked-button color="primary" class="download-btn"
                                    (click)="downloadBill(bill); $event.stopPropagation()">
                              <mat-icon>download</mat-icon> Download Bill
                            </button>
                          </div>
                        </div>
                      </div>
                    </td>
                  </ng-container>

                  <tr mat-header-row *matHeaderRowDef="billColumns"></tr>
                  <tr mat-row *matRowDef="let row; columns: billColumns;"
                      class="bill-row"
                      [class.expanded-row]="expandedBill === row"
                      (click)="expandedBill = expandedBill === row ? null : row">
                  </tr>
                  <tr mat-row *matRowDef="let row; columns: ['expandedDetail']"
                      class="detail-row"></tr>
                </table>
              </div>
            </mat-tab>

            <!-- Payment History Tab -->
            <mat-tab>
              <ng-template mat-tab-label>
                <mat-icon>history</mat-icon>&nbsp;Payment History
              </ng-template>

              <div class="tab-content">
                <div *ngIf="dashboard.recentPayments.length === 0" class="empty-state">
                  <mat-icon>hourglass_empty</mat-icon>
                  <p>No payment records found.</p>
                </div>

                <table mat-table [dataSource]="dashboard.recentPayments"
                       *ngIf="dashboard.recentPayments.length > 0" class="full-width">

                  <ng-container matColumnDef="paymentDate">
                    <th mat-header-cell *matHeaderCellDef>Date</th>
                    <td mat-cell *matCellDef="let p">{{ p.paymentDate | date:'dd MMM yyyy' }}</td>
                  </ng-container>

                  <ng-container matColumnDef="amount">
                    <th mat-header-cell *matHeaderCellDef>Amount</th>
                    <td mat-cell *matCellDef="let p" class="amount-col">
                      {{ p.amount | currency:'INR':'symbol':'1.0-0' }}
                    </td>
                  </ng-container>

                  <ng-container matColumnDef="paymentMode">
                    <th mat-header-cell *matHeaderCellDef>Mode</th>
                    <td mat-cell *matCellDef="let p">{{ formatPaymentMode(p.paymentMode) }}</td>
                  </ng-container>

                  <ng-container matColumnDef="receiptNumber">
                    <th mat-header-cell *matHeaderCellDef>Receipt</th>
                    <td mat-cell *matCellDef="let p">{{ p.receiptNumber || '-' }}</td>
                  </ng-container>

                  <ng-container matColumnDef="paymentStatus">
                    <th mat-header-cell *matHeaderCellDef>Status</th>
                    <td mat-cell *matCellDef="let p">
                      <span class="status-chip"
                            [class]="'status-' + (p.status || 'pending').toLowerCase()">
                        {{ p.status || 'Pending' }}
                      </span>
                    </td>
                  </ng-container>

                  <ng-container matColumnDef="receipt">
                    <th mat-header-cell *matHeaderCellDef></th>
                    <td mat-cell *matCellDef="let p">
                      <button mat-icon-button color="primary"
                              (click)="downloadReceipt(p)"
                              matTooltip="Download Receipt"
                              *ngIf="p.status === 'SUCCESS' || p.status === 'VERIFIED'">
                        <mat-icon>download</mat-icon>
                      </button>
                    </td>
                  </ng-container>

                  <tr mat-header-row *matHeaderRowDef="paymentColumns"></tr>
                  <tr mat-row *matRowDef="let row; columns: paymentColumns;"></tr>
                </table>
              </div>
            </mat-tab>
          </mat-tab-group>
        </mat-card>
      </div>

      <!-- Error State -->
      <div *ngIf="!loading && errorMessage" class="error-container">
        <mat-icon>error_outline</mat-icon>
        <p>{{ errorMessage }}</p>
        <button mat-raised-button color="primary" (click)="loadDashboard()">
          <mat-icon>refresh</mat-icon> Retry
        </button>
      </div>
    </div>
  `,
  styles: [`
    .member-dashboard { padding: 24px; max-width: 1100px; margin: 0 auto; }

    .dashboard-header {
      display: flex; justify-content: space-between; align-items: center;
      margin-bottom: 24px; flex-wrap: wrap; gap: 16px;
    }
    .header-left { display: flex; align-items: center; gap: 12px; }
    .header-left h2 { margin: 0; color: #00796b; }
    .header-left p { margin: 2px 0 0; color: #666; font-size: 14px; }
    .header-icon { font-size: 40px; height: 40px; width: 40px; color: #00796b; }
    .header-right { display: flex; align-items: center; gap: 12px; }
    .unit-select { width: 160px; font-size: 14px; }

    .loading-container {
      display: flex; flex-direction: column; align-items: center;
      justify-content: center; padding: 60px 0; gap: 16px; color: #666;
    }

    .summary-cards {
      display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: 16px; margin-bottom: 24px;
    }

    .discount-banner {
      display: flex; align-items: center; gap: 12px;
      background: linear-gradient(135deg, #e8f5e9, #c8e6c9); border-left: 4px solid #2e7d32;
      border-radius: 8px; padding: 14px 20px; margin-bottom: 16px;
    }
    .discount-banner mat-icon { color: #2e7d32; font-size: 28px; height: 28px; width: 28px; }
    .discount-banner strong { color: #2e7d32; font-size: 16px; }
    .discount-content { display: flex; flex-direction: column; font-size: 14px; color: #333; }
    .discount-detail { font-size: 12px; color: #666; margin-top: 2px; }
    .summary-card {
      display: flex; align-items: center; gap: 16px; padding: 20px 24px;
      border-radius: 12px; border-left: 4px solid;
    }
    .summary-card mat-icon { font-size: 36px; height: 36px; width: 36px; }
    .summary-card.outstanding { border-color: #e65100; }
    .summary-card.outstanding mat-icon { color: #e65100; }
    .summary-card.paid { border-color: #2e7d32; }
    .summary-card.paid mat-icon { color: #2e7d32; }
    .summary-card.action { border-color: #1565c0; }
    .summary-card.action mat-icon { color: #1565c0; }
    .summary-card.no-due { border-color: #2e7d32; }
    .summary-card.no-due mat-icon { color: #2e7d32; }

    .card-content { display: flex; flex-direction: column; }
    .card-label { font-size: 13px; color: #888; text-transform: uppercase; letter-spacing: 0.5px; }
    .card-value { font-size: 26px; font-weight: 600; color: #333; margin: 4px 0; }
    .card-sub { font-size: 12px; color: #999; }

    .detail-card { border-radius: 12px; overflow: hidden; }
    .tab-content { padding: 16px 0; }

    .empty-state {
      display: flex; flex-direction: column; align-items: center;
      padding: 40px 0; color: #999;
    }
    .empty-state mat-icon { font-size: 48px; height: 48px; width: 48px; margin-bottom: 8px; }

    table { width: 100%; }
    th.mat-mdc-header-cell { font-weight: 600; color: #555; font-size: 13px; }
    .balance-col { font-weight: 600; color: #e65100; }
    .amount-col { font-weight: 600; color: #2e7d32; }

    .status-chip {
      display: inline-block; padding: 3px 10px; border-radius: 12px;
      font-size: 12px; font-weight: 500;
    }
    .status-unpaid, .status-overdue { background: #ffebee; color: #c62828; }
    .status-partially_paid { background: #fff3e0; color: #e65100; }
    .status-paid, .status-success, .status-verified { background: #e8f5e9; color: #2e7d32; }
    .status-pending { background: #fff8e1; color: #f57f17; }
    .status-failed { background: #ffebee; color: #c62828; }

    .badge {
      display: inline-flex; align-items: center; justify-content: center;
      background: #e65100; color: white; border-radius: 50%;
      min-width: 20px; height: 20px; font-size: 11px; margin-left: 6px;
      padding: 0 4px;
    }

    .error-container {
      display: flex; flex-direction: column; align-items: center;
      padding: 60px 0; color: #c62828; gap: 12px;
    }
    .error-container mat-icon { font-size: 48px; height: 48px; width: 48px; }

    /* Expandable bill detail */
    .bill-row { cursor: pointer; }
    .bill-row:hover { background: #f5f5f5; }
    .expanded-row { background: #e8f5e9; }
    .detail-row { height: 0; }
    .bill-detail { overflow: hidden; }
    .bill-breakdown { padding: 16px 24px 20px; }
    .breakdown-section { margin-bottom: 16px; }
    .breakdown-section h4 { font-size: 13px; color: #1565c0; text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 8px; }
    .breakdown-row { display: flex; justify-content: space-between; padding: 4px 0; font-size: 14px; }
    .breakdown-label { color: #555; }
    .breakdown-value { font-weight: 500; color: #333; }
    .breakdown-value.arrears { color: #e65100; }
    .breakdown-value.paid { color: #2e7d32; }
    .breakdown-value.balance { color: #c62828; font-weight: 600; }
    .breakdown-row.subtotal { border-top: 1px solid #e0e0e0; padding-top: 6px; margin-top: 4px; font-weight: 500; }
    .breakdown-row.total { border-top: 1px solid #bdbdbd; padding-top: 8px; margin-top: 4px; font-weight: 600; font-size: 15px; }
    .breakdown-section.summary { background: #fafafa; padding: 12px; border-radius: 8px; }
    .bill-meta { display: flex; gap: 24px; font-size: 12px; color: #888; margin-top: 8px; align-items: center; }
    .download-btn { font-size: 12px; height: 32px; line-height: 32px; margin-left: auto; }

    @media (max-width: 600px) {
      .member-dashboard { padding: 12px; }
      .summary-cards { grid-template-columns: 1fr; }
      .dashboard-header { flex-direction: column; align-items: flex-start; }
      .bill-breakdown { padding: 12px 8px; }
    }
  `]
})
export class MemberDashboardComponent implements OnInit {
  dashboard: MemberDashboard | null = null;
  units: MemberUnitInfo[] = [];
  selectedUnit: MemberUnitInfo | null = null;
  loading = true;
  errorMessage = '';

  billColumns = ['period', 'totalAmount', 'paidAmount', 'balanceAmount', 'status', 'action'];
  paymentColumns = ['paymentDate', 'amount', 'paymentMode', 'receiptNumber', 'paymentStatus', 'receipt'];
  expandedBill: any = null;

  constructor(
    private memberAuth: MemberAuthService,
    private router: Router,
    private snackBar: MatSnackBar,
    private dialog: MatDialog
  ) {}

  ngOnInit(): void {
    const member = this.memberAuth.getCurrentMember();
    if (!member) {
      this.router.navigate(['/member-login']);
      return;
    }

    this.units = member.units || [];
    this.selectedUnit = this.memberAuth.getSelectedUnit();

    if (this.selectedUnit) {
      this.loadDashboard();
    } else if (this.units.length > 0) {
      this.selectedUnit = this.units[0];
      this.memberAuth.selectUnit(this.units[0]);
      this.loadDashboard();
    }
  }

  loadDashboard(): void {
    if (!this.selectedUnit) return;

    this.loading = true;
    this.errorMessage = '';

    this.memberAuth.getDashboard(this.selectedUnit.unitId).subscribe({
      next: (res) => {
        this.loading = false;
        if (res.success) {
          this.dashboard = res.data;
        }
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.error?.message || 'Failed to load dashboard. Please try again.';
      }
    });
  }

  onUnitChange(unit: MemberUnitInfo): void {
    this.selectedUnit = unit;
    this.memberAuth.selectUnit(unit);
    this.loadDashboard();
  }

  openPaymentDialog(bill?: any): void {
    if (!this.selectedUnit || !this.dashboard) return;

    const dialogRef = this.dialog.open(MemberPaymentDialogComponent, {
      width: '480px',
      disableClose: true,
      data: {
        unitId: this.selectedUnit.unitId,
        unitNumber: this.selectedUnit.unitNumber,
        ownerName: this.dashboard.ownerName,
        totalOutstanding: this.dashboard.totalOutstanding,
        bill: bill || null
      }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result === 'success') {
        this.loadDashboard();
        this.snackBar.open('Payment successful! Thank you.', 'Close', { duration: 5000 });
      }
    });
  }

  formatPaymentMode(mode: string): string {
    if (!mode) return '-';
    const map: Record<string, string> = {
      RAZORPAY: 'Online (Razorpay)',
      CASHFREE_LINK: 'Online (Cashfree)',
      CASHFREE_QR: 'QR Code',
      UPI: 'UPI',
      GPAY: 'Google Pay',
      PHONEPE: 'PhonePe',
      NEFT: 'NEFT',
      RTGS: 'RTGS',
      IMPS: 'IMPS',
      CHEQUE: 'Cheque',
      CASH: 'Cash',
      BANK_TRANSFER: 'Bank Transfer'
    };
    return map[mode] || mode;
  }

  downloadBill(bill: any): void {
    if (!this.selectedUnit) return;
    this.memberAuth.downloadBillPdf(this.selectedUnit.unitId, bill.billId);
  }

  downloadReceipt(payment: any): void {
    if (!this.selectedUnit) return;
    this.memberAuth.downloadReceiptPdf(this.selectedUnit.unitId, payment.paymentId);
  }

  logout(): void {
    this.memberAuth.logout();
  }
}
