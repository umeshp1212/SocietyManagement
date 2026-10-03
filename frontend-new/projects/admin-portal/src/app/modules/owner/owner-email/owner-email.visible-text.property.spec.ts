import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { OwnerService } from '@core/services/owner.service';
import { ApiResponse } from '@core/models/api-response.model';
import { Owner, SendOwnerEmailRequest, SendReport } from '@core/models/owner.model';
import { OwnerEmailComponent } from './owner-email.component';

/**
 * Feature: owner-email-rich-text, Property 7: Client-side visible-text validation.
 *
 * **Validates: Requirements 4.1, 4.2, 4.3**
 *
 * The Owner_Email_Component must gate submission on the *visible* text length of
 * the composed rich-text body (markup and whitespace stripped), not the raw HTML
 * length:
 *   - 4.1: a Body whose Visible_Text is 1..10,000 inclusive is allowed.
 *   - 4.2: Visible_Text length 0 blocks submission, retains composed content and
 *          recipient selection, and requests message content.
 *   - 4.3: Visible_Text length > 10,000 blocks submission and indicates the
 *          maximum allowed visible-text length.
 *
 * `fast-check` is not a project dependency, so this uses a parameterized table of
 * boundary and representative cases spanning empty, in-range, and over-length
 * visible text. Each case wraps the visible text in real allowed markup so the
 * test exercises the visible-text extraction, not just plain strings.
 */
describe('OwnerEmailComponent visible-text validation (Property 7)', () => {
  const BODY_MAX = 10000;

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
      sentCount: 2,
      notEmailedCount: 0,
      mailConfigured: true,
      notEmailed: [],
      ...overrides,
    };
  }

  /**
   * Wrap `visibleText` in allowed markup (a heading + a bold paragraph) plus
   * ignorable whitespace/tags, so that the DOM-visible text equals `visibleText`
   * exactly. Empty visible text yields markup-only HTML (its textContent is "").
   */
  function htmlWithVisibleText(visibleText: string): string {
    if (visibleText.length === 0) {
      // Markup and whitespace only: visible text is empty after strip + trim.
      return '<p><br></p><h1></h1>';
    }
    // Split across two allowed elements to prove tags/whitespace are not counted.
    const mid = Math.floor(visibleText.length / 2);
    const head = visibleText.slice(0, mid);
    const tail = visibleText.slice(mid);
    return `<h2>${head}</h2><p><strong>${tail}</strong></p>`;
  }

  beforeEach(async () => {
    ownerService = jasmine.createSpyObj<OwnerService>('OwnerService', [
      'getActiveOwnersList',
      'sendOwnerEmail',
    ]);
    ownerService.getActiveOwnersList.and.returnValue(
      of(ok([
        makeOwner({ ownerId: 1 }),
        makeOwner({ ownerId: 2, fullName: 'John Owner', email: 'john@example.com' }),
      ])),
    );
    ownerService.sendOwnerEmail.and.returnValue(of(ok(makeReport())));

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
   * The reactive-form validator (bodyVisibleValidator) is the source of truth for
   * the visible-text bound. Asserting its error keys directly proves Req 4.1-4.3
   * across the full boundary table without depending on send()'s ordering.
   */
  describe('bodyVisibleValidator error keys reflect the visible-text bound', () => {
    interface Case {
      readonly label: string;
      readonly visibleLen: number;
      /** null = valid (in-range); otherwise the expected error key. */
      readonly expectedError: 'emptyVisible' | 'tooLongVisible' | null;
    }

    const cases: readonly Case[] = [
      { label: 'empty (0)', visibleLen: 0, expectedError: 'emptyVisible' },
      { label: 'lower boundary (1)', visibleLen: 1, expectedError: null },
      { label: 'small in-range (10)', visibleLen: 10, expectedError: null },
      { label: 'mid-range (5000)', visibleLen: 5000, expectedError: null },
      { label: 'just below max (9999)', visibleLen: BODY_MAX - 1, expectedError: null },
      { label: 'upper boundary (10000)', visibleLen: BODY_MAX, expectedError: null },
      { label: 'just over max (10001)', visibleLen: BODY_MAX + 1, expectedError: 'tooLongVisible' },
      { label: 'large over-length (25000)', visibleLen: 25000, expectedError: 'tooLongVisible' },
    ];

    for (const c of cases) {
      it(`${c.label} -> ${c.expectedError ?? 'valid'}`, () => {
        const control = component.form.get('body')!;
        control.setValue(htmlWithVisibleText('a'.repeat(c.visibleLen)));

        if (c.expectedError === null) {
          expect(control.hasError('emptyVisible')).withContext('emptyVisible').toBeFalse();
          expect(control.hasError('tooLongVisible')).withContext('tooLongVisible').toBeFalse();
          expect(control.valid).withContext('control.valid').toBeTrue();
        } else {
          expect(control.hasError(c.expectedError)).withContext(c.expectedError).toBeTrue();
          expect(control.valid).withContext('control.valid').toBeFalse();
        }
      });
    }

    it('ignores markup and leading/trailing whitespace, counting only visible text', () => {
      const control = component.form.get('body')!;
      // A few visible chars wrapped in many tags plus large leading/trailing
      // whitespace (which the validator trims). The raw HTML length dwarfs
      // BODY_MAX, yet the visible text is short, so the control is valid.
      const edgePad = ' '.repeat(BODY_MAX * 2);
      control.setValue(
        `<p>${edgePad}</p><h1>abc</h1><p><strong>def</strong><em>ghi</em></p><p>${edgePad}</p>`,
      );
      expect(control.hasError('emptyVisible')).toBeFalse();
      expect(control.hasError('tooLongVisible')).toBeFalse();
      expect(control.valid).toBeTrue();
    });
  });

  /**
   * End-to-end through send(): submission is blocked at 0 and >10000 with the
   * appropriate message, accepted in-range, and on block the composed body and
   * recipient selection are retained (Req 4.2, 4.3).
   */
  describe('send() gates on visible-text length and retains state on block', () => {
    const SUBJECT = 'Notice';

    function selectFirstRecipient(): void {
      component.recipientScope = 'SELECTED';
      component.selected[1] = true;
      component.onSelectionChange();
      expect(component.selectedCount).toBe(1);
    }

    it('blocks submission at visible length 0 and requests message content (Req 4.2)', () => {
      const body = htmlWithVisibleText('');
      component.form.setValue({ subject: SUBJECT, body });
      selectFirstRecipient();

      component.send();

      expect(ownerService.sendOwnerEmail).not.toHaveBeenCalled();
      // Composed content retained.
      expect(component.form.value.subject).toBe(SUBJECT);
      expect(component.form.value.body).toBe(body);
      // Recipient selection retained.
      expect(component.recipientScope).toBe('SELECTED');
      expect(component.selected[1]).toBeTrue();
      expect(component.selectedCount).toBe(1);

      const [message] = snackBar.calls.mostRecent().args;
      expect(message).toContain('message content');
    });

    it('blocks submission when visible length exceeds the max and indicates the limit (Req 4.3)', () => {
      const body = htmlWithVisibleText('z'.repeat(BODY_MAX + 1));
      component.form.setValue({ subject: SUBJECT, body });
      selectFirstRecipient();

      component.send();

      expect(ownerService.sendOwnerEmail).not.toHaveBeenCalled();
      // Composed content retained.
      expect(component.form.value.subject).toBe(SUBJECT);
      expect(component.form.value.body).toBe(body);
      // Recipient selection retained.
      expect(component.recipientScope).toBe('SELECTED');
      expect(component.selected[1]).toBeTrue();
      expect(component.selectedCount).toBe(1);

      const [message] = snackBar.calls.mostRecent().args;
      expect(String(message)).toContain(String(BODY_MAX));
    });

    it('accepts submission at the lower boundary (visible length 1) (Req 4.1)', () => {
      component.form.setValue({ subject: SUBJECT, body: htmlWithVisibleText('a') });

      component.send();

      expect(ownerService.sendOwnerEmail).toHaveBeenCalledTimes(1);
      const request = ownerService.sendOwnerEmail.calls.mostRecent().args[0] as SendOwnerEmailRequest;
      expect(request.body).toBe(htmlWithVisibleText('a'));
    });

    it('accepts submission at the upper boundary (visible length 10000) (Req 4.1)', () => {
      const body = htmlWithVisibleText('a'.repeat(BODY_MAX));
      component.form.setValue({ subject: SUBJECT, body });

      component.send();

      expect(ownerService.sendOwnerEmail).toHaveBeenCalledTimes(1);
      const request = ownerService.sendOwnerEmail.calls.mostRecent().args[0] as SendOwnerEmailRequest;
      expect(request.body).toBe(body);
    });

    it('accepts submission for representative in-range content (Req 4.1)', () => {
      const body = htmlWithVisibleText('Please attend the AGM this Sunday.');
      component.form.setValue({ subject: SUBJECT, body });

      component.send();

      expect(ownerService.sendOwnerEmail).toHaveBeenCalledTimes(1);
    });
  });
});
