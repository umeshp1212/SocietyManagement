import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { RouterModule } from '@angular/router';
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
import { timeout } from 'rxjs/operators';
import { OwnerService } from '@core/services/owner.service';
import {
  Owner,
  RecipientScope,
  SendOwnerEmailRequest,
  SendReport,
  NotEmailedReason
} from '@core/models/owner.model';

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
    MatIconModule, MatProgressSpinnerModule, MatSnackBarModule, MatDividerModule
  ],
  template: `
    <div class="container">
      <div class="page-header">
        <h2>Email Owners</h2>
        <a mat-button routerLink="/owners">Back to Owners</a>
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
              <input matInput formControlName="subject" [maxlength]="subjectMax"
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

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Message body</mat-label>
              <textarea matInput formControlName="body" rows="10" [maxlength]="bodyMax"
                        placeholder="Enter your message"></textarea>
              <mat-hint align="end">{{ form.value.body?.length || 0 }}/{{ bodyMax }}</mat-hint>
              <mat-error *ngIf="form.get('body')?.hasError('required')">
                Please enter message content.
              </mat-error>
              <mat-error *ngIf="form.get('body')?.hasError('whitespace')">
                Please enter message content.
              </mat-error>
              <mat-error *ngIf="form.get('body')?.hasError('maxlength')">
                Message body cannot exceed {{ bodyMax }} characters.
              </mat-error>
            </mat-form-field>
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

  recipientScope: RecipientScope = 'ALL';
  activeOwners: Owner[] = [];
  loadingOwners = false;
  selected: Record<number, boolean> = {};
  selectedCount = 0;

  form: FormGroup;
  sending = false;
  report: SendReport | null = null;

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
    private snackBar: MatSnackBar
  ) {
    this.form = this.fb.group({
      subject: ['', [Validators.required, this.notBlankValidator, Validators.maxLength(SUBJECT_MAX)]],
      body: ['', [Validators.required, this.notBlankValidator, Validators.maxLength(BODY_MAX)]]
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
    if (!body.trim()) {
      this.snackBar.open('Please enter message content.', 'Close', { duration: 4000 });
      return;
    }
    if (body.length > BODY_MAX) {
      this.snackBar.open(`Message body cannot exceed ${BODY_MAX} characters.`, 'Close', { duration: 4000 });
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
