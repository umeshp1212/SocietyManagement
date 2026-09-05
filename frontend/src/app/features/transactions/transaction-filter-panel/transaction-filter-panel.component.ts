import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { AuthService } from '@core/services/auth.service';
import {
  PayerType,
  PaymentMode,
  TransactionFilter,
  TransactionStatus,
} from '../models/transaction.models';

/**
 * Filter panel for the Transaction Page.
 *
 * Renders a reactive form with:
 *  - a date range (start / end) using mat-date-range-input
 *  - payment-mode single-select
 *  - status multi-select (OR semantics on the server)
 *  - payer-type single-select (admin only)
 *  - unit search text (admin only)
 *  - reference search text
 *
 * Emits a {@link TransactionFilter} on apply and on clear. Client-side guards
 * (start <= end, max lengths) provide fast feedback, but the server remains
 * authoritative: {@link setServerError} lets the orchestrator surface a server
 * validation message while keeping the previously-applied filter intact
 * (Req 7.6).
 *
 * Unit / payer admin-only filters are hidden for members based on the app's
 * existing role info (AuthService); the backend enforces access scope
 * regardless of what the client renders.
 */
@Component({
  selector: 'app-transaction-filter-panel',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatButtonModule, MatCardModule, MatIconModule,
    MatDatepickerModule, MatNativeDateModule,
  ],
  template: `
    <mat-card class="filter-panel">
      <mat-card-content>
        <form [formGroup]="filterForm" (ngSubmit)="onApply()">
          <div class="filter-row">

            <!-- Date range (Req 3) -->
            <mat-form-field appearance="outline">
              <mat-label>Payment date range</mat-label>
              <mat-date-range-input [rangePicker]="rangePicker">
                <input matStartDate formControlName="startDate" placeholder="Start date">
                <input matEndDate formControlName="endDate" placeholder="End date">
              </mat-date-range-input>
              <mat-datepicker-toggle matSuffix [for]="rangePicker"></mat-datepicker-toggle>
              <mat-date-range-picker #rangePicker></mat-date-range-picker>
              <mat-error *ngIf="filterForm.hasError('dateRange')">
                Start date must be on or before end date
              </mat-error>
            </mat-form-field>

            <!-- Payment mode (Req 4.1) -->
            <mat-form-field appearance="outline">
              <mat-label>Payment mode</mat-label>
              <mat-select formControlName="paymentMode">
                <mat-option [value]="null">-- Any --</mat-option>
                <mat-option *ngFor="let mode of paymentModes" [value]="mode">
                  {{ mode }}
                </mat-option>
              </mat-select>
            </mat-form-field>

            <!-- Status multi-select (Req 5.1 / 5.3) -->
            <mat-form-field appearance="outline">
              <mat-label>Status</mat-label>
              <mat-select formControlName="statuses" multiple>
                <mat-option *ngFor="let status of statuses" [value]="status">
                  {{ status }}
                </mat-option>
              </mat-select>
            </mat-form-field>

            <!-- Payer type (admin only, Req 6.3) -->
            <mat-form-field appearance="outline" *ngIf="isAdmin">
              <mat-label>Payer type</mat-label>
              <mat-select formControlName="payerType">
                <mat-option [value]="null">-- Any --</mat-option>
                <mat-option *ngFor="let type of payerTypes" [value]="type">
                  {{ type }}
                </mat-option>
              </mat-select>
            </mat-form-field>

            <!-- Unit search (admin only, Req 6.4 / 6.5) -->
            <mat-form-field appearance="outline" *ngIf="isAdmin">
              <mat-label>Unit search</mat-label>
              <input matInput formControlName="unitSearch" [maxlength]="UNIT_SEARCH_MAX"
                     placeholder="Unit number">
              <mat-error *ngIf="filterForm.get('unitSearch')?.hasError('maxlength')">
                Unit search must be at most {{ UNIT_SEARCH_MAX }} characters
              </mat-error>
            </mat-form-field>

            <!-- Reference search (Req 9.1 / 9.3) -->
            <mat-form-field appearance="outline">
              <mat-label>Reference</mat-label>
              <input matInput formControlName="reference" [maxlength]="REFERENCE_MAX"
                     placeholder="Receipt / transaction id">
              <mat-error *ngIf="filterForm.get('reference')?.hasError('maxlength')">
                Reference must be at most {{ REFERENCE_MAX }} characters
              </mat-error>
            </mat-form-field>
          </div>

          <!-- Server validation error (Req 7.6) -->
          <div class="server-error" *ngIf="serverError">
            <mat-icon>error_outline</mat-icon>
            <span>{{ serverError }}</span>
          </div>

          <div class="filter-actions">
            <button mat-button type="button" (click)="onClear()">Clear</button>
            <button mat-raised-button color="primary" type="submit"
                    [disabled]="filterForm.invalid">
              Apply
            </button>
          </div>
        </form>
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    .filter-panel { margin-bottom: 16px; }
    .filter-row { display: flex; flex-wrap: wrap; gap: 12px; }
    .filter-row mat-form-field { flex: 1 1 200px; min-width: 180px; }
    .filter-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 8px; }
    .server-error {
      display: flex; align-items: center; gap: 8px;
      color: #c62828; font-size: 13px; margin: 8px 0;
    }
    .server-error mat-icon { font-size: 18px; height: 18px; width: 18px; }
  `],
})
export class TransactionFilterPanelComponent implements OnInit {

  /** Max length for the unit-search filter (Req 6.5). Server authoritative. */
  readonly UNIT_SEARCH_MAX = 50;
  /** Max length for the reference filter (Req 9.3). Server authoritative. */
  readonly REFERENCE_MAX = 100;

  /** Society-wide roles that see the admin-only filters (mirrors backend scope). */
  private static readonly ADMIN_ROLES = ['SUPER_ADMIN', 'CHAIRMAN', 'SECRETARY', 'TREASURER'];

  readonly paymentModes: PaymentMode[] = [
    'CASHFREE_LINK', 'CASHFREE_QR', 'RAZORPAY', 'UPI', 'GPAY', 'PHONEPE',
    'NEFT', 'RTGS', 'IMPS', 'CHEQUE', 'CASH', 'BANK_TRANSFER',
  ];

  readonly statuses: TransactionStatus[] = [
    'PENDING', 'SUCCESS', 'FAILED', 'VERIFIED', 'REVERSED',
  ];

  readonly payerTypes: PayerType[] = ['OWNER', 'TENANT'];

  /**
   * Whether the current user has a society-wide role. When false (member),
   * the unit/payer admin-only filters are hidden. Can be overridden via input
   * for embedding contexts/tests.
   */
  @Input() isAdmin = false;

  /** Emitted with the composed filter when the user applies or clears. */
  @Output() filterChange = new EventEmitter<TransactionFilter>();

  filterForm!: FormGroup;

  /** Last successfully-applied filter, restored if the server rejects a change. */
  private lastAppliedFilter: TransactionFilter = {};

  /** Server-side validation message to display (Req 7.6). */
  serverError: string | null = null;

  constructor(private fb: FormBuilder, private authService: AuthService) {}

  ngOnInit(): void {
    // Derive role info from the app's existing auth state unless explicitly set.
    if (!this.isAdmin) {
      this.isAdmin = this.authService.hasAnyRole(TransactionFilterPanelComponent.ADMIN_ROLES);
    }

    this.filterForm = this.fb.group(
      {
        startDate: [null],
        endDate: [null],
        paymentMode: [null],
        statuses: [[]],
        payerType: [null],
        unitSearch: ['', [Validators.maxLength(this.UNIT_SEARCH_MAX)]],
        reference: ['', [Validators.maxLength(this.REFERENCE_MAX)]],
      },
      { validators: [dateRangeValidator] },
    );
  }

  /** Build a TransactionFilter from the current form value, dropping empties. */
  private buildFilter(): TransactionFilter {
    const v = this.filterForm.getRawValue();
    const filter: TransactionFilter = {};

    if (v.startDate) {
      filter.startDate = this.formatDate(v.startDate);
    }
    if (v.endDate) {
      filter.endDate = this.formatDate(v.endDate);
    }
    if (v.paymentMode) {
      filter.paymentMode = v.paymentMode;
    }
    if (Array.isArray(v.statuses) && v.statuses.length > 0) {
      filter.statuses = v.statuses;
    }
    // Admin-only filters are only included when the user is an administrator.
    if (this.isAdmin) {
      if (v.payerType) {
        filter.payerType = v.payerType;
      }
      const unitSearch = (v.unitSearch ?? '').trim();
      if (unitSearch.length > 0) {
        filter.unitSearch = unitSearch;
      }
    }
    const reference = (v.reference ?? '').trim();
    if (reference.length > 0) {
      filter.reference = reference;
    }

    return filter;
  }

  onApply(): void {
    if (this.filterForm.invalid) {
      return;
    }
    // A successful apply clears any stale server error and records the filter
    // so it can be restored if the subsequent server call rejects it.
    this.serverError = null;
    const filter = this.buildFilter();
    this.lastAppliedFilter = filter;
    this.filterChange.emit(filter);
  }

  onClear(): void {
    this.serverError = null;
    this.filterForm.reset({
      startDate: null,
      endDate: null,
      paymentMode: null,
      statuses: [],
      payerType: null,
      unitSearch: '',
      reference: '',
    });
    const empty: TransactionFilter = {};
    this.lastAppliedFilter = empty;
    this.filterChange.emit(empty);
  }

  /**
   * Surface a server validation error and keep the prior filter (Req 7.6).
   *
   * The orchestrator calls this when the backend rejects a filter. The panel
   * shows the message; the last successfully-applied filter remains the active
   * one (the form is left as-is so the user can correct their input).
   */
  setServerError(message: string): void {
    this.serverError = message;
  }

  /** The filter currently considered applied (survives a rejected change). */
  getLastAppliedFilter(): TransactionFilter {
    return this.lastAppliedFilter;
  }

  /** Format a Date (or ISO string) to a yyyy-MM-dd string for the API. */
  private formatDate(date: unknown): string {
    const d = new Date(date as string | number | Date);
    const year = d.getFullYear();
    const month = `${d.getMonth() + 1}`.padStart(2, '0');
    const day = `${d.getDate()}`.padStart(2, '0');
    return `${year}-${month}-${day}`;
  }
}

/**
 * Cross-field validator: when both start and end dates are present, start must
 * be on or before end (Req 3.4). Sets a `dateRange` error on the group.
 */
function dateRangeValidator(group: AbstractControl): ValidationErrors | null {
  const start = group.get('startDate')?.value;
  const end = group.get('endDate')?.value;
  if (start && end) {
    const startTime = new Date(start).getTime();
    const endTime = new Date(end).getTime();
    if (!Number.isNaN(startTime) && !Number.isNaN(endTime) && startTime > endTime) {
      return { dateRange: true };
    }
  }
  return null;
}
