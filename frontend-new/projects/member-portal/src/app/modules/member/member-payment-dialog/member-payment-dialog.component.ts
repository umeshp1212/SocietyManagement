import { Component, Inject, NgZone } from '@angular/core';
import { CommonModule, CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDividerModule } from '@angular/material/divider';
import { MemberAuthService, PaymentOrderResponse } from '@core/services/member-auth.service';

declare var Razorpay: any;
declare var Cashfree: any;

export interface PaymentDialogData {
  unitId: number;
  unitNumber: string;
  ownerName: string;
  totalOutstanding: number;
  bill: any | null; // Specific bill or null for total outstanding
}

@Component({
  selector: 'app-member-payment-dialog',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    MatDialogModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatRadioModule,
    MatProgressSpinnerModule, MatDividerModule, CurrencyPipe
  ],
  template: `
    <div class="payment-dialog">
      <!-- Step 1: Choose Amount -->
      <div *ngIf="step === 'amount'">
        <h2 mat-dialog-title>
          <mat-icon color="primary">payment</mat-icon>
          Pay Maintenance
        </h2>

        <mat-dialog-content>
          <div class="payment-info">
            <div class="info-row">
              <span class="label">Flat Number</span>
              <span class="value">{{ data.unitNumber }}</span>
            </div>
            <div class="info-row">
              <span class="label">Owner</span>
              <span class="value">{{ data.ownerName }}</span>
            </div>
            <div class="info-row" *ngIf="data.bill">
              <span class="label">Bill Period</span>
              <span class="value">{{ data.bill.billPeriod }}</span>
            </div>
            <mat-divider></mat-divider>
            <div class="info-row highlight">
              <span class="label">{{ data.bill ? 'Bill Balance' : 'Total Outstanding' }}</span>
              <span class="value amount">
                {{ maxAmount | currency:'INR':'symbol':'1.0-0' }}
              </span>
            </div>
          </div>

          <div class="amount-selection">
            <label class="section-label">Payment Amount</label>
            <mat-radio-group [(ngModel)]="paymentType" (change)="onPaymentTypeChange()">
              <mat-radio-button value="full" class="radio-option">
                Pay Full Amount ({{ maxAmount | currency:'INR':'symbol':'1.0-0' }})
              </mat-radio-button>
              <mat-radio-button value="partial" class="radio-option">
                Pay Partial Amount
              </mat-radio-button>
            </mat-radio-group>

            <mat-form-field *ngIf="paymentType === 'partial'" appearance="outline"
                            class="full-width partial-field">
              <mat-label>Enter Amount</mat-label>
              <span matPrefix class="rupee-prefix">₹&nbsp;</span>
              <input matInput type="number" [(ngModel)]="partialAmount"
                     [min]="1" [max]="maxAmount" placeholder="Enter amount">
              <mat-hint>Min ₹1, Max {{ maxAmount | currency:'INR':'symbol':'1.0-0' }}</mat-hint>
              <mat-error *ngIf="partialAmount && partialAmount > maxAmount">
                Amount cannot exceed outstanding balance
              </mat-error>
            </mat-form-field>
          </div>

          <div class="error-message" *ngIf="errorMessage">
            <mat-icon>error</mat-icon> {{ errorMessage }}
          </div>
        </mat-dialog-content>

        <mat-dialog-actions align="end">
          <button mat-button mat-dialog-close>Cancel</button>
          <button mat-raised-button color="primary"
                  [disabled]="!isAmountValid() || loading"
                  (click)="onProceedToPayment()">
            <mat-spinner *ngIf="loading" diameter="18" class="btn-spinner"></mat-spinner>
            <mat-icon *ngIf="!loading">lock</mat-icon>
            {{ loading ? 'Creating order...' : 'Proceed to Pay ' + (getPayAmount() | currency:'INR':'symbol':'1.0-0') }}
          </button>
        </mat-dialog-actions>
      </div>

      <!-- Step 2: Processing -->
      <div *ngIf="step === 'processing'" class="processing-state">
        <mat-spinner diameter="48"></mat-spinner>
        <h3>Processing Payment</h3>
        <p>Please complete the payment in the Razorpay window.</p>
        <p class="sub-text">Do not close this dialog or refresh the page.</p>
      </div>

      <!-- Step 3: Success -->
      <div *ngIf="step === 'success'" class="success-state">
        <mat-icon class="success-icon">check_circle</mat-icon>
        <h3>Payment Successful!</h3>

        <!-- Discount Applied Info -->
        <div class="discount-applied" *ngIf="lastOrderData?.discountApplied">
          <mat-icon>local_offer</mat-icon>
          <span>{{ lastOrderData.discountPercent }}% discount applied!
            You saved {{ lastOrderData.discountAmount | currency:'INR':'symbol':'1.0-0' }}</span>
        </div>

        <div class="receipt-info">
          <div class="info-row" *ngIf="lastOrderData?.discountApplied">
            <span class="label">Original Amount</span>
            <span class="value strikethrough">{{ lastOrderData.originalAmount | currency:'INR':'symbol':'1.0-0' }}</span>
          </div>
          <div class="info-row">
            <span class="label">Amount Paid</span>
            <span class="value amount">{{ successData?.amount | currency:'INR':'symbol':'1.0-0' }}</span>
          </div>
          <div class="info-row">
            <span class="label">Receipt Number</span>
            <span class="value">{{ successData?.receiptNumber }}</span>
          </div>
          <div class="info-row" *ngIf="successData?.razorpayPaymentId">
            <span class="label">Payment ID</span>
            <span class="value small">{{ successData?.razorpayPaymentId }}</span>
          </div>
          <div class="info-row">
            <span class="label">Date</span>
            <span class="value">{{ successData?.paymentDate }}</span>
          </div>
        </div>
        <button mat-raised-button color="primary" (click)="dialogRef.close('success')">
          <mat-icon>done</mat-icon> Done
        </button>
      </div>

      <!-- Step 4: Failed -->
      <div *ngIf="step === 'failed'" class="failed-state">
        <mat-icon class="failed-icon">cancel</mat-icon>
        <h3>Payment Failed</h3>
        <p>{{ errorMessage || 'Something went wrong. Please try again.' }}</p>
        <div class="failed-actions">
          <button mat-button (click)="step = 'amount'; errorMessage = ''">
            <mat-icon>refresh</mat-icon> Try Again
          </button>
          <button mat-raised-button color="primary" (click)="dialogRef.close()">Close</button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .payment-dialog { min-width: 400px; }

    h2[mat-dialog-title] {
      display: flex; align-items: center; gap: 8px;
      margin: 0; padding: 16px 24px; font-size: 20px;
    }

    .payment-info { padding: 8px 0; }
    .info-row {
      display: flex; justify-content: space-between; align-items: center;
      padding: 8px 0;
    }
    .info-row .label { color: #666; font-size: 14px; }
    .info-row .value { font-weight: 500; font-size: 14px; }
    .info-row .value.amount { font-size: 20px; color: #e65100; font-weight: 600; }
    .info-row .value.small { font-size: 12px; color: #888; }
    .info-row.highlight { padding: 12px 0; }

    .amount-selection { margin-top: 16px; }
    .section-label { font-size: 14px; font-weight: 500; color: #333; margin-bottom: 8px; display: block; }
    .radio-option { display: block; margin: 8px 0; }
    .partial-field { margin-top: 12px; }
    .rupee-prefix { font-weight: 500; color: #333; }
    .full-width { width: 100%; }

    .error-message {
      display: flex; align-items: center; gap: 6px; color: #c62828;
      font-size: 13px; margin-top: 12px; padding: 8px;
      background: #ffebee; border-radius: 6px;
    }
    .error-message mat-icon { font-size: 18px; height: 18px; width: 18px; }

    .btn-spinner { display: inline-block; margin-right: 4px; }

    .processing-state, .success-state, .failed-state {
      display: flex; flex-direction: column; align-items: center;
      padding: 40px 24px; text-align: center;
    }
    .processing-state h3, .success-state h3, .failed-state h3 { margin: 16px 0 8px; }
    .processing-state p { color: #666; margin: 4px 0; }
    .processing-state .sub-text { font-size: 12px; color: #999; }

    .success-icon { font-size: 64px; height: 64px; width: 64px; color: #2e7d32; }
    .discount-applied {
      display: flex; align-items: center; gap: 8px;
      background: #e8f5e9; border-radius: 8px; padding: 10px 16px;
      margin-bottom: 16px; color: #2e7d32; font-size: 14px; font-weight: 500;
    }
    .discount-applied mat-icon { font-size: 20px; height: 20px; width: 20px; }
    .strikethrough { text-decoration: line-through; color: #999; }
    .failed-icon { font-size: 64px; height: 64px; width: 64px; color: #c62828; }

    .receipt-info {
      width: 100%; margin: 16px 0 24px;
      padding: 16px; background: #f5f5f5; border-radius: 8px;
    }

    .failed-state p { color: #666; }
    .failed-actions { display: flex; gap: 12px; margin-top: 16px; }

    @media (max-width: 500px) {
      .payment-dialog { min-width: unset; }
    }
  `]
})
export class MemberPaymentDialogComponent {
  step: 'amount' | 'processing' | 'success' | 'failed' = 'amount';
  paymentType: 'full' | 'partial' = 'full';
  partialAmount: number | null = null;
  maxAmount: number;
  loading = false;
  errorMessage = '';
  successData: any = null;
  lastOrderData: any = null;

  constructor(
    public dialogRef: MatDialogRef<MemberPaymentDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: PaymentDialogData,
    private memberAuth: MemberAuthService,
    private ngZone: NgZone
  ) {
    this.maxAmount = data.bill
      ? data.bill.balanceAmount
      : data.totalOutstanding;
  }

  onPaymentTypeChange(): void {
    if (this.paymentType === 'full') {
      this.partialAmount = null;
    }
  }

  getPayAmount(): number {
    if (this.paymentType === 'partial' && this.partialAmount) {
      return this.partialAmount;
    }
    return this.maxAmount;
  }

  isAmountValid(): boolean {
    const amount = this.getPayAmount();
    return amount > 0 && amount <= this.maxAmount;
  }

  onProceedToPayment(): void {
    if (!this.isAmountValid()) return;

    this.loading = true;
    this.errorMessage = '';

    const amount = this.getPayAmount();
    const billId = this.data.bill?.billId || undefined;

    this.memberAuth.createPaymentOrder(this.data.unitId, amount, billId).subscribe({
      next: (res) => {
        this.loading = false;
        if (res.success) {
          const orderData = res.data;
          this.lastOrderData = orderData;
          if (orderData.gateway === 'CASHFREE') {
            this.openCashfreeCheckout(orderData);
          } else {
            this.openRazorpayCheckout(orderData);
          }
        } else {
          this.errorMessage = res.message || 'Failed to create payment order.';
        }
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.error?.message || 'Failed to create payment order. Please try again.';
      }
    });
  }

  private openRazorpayCheckout(orderData: PaymentOrderResponse): void {
    this.step = 'processing';

    const options = {
      key: orderData.razorpayKeyId,
      amount: orderData.amount * 100, // Razorpay expects paise
      currency: orderData.currency,
      name: 'Society Management',
      description: orderData.description,
      order_id: orderData.razorpayOrderId,
      prefill: {
        name: orderData.ownerName,
        email: orderData.email || '',
        contact: orderData.phone || ''
      },
      notes: {
        unit_number: orderData.unitNumber,
        receipt: orderData.receipt
      },
      theme: {
        color: '#00796b'
      },
      modal: {
        ondismiss: () => {
          this.ngZone.run(() => {
            this.step = 'failed';
            this.errorMessage = 'Payment was cancelled. You can try again.';
          });
        }
      },
      handler: (response: any) => {
        this.ngZone.run(() => {
          this.onPaymentSuccess(response, orderData);
        });
      }
    };

    try {
      const rzp = new Razorpay(options);

      rzp.on('payment.failed', (response: any) => {
        this.ngZone.run(() => {
          this.step = 'failed';
          this.errorMessage = response.error?.description || 'Payment failed. Please try again.';
        });
      });

      rzp.open();
    } catch (e) {
      this.step = 'failed';
      this.errorMessage = 'Failed to open payment gateway. Please check your internet connection.';
    }
  }

  private onPaymentSuccess(response: any, orderData: PaymentOrderResponse): void {
    this.step = 'processing';

    const verifyData: any = {
      gateway: orderData.gateway,
      unitId: this.data.unitId,
      amount: orderData.amount,
      billId: this.data.bill?.billId || undefined,
      discountAmount: orderData.discountApplied ? orderData.discountAmount : 0
    };

    if (orderData.gateway === 'CASHFREE') {
      verifyData.cashfreeOrderId = orderData.cashfreeOrderId;
    } else {
      verifyData.razorpayOrderId = response.razorpay_order_id;
      verifyData.razorpayPaymentId = response.razorpay_payment_id;
      verifyData.razorpaySignature = response.razorpay_signature;
    }

    this.memberAuth.verifyPayment(verifyData).subscribe({
      next: (res) => {
        if (res.success) {
          this.successData = res.data;
          this.step = 'success';
        } else {
          this.step = 'failed';
          this.errorMessage = res.message || 'Payment verification failed.';
        }
      },
      error: (err) => {
        this.step = 'failed';
        this.errorMessage = err.error?.message
          || 'Payment was received but verification failed. Contact society admin.';
      }
    });
  }

  private openCashfreeCheckout(orderData: PaymentOrderResponse): void {
    this.step = 'processing';

    try {
      const cashfree = new Cashfree({ mode: 'sandbox' }); // Change to 'production' for live

      cashfree.checkout({
        paymentSessionId: orderData.cashfreePaymentSessionId,
        redirectTarget: '_modal',
      }).then((result: any) => {
        this.ngZone.run(() => {
          if (result.error) {
            this.step = 'failed';
            this.errorMessage = result.error?.message || 'Payment failed. Please try again.';
          } else if (result.paymentDetails) {
            this.onPaymentSuccess(result.paymentDetails, orderData);
          } else {
            // Payment may have completed — verify with backend
            this.onPaymentSuccess({}, orderData);
          }
        });
      }).catch((err: any) => {
        this.ngZone.run(() => {
          this.step = 'failed';
          this.errorMessage = 'Payment was cancelled or failed. You can try again.';
        });
      });

    } catch (e) {
      this.step = 'failed';
      this.errorMessage = 'Failed to open payment gateway. Please check your internet connection.';
    }
  }
}
