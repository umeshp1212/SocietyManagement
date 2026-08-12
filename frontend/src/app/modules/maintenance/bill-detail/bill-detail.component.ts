import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatTableModule } from '@angular/material/table';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MaintenanceService } from '@core/services/maintenance.service';

@Component({
  selector: 'app-bill-detail',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, MatCardModule, MatButtonModule,
    MatIconModule, MatFormFieldModule, MatSelectModule, MatInputModule, MatTableModule,
    MatChipsModule, MatDividerModule],
  template: `
    <div class="container" *ngIf="bill">
      <div class="page-header">
        <h2>Maintenance Bill - {{ bill.billPeriod || (bill.billMonth + '/' + bill.billYear) }}</h2>
        <div class="header-actions">
          <button mat-raised-button color="accent" (click)="downloadPdf()">
            <mat-icon>download</mat-icon> Download PDF
          </button>
          <a mat-button routerLink="/maintenance">
            <mat-icon>arrow_back</mat-icon> Back to Bills
          </a>
        </div>
      </div>

      <!-- Bill Info Header -->
      <mat-card class="info-card">
        <mat-card-content>
          <div class="info-grid">
            <div class="info-item"><span class="label">Unit No</span><span class="value">{{ bill.unitNumber }}</span></div>
            <div class="info-item"><span class="label">Owner</span><span class="value">{{ bill.ownerName }}</span></div>
            <div class="info-item"><span class="label">Area (Sq.Ft)</span><span class="value">{{ bill.unitAreaSqft || '-' }}</span></div>
            <div class="info-item"><span class="label">Bill Period</span><span class="value">{{ bill.billPeriod }}</span></div>
            <div class="info-item"><span class="label">Bill Date</span><span class="value">{{ bill.billDate | date:'mediumDate' }}</span></div>
            <div class="info-item"><span class="label">Due Date</span><span class="value">{{ bill.dueDate | date:'mediumDate' }}</span></div>
            <div class="info-item">
              <span class="label">Status</span>
              <span class="status-badge" [ngClass]="bill.status?.toLowerCase()">{{ bill.status }}</span>
            </div>
          </div>
        </mat-card-content>
      </mat-card>

      <!-- Charges Breakup Table -->
      <mat-card class="charges-card">
        <mat-card-header><mat-card-title>Charges Breakup</mat-card-title></mat-card-header>
        <mat-card-content>
          <table class="charges-table">
            <thead>
              <tr>
                <th class="sr-col">Sr.</th>
                <th class="desc-col">Description</th>
                <th class="calc-col">Calculation</th>
                <th class="amt-col">Amount (Rs.)</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let item of bill.lineItems; let i = index">
                <td class="sr-col">{{ i + 1 }}</td>
                <td class="desc-col">{{ item.chargeName }}</td>
                <td class="calc-col">
                  <span *ngIf="item.calculationType === 'AREA_BASED'">
                    {{ item.areaSqft }} sq.ft x Rs.{{ item.rate }}
                  </span>
                  <span *ngIf="item.calculationType === 'FLAT'">
                    Flat Charge
                  </span>
                </td>
                <td class="amt-col">{{ item.amount | number:'1.2-2' }}</td>
              </tr>
            </tbody>
            <tfoot>
              <tr class="subtotal-row">
                <td colspan="3" class="text-right"><strong>Current Month Charges</strong></td>
                <td class="amt-col"><strong>{{ bill.amount | number:'1.2-2' }}</strong></td>
              </tr>
              <tr *ngIf="bill.previousArrears > 0" class="arrears-row">
                <td colspan="3" class="text-right">Previous Arrears (Principal)</td>
                <td class="amt-col">{{ bill.previousArrears | number:'1.2-2' }}</td>
              </tr>
              <tr *ngIf="bill.interestOnArrears > 0" class="arrears-row">
                <td colspan="3" class="text-right">Interest on Arrears (1% per month)</td>
                <td class="amt-col">{{ bill.interestOnArrears | number:'1.2-2' }}</td>
              </tr>
              <tr class="total-row">
                <td colspan="3" class="text-right"><strong>Grand Total</strong></td>
                <td class="amt-col"><strong>{{ bill.totalAmount | number:'1.2-2' }}</strong></td>
              </tr>
              <tr *ngIf="bill.paidAmount > 0" class="paid-row">
                <td colspan="3" class="text-right">Paid Amount</td>
                <td class="amt-col">- {{ bill.paidAmount | number:'1.2-2' }}</td>
              </tr>
              <tr class="balance-row">
                <td colspan="3" class="text-right"><strong>Balance Due</strong></td>
                <td class="amt-col"><strong>{{ bill.balanceAmount | number:'1.2-2' }}</strong></td>
              </tr>
            </tfoot>
          </table>
        </mat-card-content>
      </mat-card>

      <!-- QR Code & Payment Actions -->
      <div class="actions-row">
        <mat-card class="qr-card">
          <mat-card-header><mat-card-title>Payment QR Code</mat-card-title></mat-card-header>
          <mat-card-content>
            <img *ngIf="qrCodeBase64" [src]="qrCodeBase64" alt="Payment QR Code" class="qr-image">
            <p *ngIf="!qrCodeBase64" class="loading-text">Loading QR code...</p>
          </mat-card-content>
        </mat-card>

        <mat-card class="links-card">
          <mat-card-header><mat-card-title>Payment Actions</mat-card-title></mat-card-header>
          <mat-card-content>
            <div class="action-buttons">
              <button mat-raised-button color="primary" (click)="copyPaymentLink()">
                <mat-icon>content_copy</mat-icon> Copy Payment Link
              </button>
              <span *ngIf="paymentLink" class="link-text">{{ paymentLink }}</span>

              <button mat-raised-button color="accent" (click)="shareWhatsApp()">
                <mat-icon>share</mat-icon> Share via WhatsApp
              </button>
            </div>
          </mat-card-content>
        </mat-card>
      </div>

      <!-- Record Offline Payment -->
      <mat-card class="payment-form-card" *ngIf="bill.status !== 'PAID'">
        <mat-card-header><mat-card-title>Record Offline Payment</mat-card-title></mat-card-header>
        <mat-card-content>
          <form class="payment-form" (ngSubmit)="recordPayment()">
            <mat-form-field appearance="outline">
              <mat-label>Amount</mat-label>
              <input matInput type="number" [(ngModel)]="paymentForm.amount" name="amount" required>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Payment Date</mat-label>
              <input matInput type="date" [(ngModel)]="paymentForm.paymentDate" name="paymentDate" required>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Payment Mode</mat-label>
              <mat-select [(ngModel)]="paymentForm.paymentMode" name="paymentMode" required>
                <mat-option value="CASH">Cash</mat-option>
                <mat-option value="CHEQUE">Cheque</mat-option>
                <mat-option value="BANK_TRANSFER">Bank Transfer</mat-option>
                <mat-option value="UPI">UPI</mat-option>
                <mat-option value="NEFT">NEFT</mat-option>
                <mat-option value="RTGS">RTGS</mat-option>
                <mat-option value="IMPS">IMPS</mat-option>
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Transaction ID</mat-label>
              <input matInput [(ngModel)]="paymentForm.transactionId" name="transactionId">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Payer Name</mat-label>
              <input matInput [(ngModel)]="paymentForm.payerName" name="payerName">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Remarks</mat-label>
              <input matInput [(ngModel)]="paymentForm.remarks" name="remarks">
            </mat-form-field>
            <button mat-raised-button color="primary" type="submit">
              <mat-icon>payment</mat-icon> Submit Payment
            </button>
          </form>
        </mat-card-content>
      </mat-card>

      <!-- Payment History for this Bill -->
      <mat-card class="history-card">
        <mat-card-header><mat-card-title>Payment History</mat-card-title></mat-card-header>
        <mat-card-content>
          <table mat-table [dataSource]="payments" class="mat-elevation-z1" *ngIf="payments.length > 0">
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
            <ng-container matColumnDef="receiptNumber">
              <th mat-header-cell *matHeaderCellDef>Receipt #</th>
              <td mat-cell *matCellDef="let p">{{ p.receiptNumber || '-' }}</td>
            </ng-container>
            <ng-container matColumnDef="transactionId">
              <th mat-header-cell *matHeaderCellDef>Transaction ID</th>
              <td mat-cell *matCellDef="let p">{{ p.transactionId || '-' }}</td>
            </ng-container>
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>Status</th>
              <td mat-cell *matCellDef="let p">
                <span class="status-badge" [ngClass]="p.status?.toLowerCase()">{{ p.status }}</span>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="paymentColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: paymentColumns;"></tr>
          </table>
          <p *ngIf="payments.length === 0" class="no-data">No payments recorded yet.</p>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .container { padding: 24px; max-width: 1100px; margin: 0 auto; }
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .header-actions { display: flex; gap: 8px; align-items: center; }
    .info-card { margin-bottom: 24px; }
    .info-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 16px; }
    .info-item { display: flex; flex-direction: column; }
    .info-item .label { font-size: 12px; color: #666; text-transform: uppercase; }
    .info-item .value { font-size: 16px; font-weight: 500; margin-top: 4px; }

    /* Charges Breakup Table */
    .charges-card { margin-bottom: 24px; }
    .charges-table { width: 100%; border-collapse: collapse; margin-top: 8px; }
    .charges-table th, .charges-table td { padding: 10px 12px; border: 1px solid #e0e0e0; }
    .charges-table thead { background: #f5f5f5; }
    .charges-table th { font-weight: 600; text-align: left; font-size: 13px; }
    .sr-col { width: 50px; text-align: center; }
    .desc-col { }
    .calc-col { width: 200px; color: #666; font-size: 13px; }
    .amt-col { width: 130px; text-align: right; }
    .text-right { text-align: right; }
    .subtotal-row td { background: #f9f9f9; border-top: 2px solid #ccc; }
    .arrears-row td { background: #fff8e1; }
    .total-row td { background: #e3f2fd; border-top: 2px solid #1976d2; font-size: 15px; }
    .paid-row td { background: #e8f5e9; }
    .balance-row td { background: #fff3e0; border-top: 1px solid #f57c00; font-size: 15px; }

    .actions-row { display: flex; gap: 24px; margin-bottom: 24px; flex-wrap: wrap; }
    .qr-card, .links-card { flex: 1; min-width: 280px; }
    .qr-image { max-width: 200px; display: block; margin: 0 auto; }
    .loading-text { text-align: center; color: #666; }
    .action-buttons { display: flex; flex-direction: column; gap: 12px; }
    .link-text { font-size: 12px; color: #666; word-break: break-all; }
    .payment-form-card { margin-bottom: 24px; }
    .payment-form { display: grid; grid-template-columns: 1fr 1fr; gap: 8px 16px; }
    .payment-form mat-form-field { width: 100%; }
    .payment-form button { grid-column: 1 / -1; justify-self: start; }
    .history-card { margin-bottom: 24px; }
    .history-card table { width: 100%; }
    .no-data { text-align: center; color: #666; padding: 16px; }
    .status-badge { padding: 4px 8px; border-radius: 12px; font-size: 12px; font-weight: 500; }
    .status-badge.paid { background: #e8f5e9; color: #2e7d32; }
    .status-badge.unpaid { background: #fbe9e7; color: #c62828; }
    .status-badge.partially_paid { background: #fff3e0; color: #e65100; }
    .status-badge.overdue { background: #ffebee; color: #b71c1c; }
    .status-badge.success, .status-badge.verified { background: #e8f5e9; color: #2e7d32; }
    .status-badge.pending { background: #fff3e0; color: #e65100; }
    .status-badge.failed { background: #ffebee; color: #b71c1c; }
  `]
})
export class BillDetailComponent implements OnInit {
  bill: any = null;
  qrCodeBase64: string | null = null;
  paymentLink: string | null = null;
  payments: any[] = [];
  paymentColumns = ['paymentDate', 'amount', 'paymentMode', 'receiptNumber', 'transactionId', 'status'];
  billId!: number;

  paymentForm = {
    amount: null as number | null,
    paymentDate: '',
    paymentMode: '',
    transactionId: '',
    payerName: '',
    remarks: ''
  };

  constructor(private maintenanceService: MaintenanceService, private route: ActivatedRoute) {}

  ngOnInit(): void {
    this.billId = +this.route.snapshot.paramMap.get('id')!;
    this.loadBill();
    this.loadQrCode();
    this.loadPayments();
  }

  loadBill(): void {
    this.maintenanceService.getBillById(this.billId).subscribe(res => {
      if (res.success) {
        this.bill = res.data;
      }
    });
  }

  loadQrCode(): void {
    this.maintenanceService.getQrCode(this.billId).subscribe(res => {
      if (res.success && res.data?.qrCode) {
        this.qrCodeBase64 = 'data:image/png;base64,' + res.data.qrCode;
      }
    });
  }

  loadPayments(): void {
    this.maintenanceService.getPaymentsByBill(this.billId).subscribe(res => {
      if (res.success) {
        this.payments = res.data || [];
      }
    });
  }

  copyPaymentLink(): void {
    this.maintenanceService.generatePaymentLink(this.billId).subscribe(res => {
      if (res.success) {
        this.paymentLink = res.data?.paymentLink;
        if (this.paymentLink) {
          navigator.clipboard.writeText(this.paymentLink);
        }
      }
    });
  }

  shareWhatsApp(): void {
    this.maintenanceService.getWhatsAppLink(this.billId).subscribe(res => {
      if (res.success && res.data?.whatsappLink) {
        window.open(res.data.whatsappLink, '_blank');
      }
    });
  }

  recordPayment(): void {
    const request = {
      billId: this.billId,
      ...this.paymentForm
    };
    this.maintenanceService.recordOfflinePayment(request).subscribe(res => {
      if (res.success) {
        this.loadBill();
        this.loadPayments();
        this.paymentForm = { amount: null, paymentDate: '', paymentMode: '', transactionId: '', payerName: '', remarks: '' };
      }
    });
  }

  downloadPdf(): void {
    this.maintenanceService.downloadBillPdf(this.billId);
  }
}
