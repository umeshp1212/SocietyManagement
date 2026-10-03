import { Component, Inject } from '@angular/core';
import { CommonModule, CurrencyPipe } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

export interface ReversePaymentDialogData {
  amount: number;
  receiptNumber?: string;
}

/**
 * Confirmation dialog for reversing a payment. Requires a non-empty reason.
 * Returns the trimmed reason string on confirm, or undefined on cancel.
 */
@Component({
  selector: 'app-reverse-payment-dialog',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatDialogModule, MatFormFieldModule,
    MatInputModule, MatButtonModule, MatIconModule, CurrencyPipe
  ],
  template: `
    <h2 mat-dialog-title>Reverse Payment</h2>
    <mat-dialog-content>
      <p class="dialog-subtitle">
        You are about to reverse a payment of
        <strong>{{ data.amount | currency:'INR' }}</strong>
        <ng-container *ngIf="data.receiptNumber"> (receipt {{ data.receiptNumber }})</ng-container>.
        This restores the amount to the bill's outstanding balance and is recorded in the audit ledger.
      </p>

      <form [formGroup]="form">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Reason for reversal</mat-label>
          <textarea matInput formControlName="reason" rows="3"
                    placeholder="e.g. Duplicate payment, bounced cheque, wrong unit"></textarea>
          <mat-error *ngIf="form.get('reason')?.hasError('required')">A reason is required</mat-error>
          <mat-error *ngIf="form.get('reason')?.hasError('maxlength')">Reason must be 500 characters or fewer</mat-error>
        </mat-form-field>
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-raised-button color="warn" (click)="onConfirm()" [disabled]="form.invalid">
        <mat-icon>undo</mat-icon> Reverse Payment
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .full-width { width: 100%; }
    .dialog-subtitle { color: #555; margin-bottom: 16px; line-height: 1.5; }
  `]
})
export class ReversePaymentDialogComponent {
  form: FormGroup;

  constructor(
    private fb: FormBuilder,
    private dialogRef: MatDialogRef<ReversePaymentDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: ReversePaymentDialogData
  ) {
    this.form = this.fb.group({
      reason: ['', [Validators.required, Validators.maxLength(500)]]
    });
  }

  onConfirm(): void {
    if (this.form.invalid) { return; }
    this.dialogRef.close((this.form.value.reason as string).trim());
  }
}
