import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { HttpErrorResponse } from '@angular/common/http';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';

import { OwnerService } from '@core/services/owner.service';
import { ApiResponse } from '@core/models/api-response.model';
import { Owner, SendOwnerEmailRequest, SendReport } from '@core/models/owner.model';
import { OwnerEmailComponent } from './owner-email.component';

/**
 * Tests for OwnerEmailComponent.
 *
 * Covers:
 *  - default recipient scope is ALL (Req 2.1)
 *  - SELECTED scope with no owners selected blocks submission and retains
 *    scope/content (Req 2.5)
 *  - ALL scope with zero active owners blocks submission and retains
 *    content (Req 2.6)
 *  - subject/body bounds + validation messages (Req 3.1, 3.2, 3.3, 3.4)
 *  - results panel counts + not-emailed list (Req 7.4, 7.5)
 *  - 30s timeout surfaces an error, retains content, allows retry (Req 7.6)
 *  - active-owner load failure is handled without discarding the view (Req 1.4)
 */
describe('OwnerEmailComponent', () => {
  let fixture: ComponentFixture<OwnerEmailComponent>;
  let component: OwnerEmailComponent;

  let ownerService: jasmine.SpyObj<OwnerService>;
  let snackBar: jasmine.Spy;

  function makeOwner(overrides: Partial<Owner> = {}): Owner {
    return {
      ownerId: 1,
      fullName: 'Jane Owner',
      contactNumber: '9990001111',
      email: 'jane@example.com',
      status: 'ACTIVE',
      ...overrides,
    };
  }

  function ok<T>(data: T): ApiResponse<T> {
    return { success: true, message: 'ok', data, timestamp: '2024-01-01T00:00:00Z' };
  }

  function makeReport(overrides: Partial<SendReport> = {}): SendReport {
    return {
      totalAttempted: 2,
      sentCount: 1,
      notEmailedCount: 1,
      mailConfigured: true,
      notEmailed: [{ ownerId: 2, ownerName: 'No Email Owner', reason: 'MISSING_EMAIL' }],
      ...overrides,
    };
  }

  /** Create the component; the caller controls the getActiveOwnersList result first. */
  function setup(): void {
    fixture = TestBed.createComponent(OwnerEmailComponent);
    component = fixture.componentInstance;
    // Spy on the exact MatSnackBar the component injected, so block and result
    // notifications are captured regardless of injector resolution.
    snackBar = spyOn((component as unknown as { snackBar: MatSnackBar }).snackBar, 'open').and.stub();
  }

  /** Enter a valid subject/body so recipient-level rules are what's under test. */
  function fillValidMessage(): void {
    component.form.setValue({ subject: 'Notice', body: 'Please note the AGM.' });
  }

  beforeEach(async () => {
    ownerService = jasmine.createSpyObj<OwnerService>('OwnerService', [
      'getActiveOwnersList',
      'sendOwnerEmail',
    ]);

    // Default: two active owners load successfully.
    ownerService.getActiveOwnersList.and.returnValue(
      of(ok([makeOwner({ ownerId: 1 }), makeOwner({ ownerId: 2, fullName: 'John Owner', email: 'john@example.com' })])),
    );

    await TestBed.configureTestingModule({
      imports: [OwnerEmailComponent, NoopAnimationsModule],
      providers: [
        { provide: OwnerService, useValue: ownerService },
        provideRouter([]),
      ],
    }).compileComponents();
  });

  it('defaults the recipient scope to ALL (Req 2.1)', () => {
    setup();
    fixture.detectChanges(); // ngOnInit -> loadActiveOwners

    expect(component.recipientScope).toBe('ALL');
    expect(component.activeOwners.length).toBe(2);
  });

  it('blocks submission when SELECTED scope has no owners selected and retains scope/content (Req 2.5)', () => {
    setup();
    fixture.detectChanges();

    component.recipientScope = 'SELECTED';
    fillValidMessage();
    component.onSelectionChange();
    expect(component.selectedCount).toBe(0);

    component.send();

    // Service never called; scope and composed content are retained.
    expect(ownerService.sendOwnerEmail).not.toHaveBeenCalled();
    expect(component.recipientScope).toBe('SELECTED');
    expect(component.form.value.subject).toBe('Notice');
    expect(component.form.value.body).toBe('Please note the AGM.');

    const [message] = snackBar.calls.mostRecent().args;
    expect(message).toContain('at least one recipient');
  });

  it('blocks submission when ALL scope has zero active owners and retains content (Req 2.6)', () => {
    ownerService.getActiveOwnersList.and.returnValue(of(ok<Owner[]>([])));
    setup();
    fixture.detectChanges();

    expect(component.recipientScope).toBe('ALL');
    expect(component.activeOwners.length).toBe(0);
    fillValidMessage();

    component.send();

    expect(ownerService.sendOwnerEmail).not.toHaveBeenCalled();
    expect(component.form.value.subject).toBe('Notice');
    const [message] = snackBar.calls.mostRecent().args;
    expect(message).toContain('No active owners');
  });

  it('accepts subject/body within bounds and submits (Req 3.1)', () => {
    ownerService.sendOwnerEmail.and.returnValue(of(ok(makeReport())));
    setup();
    fixture.detectChanges();

    component.form.setValue({ subject: 'a'.repeat(200), body: 'b'.repeat(10000) });
    component.send();

    expect(ownerService.sendOwnerEmail).toHaveBeenCalledTimes(1);
    const request = ownerService.sendOwnerEmail.calls.mostRecent().args[0] as SendOwnerEmailRequest;
    expect(request.subject.length).toBe(200);
    expect(request.body.length).toBe(10000);
    expect(request.recipientScope).toBe('ALL');
  });

  it('blocks and messages when the subject is whitespace-only (Req 3.2)', () => {
    setup();
    fixture.detectChanges();

    component.form.setValue({ subject: '   ', body: 'Real content' });
    component.send();

    expect(ownerService.sendOwnerEmail).not.toHaveBeenCalled();
    const [message] = snackBar.calls.mostRecent().args;
    expect(message).toContain('subject');
    // Content retained on block.
    expect(component.form.value.body).toBe('Real content');
  });

  it('blocks and messages when the body is whitespace-only (Req 3.3)', () => {
    setup();
    fixture.detectChanges();

    component.form.setValue({ subject: 'Has subject', body: '   ' });
    component.send();

    expect(ownerService.sendOwnerEmail).not.toHaveBeenCalled();
    const [message] = snackBar.calls.mostRecent().args;
    expect(message).toContain('message content');
    expect(component.form.value.subject).toBe('Has subject');
  });

  it('marks the subject control invalid when it exceeds the max length (Req 3.4)', () => {
    setup();
    fixture.detectChanges();

    component.form.get('subject')!.setValue('x'.repeat(201));
    expect(component.form.get('subject')!.hasError('maxlength')).toBeTrue();
  });

  it('marks the body control invalid when its visible text exceeds the max length (Req 3.4)', () => {
    setup();
    fixture.detectChanges();

    component.form.get('body')!.setValue('y'.repeat(10001));
    expect(component.form.get('body')!.hasError('tooLongVisible')).toBeTrue();
  });

  it('renders the results panel with sent/not-emailed counts and the not-emailed list (Req 7.4, 7.5)', () => {
    ownerService.sendOwnerEmail.and.returnValue(
      of(ok(makeReport({ totalAttempted: 3, sentCount: 2, notEmailedCount: 1 }))),
    );
    setup();
    fixture.detectChanges();

    fillValidMessage();
    component.send();
    fixture.detectChanges();

    expect(component.report).toBeTruthy();
    expect(component.report!.sentCount).toBe(2);
    expect(component.report!.notEmailedCount).toBe(1);

    const host: HTMLElement = fixture.nativeElement;
    const counts = host.querySelector('.result-counts');
    expect(counts).toBeTruthy();
    expect((counts!.textContent ?? '')).toContain('2'); // sent
    expect((counts!.textContent ?? '')).toContain('1'); // not emailed

    // Not-emailed table shows the recipient identity and a reason (Req 7.5).
    const notEmailedTable = host.querySelector('.not-emailed-table');
    expect(notEmailedTable).toBeTruthy();
    expect((notEmailedTable!.textContent ?? '')).toContain('No Email Owner');
    expect((notEmailedTable!.textContent ?? '')).toContain('No email address on file');
  });

  it('surfaces a timeout error, retains content, and allows retry after 30s with no report (Req 7.6)', fakeAsync(() => {
    // A request that never emits triggers the RxJS timeout(30000).
    const pending = new Subject<ApiResponse<SendReport>>();
    ownerService.sendOwnerEmail.and.returnValue(pending.asObservable());
    setup();
    fixture.detectChanges();

    fillValidMessage();
    component.send();
    expect(component.sending).toBeTrue();

    tick(30000); // trip the timeout
    fixture.detectChanges();

    // Loading cleared, no report produced, content retained.
    expect(component.sending).toBeFalse();
    expect(component.report).toBeNull();
    expect(component.form.value.subject).toBe('Notice');
    expect(component.form.value.body).toBe('Please note the AGM.');

    const [message] = snackBar.calls.mostRecent().args;
    expect(message).toContain('timed out');

    // Retry is allowed: a subsequent send succeeds.
    ownerService.sendOwnerEmail.and.returnValue(of(ok(makeReport())));
    component.send();
    tick();
    fixture.detectChanges();

    expect(ownerService.sendOwnerEmail).toHaveBeenCalledTimes(2);
    expect(component.report).toBeTruthy();

    pending.complete();
  }));

  it('handles an active-owner load failure without discarding the composition view (Req 1.4)', () => {
    ownerService.getActiveOwnersList.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 500, statusText: 'Server Error' })),
    );
    setup();
    fixture.detectChanges();

    // Loading finished; the view remains and shows an error indication.
    expect(component.loadingOwners).toBeFalse();
    expect(component.activeOwners.length).toBe(0);
    const [message] = snackBar.calls.mostRecent().args;
    expect(message).toContain('Failed to load owners');

    // The composition form is still present and usable.
    const host: HTMLElement = fixture.nativeElement;
    expect(host.querySelector('input[formControlName="subject"]')).toBeTruthy();
    expect(host.querySelector('textarea[formControlName="body"]')).toBeTruthy();
  });
});

