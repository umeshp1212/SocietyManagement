import { Component, Inject, OnInit } from '@angular/core';
import { CommonModule, CurrencyPipe } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { MaintenanceService } from '@core/services/maintenance.service';
import { OwnerService } from '@core/services/owner.service';
import { Unit } from '@core/models/owner.model';

export interface ReassignPaymentDialogData {
  paymentId: number;
  amount: number;
  receiptNumber?: string;
  /** Current (wrong) unit label, shown for context. */
  currentUnitNumber?: string;
  currentBillId?: number;
}

export interface ReassignPaymentResult {
  targetBillId: number;
  reason: string;
}

interface TargetBillOption {
  billId: number;
  label: string;
  balanceAmount: number;
  status: string;
}

/**
 * Dialog to reassign a wrongly-attributed payment to the correct unit's bill.
 *
 * Flow: pick the correct unit -> pick one of that unit's not-fully-paid bills -> give a reason.
 * Only bills whose outstanding balance can absorb the payment amount are selectable.
 * Returns { targetBillId, reason } on confirm, or undefined on cancel.
 */
@Component({
  selector: 'app-reassign-payment-dialog',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatDialogModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, MatButtonModule, MatIconModule,
    MatProgressSpinnerModule, CurrencyPipe
  ],
  template: `
    <h2 mat-dialog-title>Reassign Payment</h2>
    <mat-dialog-content>
      <p class="dialog-subtitle">
        You are reassigning a payment of
        <strong>{{ data.amount | currency:'INR' }}</strong>
        <ng-container *ngIf="data.receiptNumber"> (receipt {{ data.receiptNumber }})</ng-container>
        <ng-container *ngIf="data.currentUnitNumber"> currently on unit
          <strong>{{ data.currentUnitNumber }}</strong></ng-container>.
        This reverses it from the current bill and re-applies it to the selected bill.
        Both bills are recorded in the audit ledger.
      </p>

      <form [formGroup]="form">
        <!-- Correct unit -->
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Correct Unit</mat-label>
          <mat-select formControlName="unitId" (selectionChange)="onUnitChange($event.value)">
            <mat-option *ngFor="let u of units" [value]="u.unitId">
              {{ u.unitNumber }}<span *ngIf="u.primaryOwnerName"> — {{ u.primaryOwnerName }}</span>
            </mat-option>
          </mat-select>
          <mat-error *ngIf="form.get('unitId')?.hasError('required')">Select the correct unit</mat-error>
        </mat-form-field>

        <!-- Loading bills for the chosen unit -->
        <div class="loading-row" *ngIf="loadingBills">
          <mat-spinner diameter="20"></mat-spinner>
          <span>Loading bills…</span>
        </div>

        <!-- Target bill -->
        <mat-form-field appearance="outline" class="full-width"
                        *ngIf="form.get('unitId')?.value && !loadingBills">
          <mat-label>Target Bill</mat-label>
          <mat-select formControlName="targetBillId">
            <mat-option *ngFor="let b of eligibleBills" [value]="b.billId">{{ b.label }}</mat-option>
          </mat-select>
          <mat-error *ngIf="form.get('targetBillId')?.hasError('required')">Select a bill</mat-error>
        </mat-form-field>

        <p class="hint" *ngIf="form.get('unitId')?.value && !loadingBills && eligibleBills.length === 0">
          This unit has no outstanding bill that can absorb {{ data.amount | currency:'INR' }}.
        </p>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Reason for reassignment</mat-label>
          <textarea matInput formControlName="reason" rows="3"
                    placeholder="e.g. Bank statement showed payment credited to wrong unit"></textarea>
          <mat-error *ngIf="form.get('reason')?.hasError('required')">A reason is required</mat-error>
          <mat-error *ngIf="form.get('reason')?.hasError('maxlength')">Reason must be 500 characters or fewer</mat-error>
        </mat-form-field>
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-raised-button color="primary" (click)="onConfirm()" [disabled]="form.invalid">
        <mat-icon>swap_horiz</mat-icon> Reassign Payment
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .full-width { width: 100%; }
    .dialog-subtitle { color: #555; margin-bottom: 16px; line-height: 1.5; }
    .loading-row { display: flex; align-items: center; gap: 10px; color: #555; margin: 4px 0 12px; }
    .hint { color: #b71c1c; font-size: 13px; margin: -4px 0 12px; }
  `]
})
export class ReassignPaymentDialogComponent implements OnInit {
  form: FormGroup;
  units: Unit[] = [];
  eligibleBills: TargetBillOption[] = [];
  loadingBills = false;

  constructor(
    private fb: FormBuilder,
    private maintenanceService: MaintenanceService,
    private ownerService: OwnerService,
    private dialogRef: MatDialogRef<ReassignPaymentDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: ReassignPaymentDialogData
  ) {
    this.form = this.fb.group({
      unitId: [null, Validators.required],
      targetBillId: [null, Validators.required],
      reason: ['', [Validators.required, Validators.maxLength(500)]]
    });
  }

  ngOnInit(): void {
    // Load a generous page of units for the picker.
    this.ownerService.getAllUnits(0, 500).subscribe(res => {
      if (res.success) {
        this.units = (res.data?.content || [])
          .sort((a, b) => (a.unitNumber || '').localeCompare(b.unitNumber || ''));
      }
    });
  }

  onUnitChange(unitId: number): void {
    this.form.patchValue({ targetBillId: null });
    this.eligibleBills = [];
    if (!unitId) { return; }

    this.loadingBills = true;
    this.maintenanceService.getBillsByUnit(unitId).subscribe({
      next: res => {
        const bills = res.success ? (res.data || []) : [];
        this.eligibleBills = bills
          .filter(b => b.status !== 'PAID'
            && b.billId !== this.data.currentBillId
            && Number(b.balanceAmount) >= Number(this.data.amount))
          .map(b => ({
            billId: b.billId,
            balanceAmount: Number(b.balanceAmount),
            status: b.status,
            label: `${b.billPeriod || (b.billMonth + '/' + b.billYear)} — outstanding `
              + `₹${Number(b.balanceAmount).toFixed(2)} (${b.status})`
          }));
        this.loadingBills = false;
      },
      error: () => {
        this.eligibleBills = [];
        this.loadingBills = false;
      }
    });
  }

  onConfirm(): void {
    if (this.form.invalid) { return; }
    const result: ReassignPaymentResult = {
      targetBillId: this.form.value.targetBillId,
      reason: (this.form.value.reason as string).trim()
    };
    this.dialogRef.close(result);
  }
}
