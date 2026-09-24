import { Component, Inject, OnInit } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatTableModule } from '@angular/material/table';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

import { TdsService } from '@core/services/tds.service';
import {
  CreateRemittanceRequest,
  TdsLineDTO,
  TdsRemittanceDTO
} from '@core/models/tds.model';
import { TdsStatusBadgeComponent } from '../tds-status-badge/tds-status-badge.component';

export interface CreateRemittanceDialogData {
  /** Candidate lines to choose from; only DEDUCTED lines are selectable. */
  lines: TdsLineDTO[];
}

/**
 * Dialog for stage 1 of the TDS remittance lifecycle: paid-to-accountant.
 *
 * The user picks one or more eligible (DEDUCTED) deducted-TDS lines, records the
 * accountant name, the date the amount was handed to the accountant, and an
 * optional reference/remarks. On confirm it calls POST /tds/remittances via
 * {@link TdsService.createRemittance} and closes with the created batch.
 */
@Component({
  selector: 'app-create-remittance-dialog',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule,
    MatDialogModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule,
    MatDatepickerModule, MatNativeDateModule,
    MatTableModule, MatCheckboxModule,
    MatProgressSpinnerModule, MatSnackBarModule,
    CurrencyPipe, DatePipe, TdsStatusBadgeComponent
  ],
  template: `
    <h2 mat-dialog-title>
      <mat-icon color="primary">account_balance</mat-icon>
      Record Remittance to Accountant
    </h2>

    <mat-dialog-content>
      <p class="dialog-subtitle">
        Select the deducted TDS lines handed over to the accountant, then capture the
        accountant details. Only lines with status <strong>Deducted</strong> can be included.
      </p>

      <!-- Selectable lines -->
      <div class="lines-section">
        <ng-container *ngIf="eligibleLines.length > 0; else noEligible">
          <table mat-table [dataSource]="eligibleLines" class="lines-table">
            <!-- Select -->
            <ng-container matColumnDef="select">
              <th mat-header-cell *matHeaderCellDef>
                <mat-checkbox
                  [checked]="allSelected"
                  [indeterminate]="someSelected"
                  (change)="toggleAll($event.checked)">
                </mat-checkbox>
              </th>
              <td mat-cell *matCellDef="let line">
                <mat-checkbox
                  [checked]="isSelected(line)"
                  (change)="toggleLine(line, $event.checked)">
                </mat-checkbox>
              </td>
            </ng-container>

            <!-- Voucher -->
            <ng-container matColumnDef="voucherNumber">
              <th mat-header-cell *matHeaderCellDef>Voucher</th>
              <td mat-cell *matCellDef="let line">{{ line.voucherNumber }}</td>
            </ng-container>

            <!-- Vendor -->
            <ng-container matColumnDef="vendorName">
              <th mat-header-cell *matHeaderCellDef>Vendor</th>
              <td mat-cell *matCellDef="let line">{{ line.vendorName }}</td>
            </ng-container>

            <!-- Deduction Date -->
            <ng-container matColumnDef="deductionDate">
              <th mat-header-cell *matHeaderCellDef>Deducted On</th>
              <td mat-cell *matCellDef="let line">{{ line.deductionDate | date:'dd MMM yyyy' }}</td>
            </ng-container>

            <!-- Section -->
            <ng-container matColumnDef="tdsSection">
              <th mat-header-cell *matHeaderCellDef>Section</th>
              <td mat-cell *matCellDef="let line">{{ line.tdsSection }}</td>
            </ng-container>

            <!-- Amount -->
            <ng-container matColumnDef="tdsAmount">
              <th mat-header-cell *matHeaderCellDef class="num">TDS Amount</th>
              <td mat-cell *matCellDef="let line" class="num">
                {{ line.tdsAmount | currency:'INR':'symbol':'1.2-2' }}
              </td>
            </ng-container>

            <!-- Status -->
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>Status</th>
              <td mat-cell *matCellDef="let line">
                <app-tds-status-badge [status]="line.status"></app-tds-status-badge>
              </td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
          </table>

          <div class="selection-summary">
            <span>{{ selectedIds.size }} line(s) selected</span>
            <span class="total">
              Total: {{ selectedTotal | currency:'INR':'symbol':'1.2-2' }}
            </span>
          </div>
        </ng-container>

        <ng-template #noEligible>
          <div class="empty-state">
            <mat-icon>info</mat-icon>
            <span>No deducted lines are available to remit.</span>
          </div>
        </ng-template>
      </div>

      <!-- Accountant details -->
      <form [formGroup]="form" class="details-form">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Accountant Name</mat-label>
          <input matInput formControlName="accountantName" maxlength="100"
                 placeholder="Name of the accountant">
          <mat-error *ngIf="form.get('accountantName')?.hasError('required')">
            Accountant name is required
          </mat-error>
          <mat-error *ngIf="form.get('accountantName')?.hasError('maxlength')">
            Accountant name must be 100 characters or fewer
          </mat-error>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Paid to Accountant Date</mat-label>
          <input matInput [matDatepicker]="picker" [max]="today"
                 formControlName="paidToAccountantDate">
          <mat-datepicker-toggle matSuffix [for]="picker"></mat-datepicker-toggle>
          <mat-datepicker #picker></mat-datepicker>
          <mat-error *ngIf="form.get('paidToAccountantDate')?.hasError('required')">
            Payment date is required
          </mat-error>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Accountant Reference (optional)</mat-label>
          <input matInput formControlName="accountantReference"
                 placeholder="e.g. hand-over slip / voucher no.">
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Remarks (optional)</mat-label>
          <textarea matInput formControlName="remarks" rows="2"></textarea>
        </mat-form-field>
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close [disabled]="submitting">Cancel</button>
      <button mat-raised-button color="primary"
              [disabled]="!canSubmit()"
              (click)="onSubmit()">
        <mat-spinner *ngIf="submitting" diameter="18" class="btn-spinner"></mat-spinner>
        <mat-icon *ngIf="!submitting">save</mat-icon>
        {{ submitting ? 'Saving…' : 'Create Remittance' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    h2[mat-dialog-title] {
      display: flex; align-items: center; gap: 8px; margin: 0;
    }
    .dialog-subtitle { color: #555; margin-bottom: 16px; line-height: 1.5; }

    .lines-section { margin-bottom: 20px; }
    .lines-table { width: 100%; }
    .lines-table .num { text-align: right; }
    td.mat-cell, th.mat-header-cell { padding: 6px 10px; }

    .selection-summary {
      display: flex; justify-content: space-between; align-items: center;
      padding: 10px 12px; margin-top: 8px;
      background: #f5f5f5; border-radius: 6px; font-size: 14px;
    }
    .selection-summary .total { font-weight: 600; color: #00695c; }

    .empty-state {
      display: flex; align-items: center; gap: 8px; color: #757575;
      padding: 16px; background: #fafafa; border-radius: 6px;
    }

    .details-form { display: flex; flex-direction: column; }
    .full-width { width: 100%; }
    .btn-spinner { display: inline-block; margin-right: 4px; }

    mat-dialog-content { min-width: 560px; max-width: 760px; }
    @media (max-width: 600px) {
      mat-dialog-content { min-width: unset; }
    }
  `]
})
export class CreateRemittanceDialogComponent implements OnInit {
  form: FormGroup;
  eligibleLines: TdsLineDTO[] = [];
  readonly selectedIds = new Set<number>();
  readonly today = new Date();
  submitting = false;

  readonly displayedColumns = [
    'select', 'voucherNumber', 'vendorName',
    'deductionDate', 'tdsSection', 'tdsAmount', 'status'
  ];

  constructor(
    private fb: FormBuilder,
    private tdsService: TdsService,
    private snackBar: MatSnackBar,
    private dialogRef: MatDialogRef<CreateRemittanceDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: CreateRemittanceDialogData
  ) {
    this.form = this.fb.group({
      accountantName: ['', [Validators.required, Validators.maxLength(100)]],
      paidToAccountantDate: [null, Validators.required],
      accountantReference: [''],
      remarks: ['']
    });
  }

  ngOnInit(): void {
    this.eligibleLines = (this.data?.lines || []).filter(l => l.status === 'DEDUCTED');
  }

  isSelected(line: TdsLineDTO): boolean {
    return this.selectedIds.has(line.voucherId);
  }

  toggleLine(line: TdsLineDTO, checked: boolean): void {
    if (checked) {
      this.selectedIds.add(line.voucherId);
    } else {
      this.selectedIds.delete(line.voucherId);
    }
  }

  toggleAll(checked: boolean): void {
    if (checked) {
      this.eligibleLines.forEach(l => this.selectedIds.add(l.voucherId));
    } else {
      this.selectedIds.clear();
    }
  }

  get allSelected(): boolean {
    return this.eligibleLines.length > 0
      && this.selectedIds.size === this.eligibleLines.length;
  }

  get someSelected(): boolean {
    return this.selectedIds.size > 0 && !this.allSelected;
  }

  get selectedTotal(): number {
    return this.eligibleLines
      .filter(l => this.selectedIds.has(l.voucherId))
      .reduce((sum, l) => sum + Number(l.tdsAmount), 0);
  }

  canSubmit(): boolean {
    return !this.submitting && this.form.valid && this.selectedIds.size > 0;
  }

  onSubmit(): void {
    if (!this.canSubmit()) { return; }

    const raw = this.form.value;
    const request: CreateRemittanceRequest = {
      voucherIds: Array.from(this.selectedIds),
      accountantName: (raw.accountantName as string).trim(),
      paidToAccountantDate: this.formatDate(raw.paidToAccountantDate),
      accountantReference: this.trimToUndefined(raw.accountantReference),
      remarks: this.trimToUndefined(raw.remarks)
    };

    this.submitting = true;
    this.tdsService.createRemittance(request).subscribe({
      next: res => {
        this.submitting = false;
        if (res.success) {
          this.snackBar.open('Remittance batch created.', 'Close', { duration: 3000 });
          this.dialogRef.close(res.data as TdsRemittanceDTO);
        } else {
          this.snackBar.open(res.message || 'Failed to create remittance.', 'Close', { duration: 5000 });
        }
      },
      error: err => {
        this.submitting = false;
        const message = err?.error?.message || 'Failed to create remittance. Please try again.';
        this.snackBar.open(message, 'Close', { duration: 5000 });
      }
    });
  }

  /** Format a Date to ISO yyyy-MM-dd using local components (no UTC shift). */
  private formatDate(date: Date): string {
    const y = date.getFullYear();
    const m = `${date.getMonth() + 1}`.padStart(2, '0');
    const d = `${date.getDate()}`.padStart(2, '0');
    return `${y}-${m}-${d}`;
  }

  private trimToUndefined(value?: string): string | undefined {
    const trimmed = (value || '').trim();
    return trimmed === '' ? undefined : trimmed;
  }
}
