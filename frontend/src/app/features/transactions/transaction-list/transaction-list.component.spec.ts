import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { HttpErrorResponse } from '@angular/common/http';
import { MatDialog } from '@angular/material/dialog';
import { Subject, of, throwError } from 'rxjs';

import { AuthService } from '@core/services/auth.service';
import { TransactionListComponent } from './transaction-list.component';
import { TransactionService } from '../services/transaction.service';
import {
  PagedResponse,
  TransactionSummary,
} from '../models/transaction.models';

/**
 * Tests for TransactionListComponent.
 *
 * Covers:
 *  - required columns (payment id, unit number, payer name, payer type,
 *    amount, payment date, payment mode, status, reference, receipt number)
 *    and the mat-paginator are rendered (Req 1.2 / 1.3 / 1.7)
 *  - the empty-state template is shown when the content is empty
 *    (Req 2.5 / 2.6 / 3.6)
 *  - the error banner retains the prior rows on a service failure
 *    (Req 1.7 / 2.6)
 */
describe('TransactionListComponent', () => {

  let fixture: ComponentFixture<TransactionListComponent>;
  let component: TransactionListComponent;

  let transactionService: jasmine.SpyObj<TransactionService>;
  let authService: jasmine.SpyObj<AuthService>;
  let dialog: jasmine.SpyObj<MatDialog>;

  /** Build a summary row with sensible defaults; override per test. */
  function makeSummary(overrides: Partial<TransactionSummary> = {}): TransactionSummary {
    return {
      paymentId: 101,
      unitNumber: 'A-101',
      payerName: 'Jane Doe',
      payerType: 'OWNER',
      amount: 1500,
      paymentDate: '2024-01-15',
      paymentMode: 'UPI',
      status: 'SUCCESS',
      transactionId: 'TXN-9001',
      receiptNumber: 'RCPT-5001',
      ...overrides,
    };
  }

  /** Build a paged response wrapping the given content. */
  function makePage(content: TransactionSummary[]): PagedResponse<TransactionSummary> {
    return {
      content,
      page: 0,
      size: 50,
      totalElements: content.length,
      totalPages: content.length === 0 ? 0 : 1,
      last: true,
    };
  }

  function setup(): void {
    fixture = TestBed.createComponent(TransactionListComponent);
    component = fixture.componentInstance;
  }

  beforeEach(async () => {
    transactionService = jasmine.createSpyObj<TransactionService>('TransactionService', [
      'listTransactions',
      'getTransaction',
    ]);
    authService = jasmine.createSpyObj<AuthService>('AuthService', ['hasAnyRole']);
    dialog = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);

    // Default: member (non-admin), so the deterministic 50-based page sizing applies.
    authService.hasAnyRole.and.returnValue(false);

    await TestBed.configureTestingModule({
      imports: [TransactionListComponent, NoopAnimationsModule],
      providers: [
        { provide: TransactionService, useValue: transactionService },
        { provide: AuthService, useValue: authService },
        { provide: MatDialog, useValue: dialog },
      ],
    }).compileComponents();
  });

  it('renders every required column header and the paginator when rows are present', () => {
    transactionService.listTransactions.and.returnValue(of(makePage([makeSummary()])));
    setup();
    fixture.detectChanges(); // ngOnInit -> load()

    const host: HTMLElement = fixture.nativeElement;

    // All ten required columns are declared in display order (Req 1.2).
    expect(component.displayedColumns).toEqual([
      'paymentId', 'unitNumber', 'payerName', 'payerType', 'amount',
      'paymentDate', 'paymentMode', 'status', 'reference', 'receiptNumber',
    ]);

    // Header cells are rendered for each required column.
    const headerText = Array.from(host.querySelectorAll('th.mat-mdc-header-cell'))
      .map(el => (el.textContent ?? '').trim());
    expect(headerText).toEqual([
      'Payment ID', 'Unit', 'Payer', 'Payer type', 'Amount',
      'Date', 'Mode', 'Status', 'Reference', 'Receipt',
    ]);

    // The paginator is present.
    expect(host.querySelector('mat-paginator')).toBeTruthy();
  });

  it('renders a data row reflecting the fetched transaction', () => {
    transactionService.listTransactions.and.returnValue(
      of(makePage([makeSummary({ paymentId: 777, unitNumber: 'B-202' })])),
    );
    setup();
    fixture.detectChanges();

    const host: HTMLElement = fixture.nativeElement;
    const dataRows = host.querySelectorAll('tr.transaction-row');
    expect(dataRows.length).toBe(1);

    const rowText = (dataRows[0].textContent ?? '');
    expect(rowText).toContain('777');
    expect(rowText).toContain('B-202');
    expect(component.dataSource.length).toBe(1);
  });

  it('shows the empty-state template when the content is empty', () => {
    transactionService.listTransactions.and.returnValue(of(makePage([])));
    setup();
    fixture.detectChanges();

    const host: HTMLElement = fixture.nativeElement;
    const emptyState = host.querySelector('.empty-state');
    expect(emptyState).toBeTruthy();
    expect((emptyState?.textContent ?? '')).toContain('No transactions match the current filters.');

    // No data rows and no error banner in the empty (success) case.
    expect(host.querySelectorAll('tr.transaction-row').length).toBe(0);
    expect(host.querySelector('.error-banner')).toBeNull();
  });

  it('retains the prior rows and shows an error banner on service failure', fakeAsync(() => {
    // First load succeeds with one row; subsequent load fails.
    const results = new Subject<PagedResponse<TransactionSummary>>();
    transactionService.listTransactions.and.returnValue(results.asObservable());

    setup();
    fixture.detectChanges(); // triggers first load(), still pending

    results.next(makePage([makeSummary({ paymentId: 555 })]));
    tick();
    fixture.detectChanges();

    expect(component.dataSource.length).toBe(1);
    expect(component.dataSource[0].paymentId).toBe(555);

    // Next fetch (e.g. a page change) fails with a 500 error.
    transactionService.listTransactions.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 500, statusText: 'Server Error' })),
    );
    component.onPage({ pageIndex: 1, pageSize: 50, length: 1 });
    tick();
    fixture.detectChanges();

    // Rows are retained (Req 1.7 / 2.6).
    expect(component.dataSource.length).toBe(1);
    expect(component.dataSource[0].paymentId).toBe(555);

    // The list-level error banner is shown.
    const host: HTMLElement = fixture.nativeElement;
    const banner = host.querySelector('.error-banner');
    expect(banner).toBeTruthy();
    expect(component.errorMessage).toBeTruthy();

    // The previously-rendered data row is still in the DOM.
    expect(host.querySelectorAll('tr.transaction-row').length).toBe(1);
  }));

  it('surfaces the server message from the error envelope for non-validation failures', fakeAsync(() => {
    transactionService.listTransactions.and.returnValue(of(makePage([makeSummary()])));
    setup();
    fixture.detectChanges();
    expect(component.dataSource.length).toBe(1);

    transactionService.listTransactions.and.returnValue(
      throwError(() => new HttpErrorResponse({
        status: 500,
        error: { message: 'Backend exploded' },
      })),
    );
    component.onPage({ pageIndex: 1, pageSize: 50, length: 1 });
    tick();
    fixture.detectChanges();

    expect(component.errorMessage).toBe('Backend exploded');
    expect(component.dataSource.length).toBe(1); // rows retained
  }));

  it('uses member page sizing (50-based) when the caller is not an administrator', () => {
    authService.hasAnyRole.and.returnValue(false);
    transactionService.listTransactions.and.returnValue(of(makePage([])));
    setup();
    fixture.detectChanges();

    expect(component.isAdmin).toBeFalse();
    expect(component.pageSize).toBe(50);
    expect(component.pageSizeOptions).toEqual([50, 100]);
  });

  it('uses admin page sizing (25-based) when the caller has a society-wide role', () => {
    authService.hasAnyRole.and.returnValue(true);
    transactionService.listTransactions.and.returnValue(of(makePage([])));
    setup();
    fixture.detectChanges();

    expect(component.isAdmin).toBeTrue();
    expect(component.pageSize).toBe(25);
    expect(component.pageSizeOptions).toEqual([25, 50, 100]);
  });
});
