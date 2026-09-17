import { Component, OnInit, SecurityContext } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { DomSanitizer } from '@angular/platform-browser';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatRadioModule } from '@angular/material/radio';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDividerModule } from '@angular/material/divider';
import { QuillModule } from 'ngx-quill';
import { timeout } from 'rxjs/operators';
import { OwnerService } from '@core/services/owner.service';
import {
  Owner,
  RecipientScope,
  SendOwnerEmailRequest,
  SendReport,
  NotEmailedReason
} from '@core/models/owner.model';
import { BackButtonComponent } from '@shared/components/back-button';

const SUBJECT_MAX = 200;
const BODY_MAX = 10000;
const SEND_TIMEOUT_MS = 30000;

@Component({
  selector: 'app-owner-email',
  standalone: true,
  imports: [
    CommonModule, FormsModule, ReactiveFormsModule, RouterModule,
    MatCardModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatRadioModule, MatCheckboxModule, MatTableModule, MatButtonModule,
    MatIconModule, MatProgressSpinnerModule, MatSnackBarModule, MatDividerModule,
    QuillModule, BackButtonComponent
  ],
  template: `
    <div class="container">
      <app-back-button link="/owners" label="Back to Owners"></app-back-button>
      <div class="page-header">
        <h2>Email Owners</h2>
      </div>

      <!-- Recipient selection -->
      <mat-card>
        <mat-card-header>
          <mat-card-title>Recipients</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <mat-radio-group [(ngModel)]="recipientScope" (change)="onScopeChange()"
                           class="scope-group" aria-label="Recipient scope">
            <mat-radio-button value="ALL">All active owners</mat-radio-button>
            <mat-radio-button value="SELECTED">Selected owners</mat-radio-button>
          </mat-radio-group>

          <div *ngIf="recipientScope === 'ALL'" class="scope-hint">
            <mat-icon>groups</mat-icon>
            <span>{{ activeOwners.length }} active owner(s) will receive this email.</span>
          </div>

          <div *ngIf="recipientScope === 'SELECTED'" class="selected-block">
            <div *ngIf="loadingOwners" class="loading-inline">
              <mat-spinner diameter="24"></mat-spinner>
              <span>Loading owners...</span>
            </div>

            <div *ngIf="!loadingOwners && activeOwners.length === 0" class="empty-hint">
              <mat-icon>person_off</mat-icon>
              <span>No active owners are available.</span>
            </div>

            <div *ngIf="!loadingOwners && activeOwners.length > 0">
              <div class="selection-summary">
                {{ selectedCount }} of {{ activeOwners.length }} selected
              </div>
              <div class="owner-table-wrapper">
                <table mat-table [dataSource]="activeOwners" class="mat-elevation-z1">
                  <ng-container matColumnDef="select">
                    <th mat-header-cell *matHeaderCellDef></th>
                    <td mat-cell *matCellDef="let o">
                      <mat-checkbox [(ngModel)]="selected[o.ownerId]"
                                    (change)="onSelectionChange()"></mat-checkbox>
                    </td>
                  </ng-container>
                  <ng-container matColumnDef="fullName">
                    <th mat-header-cell *matHeaderCellDef>Name</th>
                    <td mat-cell *matCellDef="let o">{{ o.fullName }}</td>
                  </ng-container>
                  <ng-container matColumnDef="email">
                    <th mat-header-cell *matHeaderCellDef>Email</th>
                    <td mat-cell *matCellDef="let o">{{ o.email || '-' }}</td>
                  </ng-container>
                  <ng-container matColumnDef="contactNumber">
                    <th mat-header-cell *matHeaderCellDef>Contact</th>
                    <td mat-cell *matCellDef="let o">{{ o.contactNumber }}</td>
                  </ng-container>
                  <tr mat-header-row *matHeaderRowDef="ownerColumns"></tr>
                  <tr mat-row *matRowDef="let row; columns: ownerColumns;"></tr>
                </table>
              </div>
            </div>
          </div>
        </mat-card-content>
      </mat-card>

      <!-- Composition -->
      <mat-card style="margin-top: 16px">
        <mat-card-header>
          <mat-card-title>Message</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="form">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Subject</mat-label>
              <input matInput id="owner-email-subject" #subjectInput formControlName="subject" [maxlength]="subjectMax"
                     (focus)="onSubjectFocus(subjectInput)"
                     placeholder="Enter email subject">
              <mat-hint align="end">{{ form.value.subject?.length || 0 }}/{{ subjectMax }}</mat-hint>
              <mat-error *ngIf="form.get('subject')?.hasError('required')">
                Please enter a subject.
              </mat-error>
              <mat-error *ngIf="form.get('subject')?.hasError('whitespace')">
                Please enter a subject.
              </mat-error>
              <mat-error *ngIf="form.get('subject')?.hasError('maxlength')">
                Subject cannot exceed {{ subjectMax }} characters.
              </mat-error>
            </mat-form-field>

            <div class="body-field full-width">
              <span class="body-label" id="body-editor-label">Message body</span>
              <quill-editor formControlName="body"
                            [modules]="quillModules"
                            [styles]="editorStyles"
                            aria-label="Message body"
                            aria-labelledby="body-editor-label"
                            placeholder="Enter your message"
                            (onEditorCreated)="onEditorCreated($event)"
                            (onContentChanged)="onBodyChanged()">
              </quill-editor>
              <div class="body-hint" aria-live="polite">
                {{ visibleLength }}/{{ bodyMax }} characters
              </div>
              <mat-error class="body-error" *ngIf="form.get('body')?.touched && form.get('body')?.hasError('emptyVisible')">
                Please enter message content.
              </mat-error>
              <mat-error class="body-error" *ngIf="form.get('body')?.touched && form.get('body')?.hasError('tooLongVisible')">
                Message cannot exceed {{ bodyMax }} visible characters.
              </mat-error>
            </div>

            <!-- Body preview: secondary-defence rendering only (Req 3.8). The editor
                 HTML is passed through DomSanitizer.sanitize(SecurityContext.HTML, ...)
                 so the preview can never execute untrusted markup. bypassSecurityTrustHtml
                 is deliberately NOT used; the server-side sanitizer remains authoritative. -->
            <div class="body-preview full-width" *ngIf="bodyPreviewHtml">
              <span class="body-label">Preview</span>
              <div class="preview-content" [innerHTML]="bodyPreviewHtml"></div>
            </div>
          </form>

          <!-- Attachments (optional) -->
          <div class="attachments-block">
            <div class="attachments-header">
              <span class="attachments-title">Attachments</span>
              <span class="attachments-optional">(optional)</span>
            </div>
            <input #fileInput type="file" multiple hidden
                   (change)="onFilesSelected($event)">
            <button mat-stroked-button type="button" (click)="fileInput.click()">
              <mat-icon>attach_file</mat-icon>
              Add files
            </button>
            <div *ngIf="attachments.length === 0" class="attachments-hint">
              No files attached.
            </div>
            <div *ngIf="attachments.length > 0" class="attachment-list">
              <div class="attachment-item" *ngFor="let file of attachments; let i = index">
                <mat-icon class="file-icon">insert_drive_file</mat-icon>
                <span class="file-name">{{ file.name }}</span>
                <span class="file-size">{{ formatSize(file.size) }}</span>
                <button mat-icon-button type="button" (click)="removeAttachment(i)"
                        aria-label="Remove attachment">
                  <mat-icon>close</mat-icon>
                </button>
              </div>
            </div>
          </div>

          <div class="actions">
            <button mat-raised-button color="primary" (click)="send()" [disabled]="sending">
              <mat-icon *ngIf="!sending">send</mat-icon>
              <mat-spinner *ngIf="sending" diameter="20" class="btn-spinner"></mat-spinner>
              {{ sending ? 'Sending...' : 'Send Email' }}
            </button>
          </div>
        </mat-card-content>
      </mat-card>

      <!-- Results -->
      <mat-card style="margin-top: 16px" *ngIf="report">
        <mat-card-header>
          <mat-card-title>Send Report</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div *ngIf="!report.mailConfigured" class="mail-warning">
            <mat-icon>report_problem</mat-icon>
            <span>Mail is not configured. No emails were sent.</span>
          </div>

          <div class="result-counts">
            <div class="count-box sent">
              <span class="count">{{ report.sentCount }}</span>
              <span class="label">Sent</span>
            </div>
            <div class="count-box not-emailed">
              <span class="count">{{ report.notEmailedCount }}</span>
              <span class="label">Not emailed</span>
            </div>
            <div class="count-box total">
              <span class="count">{{ report.totalAttempted }}</span>
              <span class="label">Total attempted</span>
            </div>
          </div>

          <div *ngIf="report.notEmailed?.length" class="not-emailed-table">
            <mat-divider style="margin: 16px 0;"></mat-divider>
            <h4>Recipients not emailed</h4>
            <table mat-table [dataSource]="report.notEmailed" class="mat-elevation-z1">
              <ng-container matColumnDef="ownerName">
                <th mat-header-cell *matHeaderCellDef>Name</th>
                <td mat-cell *matCellDef="let e">{{ e.ownerName }}</td>
              </ng-container>
              <ng-container matColumnDef="ownerId">
                <th mat-header-cell *matHeaderCellDef>Owner ID</th>
                <td mat-cell *matCellDef="let e">{{ e.ownerId }}</td>
              </ng-container>
              <ng-container matColumnDef="reason">
                <th mat-header-cell *matHeaderCellDef>Reason</th>
                <td mat-cell *matCellDef="let e">{{ reasonLabel(e.reason) }}</td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="notEmailedColumns"></tr>
              <tr mat-row *matRowDef="let row; columns: notEmailedColumns;"></tr>
            </table>
          </div>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .scope-group { display: flex; gap: 24px; margin-bottom: 12px; }
    .scope-hint, .empty-hint, .loading-inline { display: flex; align-items: center; gap: 8px; color: #666; }
    .selection-summary { margin-bottom: 8px; font-weight: 500; }
    .owner-table-wrapper { max-height: 360px; overflow: auto; }
    .full-width { width: 100%; }
    .body-field { display: block; margin-bottom: 16px; }
    /* ngx-quill's <quill-editor> host defaults to display:inline, which collapses
       its layout box so the toolbar/editing area overlap the field above it and
       intercept clicks meant for the subject input. Force it to a block that
       establishes its own height so it sits below the subject field. */
    .body-field quill-editor {
      display: block;
      position: relative;
    }
    .body-field ::ng-deep .ql-container { min-height: 200px; }
    .body-hint { text-align: right; font-size: 12px; color: rgba(0,0,0,0.6); margin-top: 4px; }
    /* Visible focus indicator when keyboard focus lands on a toolbar control (Req 6.4). */
    .body-field ::ng-deep .ql-toolbar button:focus-visible,
    .body-field ::ng-deep .ql-toolbar .ql-picker-label:focus-visible,
    .body-field ::ng-deep .ql-toolbar .ql-picker-item:focus-visible {
      outline: 2px solid #1976d2;
      outline-offset: 1px;
      border-radius: 2px;
    }
    .body-error { display: block; font-size: 12px; margin-top: 4px; }
    .body-preview { display: block; margin-bottom: 16px; }
    .preview-content { border: 1px solid rgba(0,0,0,0.12); border-radius: 4px; padding: 12px; background: #fafafa; }
    .actions { display: flex; gap: 8px; margin-top: 8px; }
    .btn-spinner { display: inline-block; margin-right: 4px; }
    .mail-warning { display: flex; align-items: center; gap: 8px; color: #e65100; margin-bottom: 12px; }
    .result-counts { display: flex; gap: 16px; flex-wrap: wrap; }
    .count-box { display: flex; flex-direction: column; align-items: center; padding: 12px 20px; border-radius: 8px; min-width: 100px; }
    .count-box .count { font-size: 28px; font-weight: 600; }
    .count-box .label { font-size: 13px; color: #666; }
    .count-box.sent { background: #e8f5e9; }
    .count-box.not-emailed { background: #ffebee; }
    .count-box.total { background: #e3f2fd; }
    .not-emailed-table table { width: 100%; }
    .attachments-block { margin-top: 8px; }
    .attachments-header { display: flex; align-items: baseline; gap: 6px; margin-bottom: 8px; }
    .attachments-title { font-weight: 500; }
    .attachments-optional { font-size: 12px; color: #888; }
    .attachments-hint { margin-top: 8px; color: #888; font-size: 13px; }
    .attachment-list { margin-top: 8px; display: flex; flex-direction: column; gap: 4px; }
    .attachment-item { display: flex; align-items: center; gap: 8px; padding: 4px 8px; background: #f5f5f5; border-radius: 4px; }
    .attachment-item .file-icon { color: #607d8b; }
    .attachment-item .file-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .attachment-item .file-size { font-size: 12px; color: #888; }
  `]
})
export class OwnerEmailComponent implements OnInit {
  readonly subjectMax = SUBJECT_MAX;
  readonly bodyMax = BODY_MAX;

  /** Quill toolbar restricted to the Allowed_Formatting allow-list:
   *  bold/italic/underline/strike, headings, ordered/bullet lists, link. */
  readonly quillModules = {
    toolbar: [
      ['bold', 'italic', 'underline', 'strike'],
      [{ header: [1, 2, 3, false] }],
      [{ list: 'ordered' }, { list: 'bullet' }],
      ['link']
    ]
  };

  /** Stable style object for the editor. Bound by reference (not an inline object
   *  literal) so ngx-quill's ngOnChanges does not re-run on every change-detection
   *  cycle; a fresh literal each cycle caused the editor to re-apply styles and
   *  reclaim DOM focus, routing keystrokes meant for the subject field into the body. */
  readonly editorStyles = { minHeight: '200px' };

  recipientScope: RecipientScope = 'ALL';
  activeOwners: Owner[] = [];
  loadingOwners = false;
  selected: Record<number, boolean> = {};
  selectedCount = 0;

  form: FormGroup;
  sending = false;
  report: SendReport | null = null;

  /** Visible-text length of the current body, shown in the length hint. */
  visibleLength = 0;

  /** The Quill instance, captured on editor creation, used for the accessibility
   *  hardening pass (aria-labels, aria-pressed, keyboard operability). */
  private quill: any;

  /** Accessible names applied to each toolbar control, keyed by its Quill CSS class
   *  or by the `<class, value>` pair for value-bearing controls (Req 6.2). */
  private static readonly TOOLBAR_LABELS: Record<string, string> = {
    'ql-bold': 'Bold',
    'ql-italic': 'Italic',
    'ql-underline': 'Underline',
    'ql-strike': 'Strikethrough',
    'ql-link': 'Insert link',
    'ql-list[ordered]': 'Numbered list',
    'ql-list[bullet]': 'Bulleted list',
    'ql-header': 'Heading style'
  };

  /** Format controls whose active/inactive state is reflected via aria-pressed
   *  on selection change (Req 6.3). Maps the Quill format name to its button class. */
  private static readonly TOGGLE_FORMATS: Array<{ format: string; selector: string }> = [
    { format: 'bold', selector: 'button.ql-bold' },
    { format: 'italic', selector: 'button.ql-italic' },
    { format: 'underline', selector: 'button.ql-underline' },
    { format: 'strike', selector: 'button.ql-strike' }
  ];

  /** Optional file attachments; empty when the user attaches nothing. */
  attachments: File[] = [];

  ownerColumns = ['select', 'fullName', 'email', 'contactNumber'];
  notEmailedColumns = ['ownerName', 'ownerId', 'reason'];

  private readonly reasonLabels: Record<NotEmailedReason, string> = {
    MISSING_EMAIL: 'No email address on file',
    SEND_FAILURE: 'Sending failed',
    MAIL_NOT_CONFIGURED: 'Mail not configured'
  };

  constructor(
    private fb: FormBuilder,
    private ownerService: OwnerService,
    private snackBar: MatSnackBar,
    private sanitizer: DomSanitizer
  ) {
    this.form = this.fb.group({
      subject: ['', [Validators.required, this.notBlankValidator, Validators.maxLength(SUBJECT_MAX)]],
      body: ['', [this.bodyVisibleValidator]]
    });
  }

  ngOnInit(): void {
    this.loadActiveOwners();
  }

  private loadActiveOwners(): void {
    this.loadingOwners = true;
    this.ownerService.getActiveOwnersList().subscribe({
      next: (res) => {
        this.loadingOwners = false;
        if (res.success) {
          this.activeOwners = res.data || [];
        }
      },
      error: () => {
        this.loadingOwners = false;
        this.snackBar.open('Failed to load owners', 'Close', { duration: 5000 });
      }
    });
  }

  /** Rejects strings that are empty or whitespace-only (leaves untouched when empty for required to fire). */
  private notBlankValidator(control: { value: unknown }): { whitespace: true } | null {
    const value = control.value;
    if (typeof value === 'string' && value.length > 0 && value.trim().length === 0) {
      return { whitespace: true };
    }
    return null;
  }

  /** Visible-text length of editor HTML: strip tags via a detached element, decode
   *  entities, and trim. Measures the actual visible content, not raw HTML length. */
  private visibleTextLength(html: string): number {
    const el = document.createElement('div');
    el.innerHTML = html ?? '';
    return (el.textContent ?? '').trim().length;
  }

  /** Reactive-form validator enforcing the 1..BODY_MAX visible-text bound (Req 4.1-4.3):
   *  emptyVisible at length 0, tooLongVisible above BODY_MAX, otherwise valid. */
  private bodyVisibleValidator = (control: AbstractControl): ValidationErrors | null => {
    const len = this.visibleTextLength((control.value ?? '') as string);
    if (len === 0) { return { emptyVisible: true }; }        // Req 4.2
    if (len > BODY_MAX) { return { tooLongVisible: true }; } // Req 4.3
    return null;
  };

  /** Keeps the visible-length hint in sync as the editor content changes. */
  onBodyChanged(): void {
    this.visibleLength = this.visibleTextLength((this.form.value.body ?? '') as string);
  }

  /** Removes the caret/selection and DOM focus from the Quill editor. */
  private blurEditor(): void {
    const q = this.quill;
    if (!q) { return; }
    q.setSelection?.(null);
    q.blur?.();
    (q.root as HTMLElement | undefined)?.blur?.();
  }

  /** Was the user's most recent pointer-down inside the editor? Only then may the
   *  editor legitimately take focus; otherwise a focusin on the editor root is
   *  Quill trying to reclaim focus (e.g. from the subject input) and is rejected. */
  private pointerInsideEditor = false;

  /** Stops Quill 2's contenteditable from stealing focus from other controls.
   *  The focus log proved the sequence on a subject click is:
   *    subject INPUT (focusin) -> ql-editor DIV (focusin)   // editor steals it back
   *  We track whether the pointer went down inside the editor. When the editor root
   *  gains focus WITHOUT such a pointer interaction, we bounce focus back to the
   *  last non-editor element (the subject input), so keystrokes stay where the user
   *  clicked. Genuine clicks into the editor set the flag and are allowed through. */
  private installFocusArbiter(quill: any): void {
    const root = quill?.root as HTMLElement | undefined;
    const container = quill?.container as HTMLElement | undefined;
    if (!root) { return; }

    const isInsideEditor = (node: EventTarget | null): boolean =>
      !!node && (root.contains(node as Node) || !!container?.contains(node as Node));

    // Record pointer intent in the capture phase, before focus moves.
    const markPointer = (e: Event) => { this.pointerInsideEditor = isInsideEditor(e.target); };
    document.addEventListener('mousedown', markPointer, true);
    document.addEventListener('touchstart', markPointer, true);

    // Remember the last focused control that is NOT the editor, so we can restore it.
    document.addEventListener('focusin', (e) => {
      if (!isInsideEditor(e.target)) {
        this.lastNonEditorFocus = e.target as HTMLElement;
      }
    }, true);

    // When the editor root gains focus without a deliberate pointer-down inside it,
    // reject the steal and hand focus back to the previous control.
    root.addEventListener('focusin', () => {
      if (!this.pointerInsideEditor) {
        quill.blur?.();
        root.blur();
        const target = this.lastNonEditorFocus;
        if (target && typeof target.focus === 'function') {
          // Defer so we win against Quill's own deferred selection/focus handling.
          setTimeout(() => target.focus(), 0);
        }
      }
    });
  }

  /** The most recent focused element outside the editor, used to restore focus when
   *  the editor tries to steal it without a deliberate click. */
  private lastNonEditorFocus: HTMLElement | null = null;

  onSubjectFocus(_input: HTMLInputElement): void {
    // Focus arbitration is handled by the editor-root focusin guard installed in
    // onEditorCreated; nothing to do here. Kept as a no-op hook for the template.
  }

  /** Accessibility hardening pass, run once the Quill editor exists (Req 6.1-6.3).
   *  Quill's toolbar buttons are real, focusable <button> elements and Quill's
   *  keyboard module already binds Ctrl/Cmd+B/I/U, so keyboard reachability and
   *  operability come for free (Req 6.1); here we ensure it and add the ARIA
   *  semantics Quill omits: accessible names and toggle state. */
  onEditorCreated(quill: any): void {
    this.quill = quill;

    // Quill 2 auto-focuses its contenteditable when the editor is created, which
    // lands the caret in the body on page load. Blur it once, after creation, so
    // the page opens with no control focused.
    setTimeout(() => this.blurEditor(), 0);

    this.installFocusArbiter(quill);

    const toolbarModule = quill?.getModule?.('toolbar');
    const toolbar: HTMLElement | null = toolbarModule?.container ?? null;
    if (toolbar) {
      this.labelToolbarControls(toolbar);   // Req 6.2
      this.reflectToggleState(toolbar);     // Req 6.3 (initial state)
    }

    // Re-evaluate toggle-button state whenever the selection or applied formats
    // change, so aria-pressed always mirrors Quill's current format state (Req 6.3).
    quill?.on?.('selection-change', () => {
      if (toolbar) {
        this.reflectToggleState(toolbar);
      }
    });
    quill?.on?.('editor-change', () => {
      if (toolbar) {
        this.reflectToggleState(toolbar);
      }
    });
  }

  /** Adds an aria-label to every toolbar control so screen readers announce its
   *  purpose, and initialises aria-pressed on toggle buttons (Req 6.2). Plain
   *  toggle buttons carry a single Quill class (e.g. ql-bold); value-bearing
   *  buttons carry a `value` attribute (e.g. ql-list value="ordered"). */
  private labelToolbarControls(toolbar: HTMLElement): void {
    const labels = OwnerEmailComponent.TOOLBAR_LABELS;

    toolbar.querySelectorAll('button').forEach((btn) => {
      const el = btn as HTMLButtonElement;
      const qlClass = Array.from(el.classList).find(c => c.startsWith('ql-'));
      if (!qlClass) { return; }

      const value = el.getAttribute('value');
      const key = value ? `${qlClass}[${value}]` : qlClass;
      const label = labels[key] ?? labels[qlClass];
      if (label && !el.getAttribute('aria-label')) {
        el.setAttribute('aria-label', label);
      }
    });

    // Picker controls (e.g. the header dropdown) expose a focusable label span
    // that lacks an accessible name; give it one and role/keyboard affordances.
    toolbar.querySelectorAll('.ql-picker').forEach((picker) => {
      const qlClass = Array.from((picker as HTMLElement).classList).find(c => c.startsWith('ql-'));
      const label = qlClass ? labels[qlClass] : undefined;
      const pickerLabel = picker.querySelector('.ql-picker-label') as HTMLElement | null;
      if (pickerLabel && label && !pickerLabel.getAttribute('aria-label')) {
        pickerLabel.setAttribute('aria-label', label);
      }
    });
  }

  /** Reflects Quill's current format state onto the toggle buttons as aria-pressed
   *  so assistive technology announces active/inactive formatting (Req 6.3). */
  private reflectToggleState(toolbar: HTMLElement): void {
    const format = this.quill?.getFormat?.() ?? {};
    for (const { format: name, selector } of OwnerEmailComponent.TOGGLE_FORMATS) {
      const btn = toolbar.querySelector(selector) as HTMLElement | null;
      if (btn) {
        btn.setAttribute('aria-pressed', format[name] ? 'true' : 'false');
      }
    }
  }

  /** Secondary-defence preview of the editor body (Req 3.8). The raw editor HTML is
   *  passed through Angular's DomSanitizer using sanitize(SecurityContext.HTML, ...),
   *  which strips unsafe markup (scripts, event handlers, unsafe URLs) rather than
   *  trusting it. bypassSecurityTrustHtml is intentionally never used here; the
   *  server-side Server_Sanitizer remains the authoritative sanitization stage. */
  get bodyPreviewHtml(): string | null {
    const html = (this.form.value.body ?? '') as string;
    if (!html.trim()) {
      return null;
    }
    return this.sanitizer.sanitize(SecurityContext.HTML, html);
  }

  onScopeChange(): void {
    // Preserve composed content; selection state is retained across scope toggles.
  }

  onSelectionChange(): void {
    this.selectedCount = this.getSelectedIds().length;
  }

  private getSelectedIds(): number[] {
    return this.activeOwners
      .filter(o => this.selected[o.ownerId])
      .map(o => o.ownerId);
  }

  reasonLabel(reason: NotEmailedReason): string {
    return this.reasonLabels[reason] || reason;
  }

  onFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      // Append to any already-selected files, then reset the input so the same
      // file can be re-added after removal.
      this.attachments = [...this.attachments, ...Array.from(input.files)];
      input.value = '';
    }
  }

  removeAttachment(index: number): void {
    this.attachments = this.attachments.filter((_, i) => i !== index);
  }

  formatSize(bytes: number): string {
    if (bytes < 1024) { return `${bytes} B`; }
    if (bytes < 1024 * 1024) { return `${(bytes / 1024).toFixed(1)} KB`; }
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  send(): void {
    // Composition validation (retains scope + content on block).
    this.form.markAllAsTouched();
    const subject = (this.form.value.subject ?? '') as string;
    const body = (this.form.value.body ?? '') as string;

    if (!subject.trim()) {
      this.snackBar.open('Please enter a subject.', 'Close', { duration: 4000 });
      return;
    }
    if (subject.length > SUBJECT_MAX) {
      this.snackBar.open(`Subject cannot exceed ${SUBJECT_MAX} characters.`, 'Close', { duration: 4000 });
      return;
    }
    // Visible-text validation on the body (Req 4.2, 4.3). On block, submission is
    // prevented while the composed content and recipient selection are retained.
    const visibleLen = this.visibleTextLength(body);
    if (visibleLen === 0) {
      this.snackBar.open('Please enter message content.', 'Close', { duration: 4000 });
      return;
    }
    if (visibleLen > BODY_MAX) {
      this.snackBar.open(`Message cannot exceed ${BODY_MAX} visible characters.`, 'Close', { duration: 4000 });
      return;
    }

    // Recipient validation.
    let ownerIds: number[] | undefined;
    if (this.recipientScope === 'SELECTED') {
      ownerIds = this.getSelectedIds();
      if (ownerIds.length === 0) {
        this.snackBar.open('Please select at least one recipient.', 'Close', { duration: 4000 });
        return;
      }
    } else {
      if (this.activeOwners.length === 0) {
        this.snackBar.open('No active owners are available to receive the email.', 'Close', { duration: 4000 });
        return;
      }
    }

    const request: SendOwnerEmailRequest = {
      recipientScope: this.recipientScope,
      ownerIds,
      subject,
      body
    };

    this.sending = true;
    this.report = null;
    this.ownerService.sendOwnerEmail(request, this.attachments).pipe(
      timeout(SEND_TIMEOUT_MS)
    ).subscribe({
      next: (res) => {
        this.sending = false;
        if (res.success) {
          this.report = res.data;
          this.snackBar.open(
            `Sent ${res.data.sentCount}, not emailed ${res.data.notEmailedCount}.`,
            'Close',
            { duration: 4000 }
          );
        } else {
          this.snackBar.open(res.message || 'Failed to send email', 'Close', { duration: 5000 });
        }
      },
      error: (err) => {
        this.sending = false;
        // On timeout the RxJS error carries no HTTP payload; retain content/selection and allow retry.
        const isTimeout = err?.name === 'TimeoutError';
        const message = isTimeout
          ? 'The request timed out. Your message was kept, please try again.'
          : (err?.error?.message || 'Failed to send email. Please try again.');
        this.snackBar.open(message, 'Close', { duration: 6000 });
      }
    });
  }
}
