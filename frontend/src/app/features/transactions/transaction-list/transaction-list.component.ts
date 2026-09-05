import { Component, OnInit, ViewChild } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';

import { AuthService } from '@core/services/auth.service';
import {
  PagedResponse,
  TransactionFilter,
  TransactionSummary,
} from '../models/transaction.models';
import { TransactionService } from '../services/transaction.service';
import { TransactionFilterPanelComponent } from '../transaction-filter-panel/transaction-filter-panel.component';
import {
  TransactionDetailDialogComponent,
  TransactionDetailDialogData,
} from '../transaction-detail-dialog/transaction-detail-dialog.component';

/**
 * Transaction Page orchestrator.
 *
 * Owns the paginated, filterable list of transactions:
 *  - a `mat-table` with the required columns (Req 1.2)
 *  - a `mat-paginator` whose page-size options reflect the caller's role —
 *    25-based for administrators (Req 1.4), 50-based for members (Req 2.1)
 *  - an empty-state template when there is no content (Req 1.3 / 2.5 / 3.6)
 *  - an error banner that leaves the current rows intact on service failure
 *    (Req 1.7 / 2.6)
 *
 * It composes {@link TransactionFilterPanelComponent}: it subscribes to the
 * panel's `filterChange` output, re-queries on every change, and calls the
 * panel's `setServerError` on a server validation (400) error while retaining
 * the prior rows (Req 7.6). A row click opens
 * {@link TransactionDetailDialogComponent}, passing `{ paymentId }` as the
 * dialog data.
 *
 * Admin vs member role is derived from the app's {@link AuthService} using the
 * same society-wide roles the filter panel uses; the result feeds both the
 * paginator page-size options and the `isAdmin` input passed to the panel.
 */
@Component({
  selector: 'app-transaction-list',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatPaginatorModule,
    MatCardModule,
    MatIconModule,
    MatProgressBarModule,
    MatDialogModule,
    CurrencyPipe,
    DatePipe,
    TransactionFilterPanelComponent,
  ],
  template: `
    <div class="transaction-page">
      <h1 class="page-title">Transactions</h1>

      <!-- Filter panel (composed; drives re-query via filterChange) -->
      <app-transaction-filter-panel
        [isAdmin]="isAdmin"
        (filterChange)="onFilterChange($event)">
      </app-transaction-filter-panel>

      <mat-card class="table-card">
        <!-- Loading indicator -->
        <mat-progress-bar *ngIf="loading" mode="indeterminate"></mat-progress-bar>

        <!-- Error banner: current rows are retained (Req 1.7 / 2.6) -->
        <div class="error-banner" *ngIf="errorMessage" role="alert">
          <mat-icon color="warn">error_outline</mat-icon>
          <span>{{ errorMessage }}</span>
        </div>

        <div class="table-container">
          <table mat-table [dataSource]="dataSource" class="transaction-table">

            <!-- Payment id -->
            <ng-container matColumnDef="paymentId">
              <th mat-header-cell *matHeaderCellDef>Payment ID</th>
              <td mat-cell *matCellDef="let row">{{ row.paymentId }}</td>
            </ng-container>

            <!-- Unit number -->
            <ng-container matColumnDef="unitNumber">
              <th mat-header-cell *matHeaderCellDef>Unit</th>
              <td mat-cell *matCellDef="let row">{{ display(row.unitNumber) }}</td>
            </ng-container>

            <!-- Payer name -->
            <ng-container matColumnDef="payerName">
              <th mat-header-cell *matHeaderCellDef>Payer</th>
              <td mat-cell *matCellDef="let row">{{ display(row.payerName) }}</td>
            </ng-container>

            <!-- Payer type -->
            <ng-container matColumnDef="payerType">
              <th mat-header-cell *matHeaderCellDef>Payer type</th>
              <td mat-cell *matCellDef="let row">{{ display(row.payerType) }}</td>
            </ng-container>

            <!-- Amount -->
            <ng-container matColumnDef="amount">
              <th mat-header-cell *matHeaderCellDef class="num">Amount</th>
              <td mat-cell *matCellDef="let row" class="num">
                {{ row.amount | currency:'INR' }}
              </td>
            </ng-container>

            <!-- Payment date -->
            <ng-container matColumnDef="paymentDate">
              <th mat-header-cell *matHeaderCellDef>Date</th>
              <td mat-cell *matCellDef="let row">
                {{ row.paymentDate | date:'mediumDate' }}
              </td>
            </ng-container>

            <!-- Payment mode -->
            <ng-container matColumnDef="paymentMode">
              <th mat-header-cell *matHeaderCellDef>Mode</th>
              <td mat-cell *matCellDef="let row">{{ display(row.paymentMode) }}</td>
            </ng-container>

            <!-- Status -->
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>Status</th>
              <td mat-cell *matCellDef="let row">{{ display(row.status) }}</td>
            </ng-container>

            <!-- Reference (transaction id) -->
            <ng-container matColumnDef="reference">
              <th mat-header-cell *matHeaderCellDef>Reference</th>
              <td mat-cell *matCellDef="let row">{{ display(row.transactionId) }}</td>
            </ng-container>

            <!-- Receipt number -->
            <ng-container matColumnDef="receiptNumber">
              <th mat-header-cell *matHeaderCellDef>Receipt</th>
              <td mat-cell *matCellDef="let row">{{ display(row.receiptNumber) }}</td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: displayedColumns;"
                class="transaction-row" (click)="openDetail(row)"></tr>
          </table>

          <!-- Empty state (Req 1.3 / 2.5 / 3.6) -->
          <div class="empty-state" *ngIf="!loading && dataSource.length === 0">
            <mat-icon>receipt_long</mat-icon>
            <p>No transactions match the current filters.</p>
          </div>
        </div>

        <mat-paginator
          [length]="totalElements"
          [pageSize]="pageSize"
          [pageIndex]="pageIndex"
          [pageSizeOptions]="pageSizeOptions"
          (page)="onPage($event)"
          showFirstLastButtons>
        </mat-paginator>
      </mat-card>
    </div>
  `,
  styles: [`
    .transaction-page { padding: 16px; }
    .page-title { margin: 0 0 16px; font-size: 1.5rem; }
    .table-card { padding: 0; overflow: hidden; }
    .table-container { position: relative; overflow-x: auto; }
    .transaction-table { width: 100%; }
    .transaction-row { cursor: pointer; }
    .transaction-row:hover { background: rgba(0, 0, 0, 0.04); }
    .num { text-align: right; }
    .error-banner {
      display: flex; align-items: center; gap: 8px;
      padding: 12px 16px; color: #b00020;
      background: #fdecea; border-bottom: 1px solid #f5c6cb;
    }
    .empty-state {
      display: flex; flex-direction: column; align-items: center;
      gap: 8px; padding: 48px 16px; color: #777;
    }
    .empty-state mat-icon {
      font-size: 40px; height: 40px; width: 40px;
    }
  `],
})
export class TransactionListComponent implements OnInit {

  /** Society-wide roles that receive admin behaviour (mirrors the filter panel). */
  private static readonly ADMIN_ROLES = ['SUPER_ADMIN', 'CHAIRMAN', 'SECRETARY', 'TREASURER'];

  /** Columns rendered by the mat-table, in display order (Req 1.2). */
  readonly displayedColumns: string[] = [
    'paymentId', 'unitNumber', 'payerName', 'payerType', 'amount',
    'paymentDate', 'paymentMode', 'status', 'reference', 'receiptNumber',
  ];

  /** Explicit placeholder for blank/absent cell values. */
  private readonly placeholder = '\u2014'; // em dash "—"

  /** Whether the current caller has a society-wide role. */
  isAdmin = false;

  /** Rows currently displayed. Retained across errors (Req 1.7 / 2.6). */
  dataSource: TransactionSummary[] = [];

  loading = false;
  errorMessage: string | null = null;

  // Pagination state.
  pageIndex = 0;
  pageSize = 50;
  totalElements = 0;
  pageSizeOptions: number[] = [50, 100];

  /** Latest filter applied via the filter panel. */
  private currentFilter: TransactionFilter = {};

  @ViewChild(TransactionFilterPanelComponent)
  private filterPanel?: TransactionFilterPanelComponent;

  constructor(
    private readonly transactionService: TransactionService,
    private readonly authService: AuthService,
    private readonly dialog: MatDialog,
  ) {}

  ngOnInit(): void {
    this.isAdmin = this.authService.hasAnyRole(TransactionListComponent.ADMIN_ROLES);
    // Page-size options reflect role: 25-based for admin, 50-based for member.
    if (this.isAdmin) {
      this.pageSizeOptions = [25, 50, 100];
      this.pageSize = 25;
    } else {
      this.pageSizeOptions = [50, 100];
      this.pageSize = 50;
    }
    this.load();
  }

  /** Re-query when the filter panel emits a new filter (Req 7.1). */
  onFilterChange(filter: TransactionFilter): void {
    this.currentFilter = filter;
    this.pageIndex = 0; // a new filter resets to the first page
    this.load();
  }

  /** Handle paginator page/size changes. */
  onPage(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.load();
  }

  /** Open the detail dialog for the clicked row (Req 8). */
  openDetail(row: TransactionSummary): void {
    const data: TransactionDetailDialogData = { paymentId: row.paymentId };
    this.dialog.open(TransactionDetailDialogComponent, {
      data,
      width: '640px',
      maxWidth: '95vw',
    });
  }

  /** Fetch the current page of transactions with the active filter. */
  private load(): void {
    this.loading = true;
    this.errorMessage = null;

    const query: TransactionFilter = {
      ...this.currentFilter,
      page: this.pageIndex,
      size: this.pageSize,
    };

    this.transactionService.listTransactions(query).subscribe({
      next: (result: PagedResponse<TransactionSummary>) => {
        this.dataSource = result.content ?? [];
        this.totalElements = result.totalElements ?? 0;
        this.pageIndex = result.page ?? this.pageIndex;
        if (result.size) {
          this.pageSize = result.size;
        }
        this.loading = false;
      },
      error: (err: unknown) => {
        this.loading = false;
        this.handleError(err);
      },
    });
  }

  /**
   * Handle a service failure. The current rows are ALWAYS retained (Req 1.7 /
   * 2.6). A server validation error (400) is routed to the filter panel via
   * {@link TransactionFilterPanelComponent.setServerError} (Req 7.6); other
   * failures surface in the list-level error banner.
   */
  private handleError(err: unknown): void {
    const status = err instanceof HttpErrorResponse ? err.status : 0;
    const serverMessage =
      err instanceof HttpErrorResponse ? this.extractMessage(err) : null;

    if (status === 400) {
      const message =
        serverMessage ?? 'The applied filters are invalid. Please adjust and try again.';
      // Keep the prior rows; surface the message on the filter panel.
      if (this.filterPanel) {
        this.filterPanel.setServerError(message);
      } else {
        this.errorMessage = message;
      }
      return;
    }

    this.errorMessage =
      serverMessage ?? 'Transactions could not be loaded. Showing the last results.';
  }

  /** Pull the human-readable message out of the ApiResponse error envelope. */
  private extractMessage(err: HttpErrorResponse): string | null {
    const body = err.error;
    if (body && typeof body === 'object' && typeof body.message === 'string') {
      return body.message;
    }
    return null;
  }

  /** Render a value, falling back to the placeholder when blank/absent. */
  display(value: string | null | undefined): string {
    if (value === null || value === undefined) {
      return this.placeholder;
    }
    const text = String(value).trim();
    return text.length > 0 ? text : this.placeholder;
  }
}
