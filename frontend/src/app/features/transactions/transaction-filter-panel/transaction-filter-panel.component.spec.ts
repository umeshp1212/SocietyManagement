import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';

import { TransactionFilterPanelComponent } from './transaction-filter-panel.component';
import { AuthService } from '@core/services/auth.service';
import { TransactionFilter } from '../models/transaction.models';

/**
 * Tests for TransactionFilterPanelComponent.
 *
 * Covers the behaviours called out in task 13.2:
 *  - emits an AND-combined {@link TransactionFilter} on apply
 *  - client-side validation mirrors the server: start <= end guard and the
 *    unitSearch (<=50) / reference (<=100) max-length guards
 *  - preserves the prior applied filter when the server rejects a change
 *
 * Validates: Requirements 3.4, 6.5, 7.1, 7.6, 9.3
 */
describe('TransactionFilterPanelComponent', () => {
  let component: TransactionFilterPanelComponent;
  let fixture: ComponentFixture<TransactionFilterPanelComponent>;
  let authServiceStub: { hasAnyRole: jasmine.Spy };

  beforeEach(async () => {
    // Default the stub to a non-admin so admin-only fields stay hidden unless a
    // test opts in via the isAdmin input.
    authServiceStub = { hasAnyRole: jasmine.createSpy('hasAnyRole').and.returnValue(false) };

    await TestBed.configureTestingModule({
      imports: [TransactionFilterPanelComponent],
      providers: [
        provideNoopAnimations(),
        { provide: AuthService, useValue: authServiceStub },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TransactionFilterPanelComponent);
    component = fixture.componentInstance;
  });

  function initAsAdmin(): void {
    component.isAdmin = true;
    fixture.detectChanges();
  }

  function initAsMember(): void {
    fixture.detectChanges();
  }

  describe('AND-combined filter emission (Req 7.1)', () => {
    it('emits a single filter combining every set field on apply', () => {
      initAsAdmin();
      const emitted: TransactionFilter[] = [];
      component.filterChange.subscribe((f) => emitted.push(f));

      component.filterForm.patchValue({
        startDate: new Date('2024-01-01T00:00:00'),
        endDate: new Date('2024-01-31T00:00:00'),
        paymentMode: 'UPI',
        statuses: ['SUCCESS', 'VERIFIED'],
        payerType: 'OWNER',
        unitSearch: 'A-101',
        reference: 'RCPT-9',
      });

      component.onApply();

      expect(emitted.length).toBe(1);
      const filter = emitted[0];
      // All fields are combined into one filter object (server ANDs them).
      expect(filter.startDate).toBe('2024-01-01');
      expect(filter.endDate).toBe('2024-01-31');
      expect(filter.paymentMode).toBe('UPI');
      expect(filter.statuses).toEqual(['SUCCESS', 'VERIFIED']);
      expect(filter.payerType).toBe('OWNER');
      expect(filter.unitSearch).toBe('A-101');
      expect(filter.reference).toBe('RCPT-9');
    });

    it('omits absent/blank fields so they act as identity in the AND', () => {
      initAsAdmin();
      let filter: TransactionFilter | undefined;
      component.filterChange.subscribe((f) => (filter = f));

      component.filterForm.patchValue({
        paymentMode: 'CASH',
        unitSearch: '   ',
        reference: '',
        statuses: [],
      });

      component.onApply();

      expect(filter).toBeDefined();
      expect(filter!.paymentMode).toBe('CASH');
      // Blank/empty inputs must not appear in the emitted filter.
      expect('startDate' in filter!).toBe(false);
      expect('endDate' in filter!).toBe(false);
      expect('statuses' in filter!).toBe(false);
      expect('unitSearch' in filter!).toBe(false);
      expect('reference' in filter!).toBe(false);
    });

    it('trims whitespace around text filters before emitting', () => {
      initAsAdmin();
      let filter: TransactionFilter | undefined;
      component.filterChange.subscribe((f) => (filter = f));

      component.filterForm.patchValue({ unitSearch: '  B-7  ', reference: '  TXN1  ' });
      component.onApply();

      expect(filter!.unitSearch).toBe('B-7');
      expect(filter!.reference).toBe('TXN1');
    });

    it('excludes admin-only filters when the user is a member', () => {
      initAsMember();
      let filter: TransactionFilter | undefined;
      component.filterChange.subscribe((f) => (filter = f));

      // Even if the underlying controls hold values, members must not send
      // payerType / unitSearch (backend enforces scope regardless).
      component.filterForm.patchValue({
        payerType: 'TENANT',
        unitSearch: 'A-101',
        reference: 'REF-1',
      });
      component.onApply();

      expect('payerType' in filter!).toBe(false);
      expect('unitSearch' in filter!).toBe(false);
      expect(filter!.reference).toBe('REF-1');
    });
  });

  describe('date-range validation mirrors server start <= end (Req 3.4)', () => {
    it('marks the form invalid and does not emit when start is after end', () => {
      initAsAdmin();
      const emitSpy = jasmine.createSpy('emit');
      component.filterChange.subscribe(emitSpy);

      component.filterForm.patchValue({
        startDate: new Date('2024-02-10T00:00:00'),
        endDate: new Date('2024-02-01T00:00:00'),
      });

      expect(component.filterForm.hasError('dateRange')).toBe(true);
      expect(component.filterForm.invalid).toBe(true);

      component.onApply();
      expect(emitSpy).not.toHaveBeenCalled();
    });

    it('accepts an equal start and end date (inclusive boundary)', () => {
      initAsAdmin();
      let filter: TransactionFilter | undefined;
      component.filterChange.subscribe((f) => (filter = f));

      const day = new Date('2024-03-15T00:00:00');
      component.filterForm.patchValue({ startDate: day, endDate: day });

      expect(component.filterForm.hasError('dateRange')).toBe(false);

      component.onApply();
      expect(filter!.startDate).toBe('2024-03-15');
      expect(filter!.endDate).toBe('2024-03-15');
    });

    it('accepts a normal start-before-end range', () => {
      initAsAdmin();
      component.filterForm.patchValue({
        startDate: new Date('2024-01-01T00:00:00'),
        endDate: new Date('2024-12-31T00:00:00'),
      });

      expect(component.filterForm.hasError('dateRange')).toBe(false);
      expect(component.filterForm.valid).toBe(true);
    });
  });

  describe('max-length validation mirrors server (Req 6.5, 9.3)', () => {
    it('rejects a unitSearch longer than 50 characters', () => {
      initAsAdmin();
      const control = component.filterForm.get('unitSearch')!;

      control.setValue('x'.repeat(51));
      expect(control.hasError('maxlength')).toBe(true);
      expect(component.filterForm.invalid).toBe(true);
    });

    it('accepts a unitSearch of exactly 50 characters', () => {
      initAsAdmin();
      const control = component.filterForm.get('unitSearch')!;

      control.setValue('x'.repeat(50));
      expect(control.hasError('maxlength')).toBe(false);
    });

    it('rejects a reference longer than 100 characters', () => {
      initAsAdmin();
      const control = component.filterForm.get('reference')!;

      control.setValue('y'.repeat(101));
      expect(control.hasError('maxlength')).toBe(true);
      expect(component.filterForm.invalid).toBe(true);
    });

    it('accepts a reference of exactly 100 characters', () => {
      initAsAdmin();
      const control = component.filterForm.get('reference')!;

      control.setValue('y'.repeat(100));
      expect(control.hasError('maxlength')).toBe(false);
    });

    it('exposes the max-length constants that mirror the server bounds', () => {
      initAsAdmin();
      expect(component.UNIT_SEARCH_MAX).toBe(50);
      expect(component.REFERENCE_MAX).toBe(100);
    });
  });

  describe('server rejection preserves prior filter (Req 7.6)', () => {
    it('keeps the last applied filter and surfaces the server message', () => {
      initAsAdmin();

      // First apply succeeds and becomes the active filter.
      component.filterForm.patchValue({ paymentMode: 'UPI' });
      component.onApply();
      const priorFilter = component.getLastAppliedFilter();
      expect(priorFilter.paymentMode).toBe('UPI');

      // The server later rejects a (different) submission.
      component.setServerError('startDate must be on or before endDate');

      expect(component.serverError).toBe('startDate must be on or before endDate');
      // The prior applied filter is untouched by the rejection.
      expect(component.getLastAppliedFilter()).toBe(priorFilter);
      expect(component.getLastAppliedFilter().paymentMode).toBe('UPI');
    });

    it('clears the server error on the next successful apply', () => {
      initAsAdmin();
      component.setServerError('something went wrong');
      expect(component.serverError).toBe('something went wrong');

      component.filterForm.patchValue({ paymentMode: 'CASH' });
      component.onApply();

      expect(component.serverError).toBeNull();
      expect(component.getLastAppliedFilter().paymentMode).toBe('CASH');
    });

    it('does not overwrite the applied filter when a rejected apply is invalid', () => {
      initAsAdmin();

      // Establish a valid applied filter.
      component.filterForm.patchValue({ reference: 'REF-1' });
      component.onApply();
      expect(component.getLastAppliedFilter().reference).toBe('REF-1');

      // User edits into an invalid state; apply must be a no-op.
      component.filterForm.patchValue({
        startDate: new Date('2024-05-10T00:00:00'),
        endDate: new Date('2024-05-01T00:00:00'),
      });
      component.onApply();

      expect(component.getLastAppliedFilter().reference).toBe('REF-1');
    });
  });

  describe('clear resets to an empty filter', () => {
    it('emits an empty filter and resets the applied filter on clear', () => {
      initAsAdmin();
      component.filterForm.patchValue({ paymentMode: 'UPI', reference: 'X' });
      component.onApply();
      expect(component.getLastAppliedFilter().paymentMode).toBe('UPI');

      let cleared: TransactionFilter | undefined;
      component.filterChange.subscribe((f) => (cleared = f));
      component.onClear();

      expect(cleared).toEqual({});
      expect(component.getLastAppliedFilter()).toEqual({});
      expect(component.serverError).toBeNull();
    });
  });
});
