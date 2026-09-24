import { Component, Inject } from '@angular/core';
import { CommonModule, CurrencyPipe } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { TdsService } from '@core/services/tds.service';
import { RecordItRemittanceRequest, TdsRemittanceDTO } from '@core/models/tds.model';

/**
 * Dialog input. Prefer passing the full batch; a minimal
 * `{ remittanceId, batchReference }` also works (the read-only context
 * simply shows fewer fields).
 */
export interface RecordItRemittanceDialogData {
  remittance?: TdsRemittanceDTO;
  remittanceId?: number;
  batchReference?: string;
}

/**
 * Stage-2 dialog: record the Income Tax Department remittance for a
 * PAID_TO_ACCOUNTANT batch. Captures a challan number and paid-to-IT date
 * and calls `PATCH /tds/remittances/{id}/it-remittance` via TdsService.
 *
 * On success, closes with the updated `TdsRemittanceDTO`. On error, keeps
 * the dialog open and surfaces the backend message via a snackbar.
 */
@Component({
  selector: 'app-record-it-remittance-dialog',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule,
    MatDialogModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule,
    MatDatepickerModule, MatNativeDateModule,
    MatSnackBarModule, MatProgressSpinnerModule, CurrencyPipe
  ],
  template: `
    <h2 mat-dialog-title>
      <mat-icon color="primary">account_balance</mat-icon>
      Record IT-Department Remittance
    </h2>

    <mat-dialog-content>
      <!-- Read-only batch context -->
      <div class="batch-context">
        <div class="info-row">
          <span class="label">Batch Reference</span>
          <span class="value">{{ batchReference || '—' }}</span>
        </div>
        <div class="info-row" *ngIf="remittance">
          <span class="label">Financial Year</span>
          <span class="value">{{ remittance.financialYear }}</span>
        </div>
        <div class="info-row" *ngIf="remittance">
          <span class="label">Total Amount</span>
          <span class="value amount">{{ remittance.totalAmount | currency:'INR' }}</span>
        </div>
      </div>

      <form [formGroup]="form">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Challan Number</mat-label>
          <input matInput formControlName="challanNumber" maxlength="50"
                 placeholder="e.g. CIN0123456789">
          <mat-error *ngIf="form.get('challanNumber')?.hasError('required')">
            Challan number is required
          </mat-error>
          <mat-error *ngIf="form.get('challanNumber')?.hasError('maxlength')">
            Challan number must be 50 characters or fewer
          </mat-error>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Paid-to-IT Date</mat-label>
          <input matInput formControlName="paidToItDate"
                 [matDatepicker]="picker" [max]="today" readonly>
          <mat-datepicker-toggle matSuffix [for]="picker"></mat-datepicker-toggle>
          <mat-datepicker #picker></mat-datepicker>
          <mat-error *ngIf="form.get('paidToItDate')?.hasError('required')">
            Paid-to-IT date is required
          </mat-error>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Remarks (optional)</mat-label>
          <textarea matInput formControlName="remarks" rows="3"
                    maxlength="500" placeholder="Any notes about this remittance"></textarea>
        </mat-form-field>
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close [disabled]="submitting">Cancel</button>
      <button mat-raised-button color="primary"
              (click)="onSubmit()" [disabled]="form.invalid || submitting">
        <mat-spinner *ngIf="submitting" diameter="18" class="btn-spinner"></mat-spinner>
        <mat-icon *ngIf="!submitting">check</mat-icon>
        {{ submitting ? 'Recording…' : 'Record Remittance' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    :host { display: block; min-width: 420px; }

    h2[mat-dialog-title] {
      display: flex; align-items: center; gap: 8px; margin: 0;
    }

    .batch-context {
      background: #f5f5f5; border-radius: 8px;
      padding: 12px 16px; margin-bottom: 16px;
    }
    .info-row {
      display: flex; justify-content: space-between; align-items: center;
      padding: 4px 0;
    }
    .info-row .label { color: #666; font-size: 13px; }
    .info-row .value { font-weight: 500; font-size: 14px; }
    .info-row .value.amount { color: #e65100; font-weight: 600; }

    .full-width { width: 100%; }

    .btn-spinner { display: inline-block; margin-right: 6px; }

    @media (max-width: 520px) {
      :host { min-width: unset; }
    }
  `]
})
export class RecordItRemittanceDialogComponent {
  form: FormGroup;
  submitting = false;

  /** Upper bound for the datepicker — the paid-to-IT date cannot be in the future. */
  readonly today = new Date();

  readonly remittance?: TdsRemittanceDTO;
  readonly remittanceId: number;
  readonly batchReference?: string;

  constructor(
    private fb: FormBuilder,
    private tdsService: TdsService,
    private snackBar: MatSnackBar,
    private dialogRef: MatDialogRef<RecordItRemittanceDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: RecordItRemittanceDialogData
  ) {
    this.remittance = data.remittance;
    this.remittanceId = data.remittance?.remittanceId ?? data.remittanceId!;
    this.batchReference = data.remittance?.batchReference ?? data.batchReference;

    this.form = this.fb.group({
      challanNumber: ['', [Validators.required, Validators.maxLength(50)]],
      paidToItDate: [null, Validators.required],
      remarks: ['', Validators.maxLength(500)]
    });
  }

  onSubmit(): void {
    if (this.form.invalid || this.submitting) { return; }

    const raw = this.form.value;
    const remarks = (raw.remarks as string | null)?.trim();
    const request: RecordItRemittanceRequest = {
      challanNumber: (raw.challanNumber as string).trim(),
      paidToItDate: this.formatDate(raw.paidToItDate as Date),
      ...(remarks ? { remarks } : {})
    };

    this.submitting = true;
    this.tdsService.recordItRemittance(this.remittanceId, request).subscribe({
      next: (res) => {
        this.submitting = false;
        if (res.success && res.data) {
          this.dialogRef.close(res.data);
        } else {
          this.snackBar.open(res.message || 'Failed to record remittance.', 'Dismiss', {
            duration: 5000
          });
        }
      },
      error: (err) => {
        this.submitting = false;
        this.snackBar.open(
          err.error?.message || 'Failed to record remittance. Please try again.',
          'Dismiss',
          { duration: 5000 }
        );
      }
    });
  }

  /** Format a Date to a timezone-safe ISO yyyy-MM-dd (avoids UTC day shift). */
  private formatDate(date: Date): string {
    const year = date.getFullYear();
    const month = `${date.getMonth() + 1}`.padStart(2, '0');
    const day = `${date.getDate()}`.padStart(2, '0');
    return `${year}-${month}-${day}`;
  }
}
