import { Component, Inject, OnInit } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDividerModule } from '@angular/material/divider';

import { TransactionDetail } from '../models/transaction.models';
import { TransactionService } from '../services/transaction.service';

/** Data injected into the detail dialog: the payment id to load. */
export interface TransactionDetailDialogData {
  paymentId: number;
}

/** Explicit placeholder rendered for any field that has no stored value (Req 8.1). */
export const EMPTY_PLACEHOLDER = '\u2014'; // em dash "—"

/**
 * Detail dialog for a single transaction.
 *
 * Loads the transaction detail by id and renders every detail field (Req 8.1),
 * showing an explicit placeholder for null/blank values rather than omitting the
 * field. A verification block is shown when the transaction is verified (Req 8.3)
 * and a reversal block when it is reversed (Req 8.4).
 *
 * Error handling (Req 8.5/8.6/8.7): the dialog NEVER shows partial fields on
 * error. Instead it shows a single message keyed by the HTTP status:
 *  - 403 -> "access denied"
 *  - 404 -> "not found"
 *  - any other failure -> "could not load"
 */
@Component({
  selector: 'app-transaction-detail-dialog',
  standalone: true,
  imports: [
    CommonModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatDividerModule,
    CurrencyPipe,
    DatePipe,
  ],
  template: `
    <h2 mat-dialog-title>Transaction Details</h2>

    <mat-dialog-content>
      <!-- Loading -->
      <div class="tdd-loading" *ngIf="loading">
        <mat-spinner diameter="36"></mat-spinner>
        <span>Loading transaction details…</span>
      </div>

      <!-- Error: no partial fields are rendered (Req 8.5/8.6/8.7) -->
      <div class="tdd-error" *ngIf="!loading && errorMessage" role="alert">
        <mat-icon color="warn">error_outline</mat-icon>
        <span>{{ errorMessage }}</span>
      </div>

      <!-- Detail body -->
      <div class="tdd-body" *ngIf="!loading && !errorMessage && detail as d">
        <dl class="tdd-grid">
          <dt>Payer name</dt>
          <dd>{{ display(d.payerName) }}</dd>

          <dt>Payer type</dt>
          <dd>{{ display(d.payerType) }}</dd>

          <dt>Unit number</dt>
          <dd>{{ display(d.unitNumber) }}</dd>

          <dt>Amount</dt>
          <dd>{{ displayCurrency(d.amount) }}</dd>

          <dt>Original amount</dt>
          <dd>{{ displayCurrency(d.originalAmount) }}</dd>

          <dt>Discount amount</dt>
          <dd>{{ displayCurrency(d.discountAmount) }}</dd>

          <dt>Discount percent</dt>
          <dd>{{ displayPercent(d.discountPercent) }}</dd>

          <dt>Payment date</dt>
          <dd>{{ displayDate(d.paymentDate) }}</dd>

          <dt>Payment mode</dt>
          <dd>{{ display(d.paymentMode) }}</dd>

          <dt>Transaction reference</dt>
          <dd>{{ display(d.transactionId) }}</dd>

          <dt>Receipt number</dt>
          <dd>{{ display(d.receiptNumber) }}</dd>

          <dt>Status</dt>
          <dd>{{ display(d.status) }}</dd>

          <dt>Remarks</dt>
          <dd>{{ display(d.remarks) }}</dd>
        </dl>

        <!-- Verification block (Req 8.3) -->
        <ng-container *ngIf="isVerified(d)">
          <mat-divider></mat-divider>
          <h3 class="tdd-section-title">Verification</h3>
          <dl class="tdd-grid">
            <dt>Verified on</dt>
            <dd>{{ displayDateTime(d.verifiedOn) }}</dd>

            <dt>Verified by</dt>
            <dd>{{ display(d.verifiedBy) }}</dd>
          </dl>
        </ng-container>

        <!-- Reversal block (Req 8.4) -->
        <ng-container *ngIf="isReversed(d)">
          <mat-divider></mat-divider>
          <h3 class="tdd-section-title">Reversal</h3>
          <dl class="tdd-grid">
            <dt>Reversed on</dt>
            <dd>{{ displayDateTime(d.reversedOn) }}</dd>

            <dt>Reversed by</dt>
            <dd>{{ display(d.reversedBy) }}</dd>

            <dt>Reversal reason</dt>
            <dd>{{ display(d.reversalReason) }}</dd>
          </dl>
        </ng-container>
      </div>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Close</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .tdd-loading {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 16px 0;
      color: #555;
    }
    .tdd-error {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 16px 0;
      color: #b00020;
    }
    .tdd-grid {
      display: grid;
      grid-template-columns: minmax(140px, auto) 1fr;
      gap: 8px 24px;
      margin: 12px 0;
    }
    .tdd-grid dt {
      font-weight: 600;
      color: #555;
    }
    .tdd-grid dd {
      margin: 0;
      color: #222;
      word-break: break-word;
    }
    .tdd-section-title {
      margin: 16px 0 4px;
      font-size: 1rem;
    }
  `],
})
export class TransactionDetailDialogComponent implements OnInit {
  loading = true;
  errorMessage: string | null = null;
  detail: TransactionDetail | null = null;

  readonly placeholder = EMPTY_PLACEHOLDER;

  constructor(
    private readonly transactionService: TransactionService,
    private readonly dialogRef: MatDialogRef<TransactionDetailDialogComponent>,
    @Inject(MAT_DIALOG_DATA) private readonly data: TransactionDetailDialogData,
  ) {}

  ngOnInit(): void {
    this.transactionService.getTransaction(this.data.paymentId).subscribe({
      next: detail => {
        this.detail = detail;
        this.loading = false;
      },
      error: (err: unknown) => {
        // On any error we clear the detail so no partial fields render
        // (Req 8.5 / 8.6 / 8.7).
        this.detail = null;
        this.errorMessage = this.messageForError(err);
        this.loading = false;
      },
    });
  }

  /** Map an HTTP error to the required user-facing message. */
  private messageForError(err: unknown): string {
    const status = err instanceof HttpErrorResponse ? err.status : 0;
    switch (status) {
      case 403:
        return 'Access denied: you do not have permission to view this transaction.';
      case 404:
        return 'Transaction not found.';
      default:
        return 'The transaction details could not be loaded. Please try again.';
    }
  }

  /** A transaction is considered verified when it carries verification metadata. */
  isVerified(d: TransactionDetail): boolean {
    return this.isPresent(d.verifiedOn) || this.isPresent(d.verifiedBy);
  }

  /** A transaction is considered reversed when it carries reversal metadata. */
  isReversed(d: TransactionDetail): boolean {
    return (
      this.isPresent(d.reversedOn) ||
      this.isPresent(d.reversedBy) ||
      this.isPresent(d.reversalReason)
    );
  }

  /** Render a string/label value, falling back to the placeholder when absent. */
  display(value: string | null | undefined): string {
    return this.isPresent(value) ? String(value).trim() : this.placeholder;
  }

  /** Render a monetary value, falling back to the placeholder when absent. */
  displayCurrency(value: number | null | undefined): string {
    if (!this.isNumberPresent(value)) {
      return this.placeholder;
    }
    return this.currencyPipe.transform(value, 'INR') ?? this.placeholder;
  }

  /** Render a percentage value, falling back to the placeholder when absent. */
  displayPercent(value: number | null | undefined): string {
    return this.isNumberPresent(value) ? `${value}%` : this.placeholder;
  }

  /** Render a date value, falling back to the placeholder when absent. */
  displayDate(value: string | null | undefined): string {
    if (!this.isPresent(value)) {
      return this.placeholder;
    }
    return this.datePipe.transform(value, 'mediumDate') ?? this.placeholder;
  }

  /** Render a date-time value, falling back to the placeholder when absent. */
  displayDateTime(value: string | null | undefined): string {
    if (!this.isPresent(value)) {
      return this.placeholder;
    }
    return this.datePipe.transform(value, 'medium') ?? this.placeholder;
  }

  private isPresent(value: unknown): boolean {
    if (value === null || value === undefined) {
      return false;
    }
    if (typeof value === 'string') {
      return value.trim().length > 0;
    }
    return true;
  }

  private isNumberPresent(value: number | null | undefined): boolean {
    return value !== null && value !== undefined && !Number.isNaN(value);
  }

  private readonly currencyPipe = new CurrencyPipe('en-IN');
  private readonly datePipe = new DatePipe('en-IN');
}
