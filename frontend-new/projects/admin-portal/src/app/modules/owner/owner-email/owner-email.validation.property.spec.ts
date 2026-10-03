import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { OwnerService } from '@core/services/owner.service';
import { ApiResponse } from '@core/models/api-response.model';
import { Owner } from '@core/models/owner.model';
import { OwnerEmailComponent } from './owner-email.component';

/**
 * Feature: owner-email, Property 13: Client-side validation rejects invalid subject/body
 *
 * For any subject that is empty/whitespace or exceeds 200 characters, or any body
 * that is empty/whitespace or exceeds 10,000 characters, the component SHALL block
 * submission and display the appropriate validation message while retaining the
 * composed content and recipient scope.
 *
 * Validates: Requirements 3.2, 3.3, 3.4
 *
 * fast-check is not available in the frontend toolchain (Jasmine/Karma), so this
 * property is exercised over a parameterized table of representative boundary
 * cases: empty strings, whitespace-only strings of varied shapes, and over-length
 * strings at the first invalid length (201 for subject, 10001 for body).
 */
describe('OwnerEmailComponent - Property 13: client-side validation rejects invalid subject/body', () => {
  const SUBJECT_MAX = 200;
  const BODY_MAX = 10000;

  const VALID_SUBJECT = 'Society Notice';
  const VALID_BODY = 'Please note the upcoming AGM this weekend.';

  let fixture: ComponentFixture<OwnerEmailComponent>;
  let component: OwnerEmailComponent;
  let ownerService: jasmine.SpyObj<OwnerService>;
  let snackBar: jasmine.Spy;

  function ok<T>(data: T): ApiResponse<T> {
    return { success: true, message: 'ok', data, timestamp: '2024-01-01T00:00:00Z' };
  }

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

  /**
   * Whitespace-only generators of varied shapes (single space, tabs, newlines,
   * mixed unicode whitespace, and a long run) plus the empty string. These stand
   * in for "any empty/whitespace string" from the property.
   */
  const emptyOrWhitespace: string[] = [
    '',
    ' ',
    '   ',
    '\t',
    '\n',
    '\t\n ',
    ' \r\n\t ',
    '\u00A0\u2003 \t',
    ' '.repeat(50),
  ];

  /** Over-length generators at and beyond the first invalid length. */
  const overLengthSubjects: string[] = [
    'a'.repeat(SUBJECT_MAX + 1),
    'b'.repeat(SUBJECT_MAX + 50),
    ('word ').repeat(60).slice(0, SUBJECT_MAX + 10),
  ];

  const overLengthBodies: string[] = [
    'a'.repeat(BODY_MAX + 1),
    'b'.repeat(BODY_MAX + 500),
    ('line\n').repeat(2100).slice(0, BODY_MAX + 25),
  ];

  beforeEach(async () => {
    ownerService = jasmine.createSpyObj<OwnerService>('OwnerService', [
      'getActiveOwnersList',
      'sendOwnerEmail',
    ]);

    // Non-empty active owners so ALL-scope recipient rules never block first;
    // the composition validation is what is under test.
    ownerService.getActiveOwnersList.and.returnValue(of(ok([makeOwner()])));

    await TestBed.configureTestingModule({
      imports: [OwnerEmailComponent, NoopAnimationsModule],
      providers: [
        { provide: OwnerService, useValue: ownerService },
        provideRouter([]),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(OwnerEmailComponent);
    component = fixture.componentInstance;
    snackBar = spyOn((component as unknown as { snackBar: MatSnackBar }).snackBar, 'open').and.stub();
    fixture.detectChanges(); // ngOnInit -> loadActiveOwners
  });

  /**
   * Runs the invariant for one (subject, body) invalid case: submission is
   * blocked (service never called), a message is shown, and both the composed
   * content and the recipient scope are retained.
   */
  function assertBlockedAndRetained(
    subject: string,
    body: string,
    expectedMessageFragment: string,
  ): void {
    ownerService.sendOwnerEmail.calls.reset();
    snackBar.calls.reset();

    // Pick a non-default scope to prove scope is retained across a blocked send.
    component.recipientScope = 'SELECTED';
    component.selected = { 1: true };
    component.onSelectionChange();
    component.form.setValue({ subject, body });

    component.send();

    // Submission blocked.
    expect(ownerService.sendOwnerEmail)
      .withContext(`subject=${JSON.stringify(subject.slice(0, 20))}... body=${JSON.stringify(body.slice(0, 20))}...`)
      .not.toHaveBeenCalled();

    // A validation message is shown.
    expect(snackBar).toHaveBeenCalled();
    const [message] = snackBar.calls.mostRecent().args as [string, ...unknown[]];
    expect(message).toContain(expectedMessageFragment);

    // Composed content retained.
    expect(component.form.value.subject).toBe(subject);
    expect(component.form.value.body).toBe(body);

    // Recipient scope retained.
    expect(component.recipientScope).toBe('SELECTED');
  }

  it('blocks every empty/whitespace subject and requests a subject (Req 3.2)', () => {
    for (const subject of emptyOrWhitespace) {
      assertBlockedAndRetained(subject, VALID_BODY, 'subject');
    }
  });

  it('blocks every empty/whitespace body and requests message content (Req 3.3)', () => {
    for (const body of emptyOrWhitespace) {
      assertBlockedAndRetained(VALID_SUBJECT, body, 'message content');
    }
  });

  it('blocks every over-length subject and indicates the max allowed length (Req 3.4)', () => {
    for (const subject of overLengthSubjects) {
      expect(subject.length).toBeGreaterThan(SUBJECT_MAX);
      assertBlockedAndRetained(subject, VALID_BODY, `${SUBJECT_MAX} characters`);
    }
  });

  it('blocks every over-length body and indicates the max allowed length (Req 3.4)', () => {
    for (const body of overLengthBodies) {
      expect(body.length).toBeGreaterThan(BODY_MAX);
      // Body length is now measured as visible text; the plain-text generators
      // above have visible length == raw length, so they still exceed the bound.
      assertBlockedAndRetained(VALID_SUBJECT, body, `${BODY_MAX} visible characters`);
    }
  });

  it('accepts the boundary-valid subject/body (control case) and submits', () => {
    ownerService.sendOwnerEmail.and.returnValue(
      of(ok({ totalAttempted: 1, sentCount: 1, notEmailedCount: 0, mailConfigured: true, notEmailed: [] })),
    );
    component.recipientScope = 'ALL';
    // Exactly at the maximum lengths — the last accepted values.
    component.form.setValue({ subject: 's'.repeat(SUBJECT_MAX), body: 'b'.repeat(BODY_MAX) });

    component.send();

    expect(ownerService.sendOwnerEmail).toHaveBeenCalledTimes(1);
  });
});
