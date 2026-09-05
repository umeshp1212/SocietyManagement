import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';

import {
  EMPTY_PLACEHOLDER,
  TransactionDetailDialogComponent,
  TransactionDetailDialogData,
} from './transaction-detail-dialog.component';
import { TransactionService } from '../services/transaction.service';
import { TransactionDetail } from '../models/transaction.models';

/**
 * Tests for TransactionDetailDialogComponent (Task 13.3).
 *
 * Covers:
 *  - Renders every detail field, using the explicit placeholder for null values (Req 8.1)
 *  - Verification block shown only when verified (Req 8.3)
 *  - Reversal block shown only when reversed (Req 8.4)
 *  - Correct error message with NO detail fields for 403 / 404 / 500 (Req 8.5/8.6/8.7)
 */
describe('TransactionDetailDialogComponent', () => {
  let serviceSpy: jasmine.SpyObj<TransactionService>;

  /** A fully-populated detail record used as the "happy path" baseline. */
  function fullDetail(overrides: Partial<TransactionDetail> = {}): TransactionDetail {
    return {
      paymentId: 42,
      unitNumber: 'A-101',
      payerName: 'Jane Doe',
      payerType: 'OWNER',
      amount: 1500,
      paymentDate: '2024-03-15',
      paymentMode: 'UPI',
      status: 'SUCCESS',
      transactionId: 'TXN-9988',
      receiptNumber: 'RCPT-0001',
      originalAmount: 2000,
      discountAmount: 500,
      discountPercent: 25,
      remarks: 'Paid in full',
      verifiedOn: undefined,
      verifiedBy: undefined,
      reversedOn: undefined,
      reversedBy: undefined,
      reversalReason: undefined,
      ...overrides,
    };
  }

  async function setup(
    result: { detail?: TransactionDetail; error?: unknown },
    data: TransactionDetailDialogData = { paymentId: 42 },
  ): Promise<ComponentFixture<TransactionDetailDialogComponent>> {
    serviceSpy = jasmine.createSpyObj<TransactionService>('TransactionService', [
      'getTransaction',
    ]);
    if (result.error !== undefined) {
      serviceSpy.getTransaction.and.returnValue(throwError(() => result.error));
    } else {
      serviceSpy.getTransaction.and.returnValue(of(result.detail!));
    }

    await TestBed.configureTestingModule({
      imports: [TransactionDetailDialogComponent],
      providers: [
        provideNoopAnimations(),
        { provide: TransactionService, useValue: serviceSpy },
        { provide: MatDialogRef, useValue: { close: () => {} } },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(TransactionDetailDialogComponent);
    fixture.detectChanges();
    return fixture;
  }

  function text(fixture: ComponentFixture<TransactionDetailDialogComponent>): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  function definitions(fixture: ComponentFixture<TransactionDetailDialogComponent>): string[] {
    return Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('.tdd-body dd'),
    ).map(dd => (dd.textContent ?? '').trim());
  }

  it('should create and request the detail by the injected payment id', async () => {
    const fixture = await setup({ detail: fullDetail() });
    expect(fixture.componentInstance).toBeTruthy();
    expect(serviceSpy.getTransaction).toHaveBeenCalledWith(42);
  });

  describe('field rendering (Req 8.1)', () => {
    it('renders the core detail fields with their values', async () => {
      const fixture = await setup({ detail: fullDetail() });
      const body = text(fixture);

      expect(body).toContain('Jane Doe');
      expect(body).toContain('OWNER');
      expect(body).toContain('A-101');
      expect(body).toContain('UPI');
      expect(body).toContain('TXN-9988');
      expect(body).toContain('RCPT-0001');
      expect(body).toContain('SUCCESS');
      expect(body).toContain('Paid in full');
      expect(body).toContain('25%');
    });

    it('shows the explicit placeholder for null/blank fields rather than omitting them', async () => {
      const fixture = await setup({
        detail: fullDetail({
          payerName: null as unknown as string,
          remarks: undefined,
          transactionId: '',
          receiptNumber: '   ',
          originalAmount: undefined,
          discountAmount: undefined,
          discountPercent: undefined,
        }),
      });

      const dds = definitions(fixture);
      // Every detail row must still be present.
      const dtCount = (fixture.nativeElement as HTMLElement).querySelectorAll('.tdd-body dt')
        .length;
      expect(dds.length).toBe(dtCount);
      // The placeholder must appear for the missing fields.
      expect(dds).toContain(EMPTY_PLACEHOLDER);
      expect(text(fixture)).toContain(EMPTY_PLACEHOLDER);
    });

    it('uses an em dash as the placeholder constant', () => {
      expect(EMPTY_PLACEHOLDER).toBe('\u2014');
    });
  });

  describe('verification block (Req 8.3)', () => {
    it('is hidden when the transaction is not verified', async () => {
      const fixture = await setup({ detail: fullDetail() });
      expect(text(fixture)).not.toContain('Verification');
    });

    it('is shown with verifier details when the transaction is verified', async () => {
      const fixture = await setup({
        detail: fullDetail({
          status: 'VERIFIED',
          verifiedOn: '2024-03-16T10:30:00',
          verifiedBy: 'admin-user',
        }),
      });
      const body = text(fixture);
      expect(body).toContain('Verification');
      expect(body).toContain('admin-user');
    });
  });

  describe('reversal block (Req 8.4)', () => {
    it('is hidden when the transaction is not reversed', async () => {
      const fixture = await setup({ detail: fullDetail() });
      expect(text(fixture)).not.toContain('Reversal');
    });

    it('is shown with reverser details and reason when the transaction is reversed', async () => {
      const fixture = await setup({
        detail: fullDetail({
          status: 'REVERSED',
          reversedOn: '2024-03-17T09:00:00',
          reversedBy: 'finance-user',
          reversalReason: 'Duplicate payment',
        }),
      });
      const body = text(fixture);
      expect(body).toContain('Reversal');
      expect(body).toContain('finance-user');
      expect(body).toContain('Duplicate payment');
    });
  });

  describe('error handling without partial fields (Req 8.5/8.6/8.7)', () => {
    function assertNoDetailFields(
      fixture: ComponentFixture<TransactionDetailDialogComponent>,
    ): void {
      const body = (fixture.nativeElement as HTMLElement).querySelector('.tdd-body');
      expect(body).toBeNull();
      // A field label that only exists in the detail body must not appear.
      expect(text(fixture)).not.toContain('Payer name');
      expect(fixture.componentInstance.detail).toBeNull();
    }

    it('shows an access-denied message for 403 and no detail fields', async () => {
      const fixture = await setup({
        error: new HttpErrorResponse({ status: 403, statusText: 'Forbidden' }),
      });
      const body = text(fixture);
      expect(body.toLowerCase()).toContain('access denied');
      assertNoDetailFields(fixture);
    });

    it('shows a not-found message for 404 and no detail fields', async () => {
      const fixture = await setup({
        error: new HttpErrorResponse({ status: 404, statusText: 'Not Found' }),
      });
      const body = text(fixture);
      expect(body.toLowerCase()).toContain('not found');
      assertNoDetailFields(fixture);
    });

    it('shows a could-not-load message for 500 and no detail fields', async () => {
      const fixture = await setup({
        error: new HttpErrorResponse({ status: 500, statusText: 'Server Error' }),
      });
      const body = text(fixture);
      expect(body.toLowerCase()).toContain('could not be loaded');
      assertNoDetailFields(fixture);
    });

    it('shows a could-not-load message for a non-HTTP failure and no detail fields', async () => {
      const fixture = await setup({ error: new Error('boom') });
      const body = text(fixture);
      expect(body.toLowerCase()).toContain('could not be loaded');
      assertNoDetailFields(fixture);
    });
  });
});
