import { Component, OnInit } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BreakpointObserver } from '@angular/cdk/layout';
import { forkJoin, of } from 'rxjs';
import { catchError, timeout } from 'rxjs/operators';

import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';

import { TdsService } from '@core/services/tds.service';
import { VendorService } from '@core/services/vendor.service';
import { AuthService } from '@core/services/auth.service';
import {
  TdsFilterRequest,
  TdsLineDTO,
  TdsRemittanceStatus,
  TdsSummaryDTO
} from '@core/models/tds.model';
import { Vendor } from '@core/models/vendor.model';
import { TdsStatusBadgeComponent } from '../tds-status-badge/tds-status-badge.component';
import { TdsSummaryCardsComponent } from '../tds-summary-cards/tds-summary-cards.component';

/** Milliseconds to wait for the TDS data before showing the error state (Requirement 10.4). */
const LOAD_TIMEOUT_MS = 10_000;

interface StatusOption {
  value: TdsRemittanceStatus;
  label: string;
}

/**
 * Filterable, paginated dashboard of deducted-TDS lines.
 *
 * Wires together the filter bar (deduction/remittance date ranges, vendor
 * picker, status multi-select, TDS section, financial year), the summary
 * cards ({@link TdsSummaryCardsComponent}) and the per-line status badges
 * ({@link TdsStatusBadgeComponent}). Both `/tds/lines` and `/tds/summary`
 * are requested for every filter change and are re-computed together so the
 * cards always agree with the visible list.
 *
 * Behaviour:
 * - Pagination defaults to page 0, size 20 and is wired to `/tds/lines`.
 * - When a filter matches zero lines an empty-result indication is shown in
 *   the table area and the summary cards render zeros (Requirement 10.5).
 * - If the data fails to load or does not arrive within 10 seconds, an error
 *   state is shown instead of a blank/partial screen (Requirement 10.4).
 */
@Component({
  selector: 'app-tds-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    MatTableModule, MatPaginatorModule,
    MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatSelectModule,
    MatDatepickerModule, MatNativeDateModule,
    MatProgressSpinnerModule, MatTooltipModule,
    CurrencyPipe, DatePipe,
    TdsStatusBadgeComponent, TdsSummaryCardsComponent
  ],
  template: `
    <div class="container">
      <div class="page-header">
        <h2>TDS Management</h2>
      </div>

      <!-- Not-authorized indication (Requirement 10.3). Shown instead of the
           screen content when the user lacks permission to view TDS data;
           no TDS data is requested in this state. -->
      <div class="state-block unauthorized-state" *ngIf="!authorized">
        <mat-icon color="warn">lock</mat-icon>
        <p>You are not authorized to view TDS data.</p>
      </div>

      <ng-container *ngIf="authorized">
      <!-- Filter bar -->
      <div class="filter-bar">
        <mat-form-field appearance="outline">
          <mat-label>Deduction From</mat-label>
          <input matInput [matDatepicker]="dedFrom" [(ngModel)]="deductionStartDate"
                 (dateChange)="applyFilters()">
          <mat-datepicker-toggle matSuffix [for]="dedFrom"></mat-datepicker-toggle>
          <mat-datepicker #dedFrom></mat-datepicker>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Deduction To</mat-label>
          <input matInput [matDatepicker]="dedTo" [(ngModel)]="deductionEndDate"
                 (dateChange)="applyFilters()">
          <mat-datepicker-toggle matSuffix [for]="dedTo"></mat-datepicker-toggle>
          <mat-datepicker #dedTo></mat-datepicker>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Remittance From</mat-label>
          <input matInput [matDatepicker]="remFrom" [(ngModel)]="remittanceStartDate"
                 (dateChange)="applyFilters()">
          <mat-datepicker-toggle matSuffix [for]="remFrom"></mat-datepicker-toggle>
          <mat-datepicker #remFrom></mat-datepicker>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Remittance To</mat-label>
          <input matInput [matDatepicker]="remTo" [(ngModel)]="remittanceEndDate"
                 (dateChange)="applyFilters()">
          <mat-datepicker-toggle matSuffix [for]="remTo"></mat-datepicker-toggle>
          <mat-datepicker #remTo></mat-datepicker>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Vendor</mat-label>
          <mat-select [(ngModel)]="vendorId" (selectionChange)="applyFilters()">
            <mat-option [value]="undefined">All Vendors</mat-option>
            <mat-option *ngFor="let v of vendors" [value]="v.vendorId">
              {{ v.vendorName }}
            </mat-option>
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Status</mat-label>
          <mat-select [(ngModel)]="statuses" (selectionChange)="applyFilters()" multiple>
            <mat-option *ngFor="let s of statusOptions" [value]="s.value">
              {{ s.label }}
            </mat-option>
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>TDS Section</mat-label>
          <input matInput [(ngModel)]="tdsSection" (keyup.enter)="applyFilters()"
                 (blur)="applyFilters()" placeholder="e.g. 194C">
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Financial Year</mat-label>
          <input matInput [(ngModel)]="financialYear" (keyup.enter)="applyFilters()"
                 (blur)="applyFilters()" placeholder="e.g. 2024-2025">
        </mat-form-field>

        <button mat-stroked-button class="clear-btn" (click)="clearFilters()"
                [disabled]="loading" matTooltip="Clear all filters">
          <mat-icon>clear</mat-icon> Clear
        </button>
      </div>

      <!-- Summary cards (zeros when no records) -->
      <app-tds-summary-cards [summary]="summary"></app-tds-summary-cards>

      <!-- Loading state -->
      <div class="state-block" *ngIf="loading">
        <mat-spinner diameter="40"></mat-spinner>
        <p>Loading TDS data…</p>
      </div>

      <!-- Error state (load failure / timeout) — never a blank screen -->
      <div class="state-block error-state" *ngIf="!loading && loadError">
        <mat-icon color="warn">error_outline</mat-icon>
        <p>TDS data could not be loaded. Please check your connection and try again.</p>
        <button mat-raised-button color="primary" (click)="retry()">
          <mat-icon>refresh</mat-icon> Retry
        </button>
      </div>

      <!-- Table area -->
      <ng-container *ngIf="!loading && !loadError">
        <table mat-table [dataSource]="lines" class="mat-elevation-z2 tds-table"
               *ngIf="lines.length > 0">
          <ng-container matColumnDef="voucherNumber">
            <th mat-header-cell *matHeaderCellDef>Voucher No</th>
            <td mat-cell *matCellDef="let l">{{ l.voucherNumber }}</td>
          </ng-container>

          <ng-container matColumnDef="vendorName">
            <th mat-header-cell *matHeaderCellDef>Vendor</th>
            <td mat-cell *matCellDef="let l">{{ l.vendorName }}</td>
          </ng-container>

          <ng-container matColumnDef="tdsSection">
            <th mat-header-cell *matHeaderCellDef>Section</th>
            <td mat-cell *matCellDef="let l">{{ l.tdsSection }}</td>
          </ng-container>

          <ng-container matColumnDef="tdsRate">
            <th mat-header-cell *matHeaderCellDef class="num">Rate</th>
            <td mat-cell *matCellDef="let l" class="num">{{ l.tdsRate | number:'1.0-2' }}%</td>
          </ng-container>

          <ng-container matColumnDef="tdsAmount">
            <th mat-header-cell *matHeaderCellDef class="num">TDS Amount</th>
            <td mat-cell *matCellDef="let l" class="num">
              {{ l.tdsAmount | currency:'INR':'symbol':'1.2-2' }}
            </td>
          </ng-container>

          <ng-container matColumnDef="deductionDate">
            <th mat-header-cell *matHeaderCellDef>Deduction Date</th>
            <td mat-cell *matCellDef="let l">{{ l.deductionDate | date:'dd MMM yyyy' }}</td>
          </ng-container>

          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>Status</th>
            <td mat-cell *matCellDef="let l">
              <app-tds-status-badge [status]="l.status"></app-tds-status-badge>
            </td>
          </ng-container>

          <ng-container matColumnDef="challanNumber">
            <th mat-header-cell *matHeaderCellDef>Challan No</th>
            <td mat-cell *matCellDef="let l">{{ l.challanNumber || '—' }}</td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="isMobile ? mobileColumns : displayedColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: isMobile ? mobileColumns : displayedColumns;"></tr>
        </table>

        <!-- Empty-result indication -->
        <div class="state-block empty-state" *ngIf="lines.length === 0">
          <mat-icon>inbox</mat-icon>
          <p>No TDS lines match the current filters.</p>
        </div>

        <mat-paginator [length]="totalElements" [pageIndex]="currentPage"
                       [pageSize]="pageSize" [pageSizeOptions]="[10, 20, 50]"
                       (page)="onPageChange($event)"
                       [hidden]="lines.length === 0"></mat-paginator>
      </ng-container>
      </ng-container>
    </div>
  `,
  styles: [`
    .container { padding: 16px; }
    .page-header { display: flex; justify-content: space-between; align-items: center; }

    .filter-bar {
      display: flex; flex-wrap: wrap; gap: 12px; align-items: center;
      margin-bottom: 12px;
    }
    .filter-bar mat-form-field { min-width: 160px; }
    .clear-btn { height: 40px; }

    .tds-table { width: 100%; }
    .tds-table .num { text-align: right; }
    td.mat-cell, th.mat-header-cell { padding: 8px 12px; }

    .state-block {
      display: flex; flex-direction: column; align-items: center; justify-content: center;
      gap: 12px; padding: 48px 16px; color: #666; text-align: center;
    }
    .state-block mat-icon {
      font-size: 40px; height: 40px; width: 40px;
    }
    .error-state { color: #c62828; }
    .empty-state { background: #fafafa; border-radius: 8px; }
    .unauthorized-state { color: #c62828; }
  `]
})
export class TdsListComponent implements OnInit {
  lines: TdsLineDTO[] = [];
  summary: TdsSummaryDTO | null = null;
  vendors: Vendor[] = [];

  /**
   * Whether the current user may view TDS data (Requirement 10.3). The route
   * is already gated by `permissionGuard(['VENDOR_VIEW'])`; this in-screen
   * check is defence-in-depth so the screen renders a clear unauthorized
   * indication — and requests no TDS data — if it is ever reached without
   * the permission.
   */
  authorized = true;

  loading = false;
  loadError = false;

  totalElements = 0;
  pageSize = 20;
  currentPage = 0;

  // Filter bar state
  deductionStartDate: Date | null = null;
  deductionEndDate: Date | null = null;
  remittanceStartDate: Date | null = null;
  remittanceEndDate: Date | null = null;
  vendorId?: number;
  statuses: TdsRemittanceStatus[] = [];
  tdsSection = '';
  financialYear = '';

  readonly statusOptions: StatusOption[] = [
    { value: 'DEDUCTED', label: 'Deducted' },
    { value: 'PAID_TO_ACCOUNTANT', label: 'Paid to Accountant' },
    { value: 'PAID_TO_IT_DEPARTMENT', label: 'Paid to IT Dept' }
  ];

  readonly displayedColumns = [
    'voucherNumber', 'vendorName', 'tdsSection', 'tdsRate',
    'tdsAmount', 'deductionDate', 'status', 'challanNumber'
  ];
  readonly mobileColumns = ['voucherNumber', 'vendorName', 'tdsAmount', 'status'];

  isMobile = false;

  /** Permission that authorizes viewing TDS data, matching the route guard. */
  private static readonly TDS_VIEW_PERMISSION = 'VENDOR_VIEW';

  constructor(
    private tdsService: TdsService,
    private vendorService: VendorService,
    private authService: AuthService,
    private breakpointObserver: BreakpointObserver
  ) {}

  ngOnInit(): void {
    this.authorized = this.authService.hasRole('SUPER_ADMIN')
      || this.authService.hasPermission(TdsListComponent.TDS_VIEW_PERMISSION);

    // Not authorized: show the indication and request no TDS data (Requirement 10.3).
    if (!this.authorized) {
      return;
    }

    this.breakpointObserver.observe(['(max-width: 768px)']).subscribe(result => {
      this.isMobile = result.matches;
    });
    this.loadVendors();
    this.load();
  }

  /** Populate the vendor picker; a failure here must not block the list. */
  private loadVendors(): void {
    this.vendorService.getActiveVendorsList()
      .pipe(catchError(() => of(null)))
      .subscribe(res => {
        if (res && res.success) {
          this.vendors = res.data ?? [];
        }
      });
  }

  /** Build the current filter, skipping absent/blank fields. */
  private buildFilter(): TdsFilterRequest {
    return {
      deductionStartDate: this.toIso(this.deductionStartDate),
      deductionEndDate: this.toIso(this.deductionEndDate),
      remittanceStartDate: this.toIso(this.remittanceStartDate),
      remittanceEndDate: this.toIso(this.remittanceEndDate),
      vendorId: this.vendorId,
      statuses: this.statuses.length > 0 ? this.statuses : undefined,
      tdsSection: this.tdsSection.trim() || undefined,
      financialYear: this.financialYear.trim() || undefined
    };
  }

  /**
   * Fetch the paginated lines and the summary together. Both must resolve
   * within {@link LOAD_TIMEOUT_MS} or the error state is shown instead of a
   * blank/partial screen (Requirement 10.4).
   */
  load(): void {
    const filter = this.buildFilter();
    this.loading = true;
    this.loadError = false;

    forkJoin({
      lines: this.tdsService.getTdsLines(filter, this.currentPage, this.pageSize),
      summary: this.tdsService.getSummary(filter)
    }).pipe(
      timeout(LOAD_TIMEOUT_MS),
      catchError(() => of(null))
    ).subscribe(result => {
      this.loading = false;

      if (!result || !result.lines.success || !result.summary.success) {
        this.loadError = true;
        this.lines = [];
        this.summary = null;
        this.totalElements = 0;
        return;
      }

      const paged = result.lines.data;
      this.lines = paged.content ?? [];
      this.totalElements = paged.totalElements ?? 0;
      this.summary = result.summary.data ?? null;
    });
  }

  /** Re-run the query from page 0 whenever a filter changes. */
  applyFilters(): void {
    this.currentPage = 0;
    this.load();
  }

  clearFilters(): void {
    this.deductionStartDate = null;
    this.deductionEndDate = null;
    this.remittanceStartDate = null;
    this.remittanceEndDate = null;
    this.vendorId = undefined;
    this.statuses = [];
    this.tdsSection = '';
    this.financialYear = '';
    this.applyFilters();
  }

  retry(): void {
    this.load();
  }

  onPageChange(event: PageEvent): void {
    this.currentPage = event.pageIndex;
    this.pageSize = event.pageSize;
    this.load();
  }

  /** Format a Date to ISO yyyy-MM-dd using local components (no UTC shift). */
  private toIso(date: Date | null): string | undefined {
    if (!date) {
      return undefined;
    }
    const y = date.getFullYear();
    const m = `${date.getMonth() + 1}`.padStart(2, '0');
    const d = `${date.getDate()}`.padStart(2, '0');
    return `${y}-${m}-${d}`;
  }
}
