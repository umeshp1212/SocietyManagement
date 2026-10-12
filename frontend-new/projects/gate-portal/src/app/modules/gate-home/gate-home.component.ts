import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatTabsModule } from '@angular/material/tabs';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Subscription, interval } from 'rxjs';
import { AuthService } from '@core/services/auth.service';
import { GateVisitorRequest, GateVisitorService } from '@core/services/gate-visitor.service';

/**
 * Guard home screen. "New Visitor" captures a delivery/visitor for a flat (number,
 * name, company, photo), submits it, then shows a live status that polls until the
 * flat owner approves/rejects or the request times out. "History" lists the gate log.
 */
@Component({
  selector: 'gate-home',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    MatCardModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule,
    MatTabsModule, MatToolbarModule, MatProgressSpinnerModule, MatSnackBarModule
  ],
  template: `
    <mat-toolbar color="primary" class="bar">
      <mat-icon>security</mat-icon>
      <span class="title">Gate Security</span>
      <span class="spacer"></span>
      <span class="who" *ngIf="guardName">{{ guardName }}</span>
      <button mat-icon-button (click)="logout()" aria-label="Logout"><mat-icon>logout</mat-icon></button>
    </mat-toolbar>

    <div class="content">
      <mat-tab-group animationDuration="200ms" (selectedTabChange)="onTabChange($event.index)">
        <!-- New Visitor -->
        <mat-tab label="New Visitor">
          <div class="tab-body">
            <mat-card *ngIf="!active">
              <mat-card-content>
                <form (ngSubmit)="submit()" #f="ngForm">
                  <mat-form-field appearance="outline" class="full-width">
                    <mat-label>Flat / Unit Number</mat-label>
                    <input matInput name="unitNumber" [(ngModel)]="form.unitNumber" required
                           placeholder="e.g. A-101">
                    <mat-icon matPrefix>home</mat-icon>
                  </mat-form-field>

                  <mat-form-field appearance="outline" class="full-width">
                    <mat-label>Visitor / Delivery Person Name</mat-label>
                    <input matInput name="visitorName" [(ngModel)]="form.visitorName" required
                           placeholder="e.g. Courier">
                    <mat-icon matPrefix>person</mat-icon>
                  </mat-form-field>

                  <mat-form-field appearance="outline" class="full-width">
                    <mat-label>Company (optional)</mat-label>
                    <input matInput name="company" [(ngModel)]="form.company" placeholder="e.g. Amazon, Swiggy">
                    <mat-icon matPrefix>local_shipping</mat-icon>
                  </mat-form-field>

                  <mat-form-field appearance="outline" class="full-width">
                    <mat-label>Purpose (optional)</mat-label>
                    <input matInput name="purpose" [(ngModel)]="form.purpose" placeholder="e.g. parcel delivery">
                  </mat-form-field>

                  <div class="photo-row">
                    <button mat-stroked-button type="button" (click)="fileInput.click()">
                      <mat-icon>photo_camera</mat-icon>
                      {{ photoName || 'Capture / attach photo' }}
                    </button>
                    <input #fileInput type="file" accept="image/*" capture="environment"
                           hidden (change)="onPhoto($event)">
                    <button mat-icon-button type="button" *ngIf="form.photo" (click)="clearPhoto()"
                            aria-label="Remove photo"><mat-icon>close</mat-icon></button>
                  </div>

                  <button mat-raised-button color="primary" type="submit" class="full-width submit-btn"
                          [disabled]="submitting || !form.unitNumber || !form.visitorName">
                    <mat-icon *ngIf="!submitting">send</mat-icon>
                    <mat-spinner *ngIf="submitting" diameter="20"></mat-spinner>
                    <span>{{ submitting ? 'Sending...' : 'Send for Approval' }}</span>
                  </button>
                </form>
              </mat-card-content>
            </mat-card>

            <!-- Live status after submit -->
            <mat-card *ngIf="active" class="status-card" [class]="'status-' + active.status.toLowerCase()">
              <div class="status-head">
                <mat-icon class="big">{{ statusIcon(active.status) }}</mat-icon>
                <div>
                  <div class="status-label">{{ statusLabel(active.status) }}</div>
                  <div class="status-sub">{{ active.visitorName }}<span *ngIf="active.company"> · {{ active.company }}</span></div>
                  <div class="status-sub">Flat {{ active.unitNumber }}</div>
                </div>
              </div>

              <div class="status-detail" *ngIf="active.status === 'PENDING'">
                <mat-spinner diameter="20"></mat-spinner>
                <span>Waiting for the flat owner to respond...</span>
              </div>
              <div class="status-detail decided" *ngIf="active.status === 'APPROVED'">
                <span>Allow entry. Approved by {{ active.decidedBy || 'owner' }}.</span>
              </div>
              <div class="status-detail decided" *ngIf="active.status === 'REJECTED'">
                <span>Do not allow entry.<span *ngIf="active.rejectionReason"> Reason: {{ active.rejectionReason }}</span></span>
              </div>
              <div class="status-detail decided" *ngIf="active.status === 'TIMED_OUT'">
                <span>No response in time. Please re-send if the visitor is still waiting.</span>
              </div>

              <div class="status-actions">
                <button mat-raised-button color="primary" (click)="newEntry()">
                  <mat-icon>add</mat-icon> New Entry
                </button>
              </div>
            </mat-card>
          </div>
        </mat-tab>

        <!-- History -->
        <mat-tab label="History">
          <div class="tab-body">
            <div class="hist-head">
              <span>Recent gate requests</span>
              <button mat-icon-button (click)="loadHistory()" aria-label="Refresh"><mat-icon>refresh</mat-icon></button>
            </div>
            <div class="loading" *ngIf="historyLoading"><mat-spinner diameter="30"></mat-spinner></div>
            <div class="empty" *ngIf="!historyLoading && history.length === 0">No requests yet.</div>
            <mat-card *ngFor="let r of history" class="hist-item">
              <div class="hist-row">
                <span class="hist-name">{{ r.visitorName }}<span *ngIf="r.company"> · {{ r.company }}</span></span>
                <span class="chip" [class]="'chip-' + r.status.toLowerCase()">{{ statusLabel(r.status) }}</span>
              </div>
              <div class="hist-meta">
                Flat {{ r.unitNumber }}
                <span *ngIf="r.createdOn"> · {{ r.createdOn | date:'dd MMM, HH:mm' }}</span>
                <span *ngIf="r.createdBy"> · by {{ r.createdBy }}</span>
                <span *ngIf="r.decidedBy"> · decided by {{ r.decidedBy }}</span>
              </div>
            </mat-card>
          </div>
        </mat-tab>
      </mat-tab-group>
    </div>
  `,
  styles: [`
    .bar { position: sticky; top: 0; z-index: 10; gap: 8px; }
    .bar .title { font-size: 18px; }
    .spacer { flex: 1; }
    .who { font-size: 13px; opacity: 0.9; margin-right: 4px; }
    .content { max-width: 560px; margin: 0 auto; }
    .tab-body { padding: 16px; }
    .full-width { width: 100%; }
    .photo-row { display: flex; align-items: center; gap: 8px; margin: 4px 0 12px; }
    .submit-btn { display: flex; align-items: center; justify-content: center; gap: 8px; height: 46px; }
    .status-card { padding: 20px; border-left: 6px solid #90a4ae; }
    .status-card.status-pending { border-color: #f9a825; }
    .status-card.status-approved { border-color: #2e7d32; }
    .status-card.status-rejected { border-color: #c62828; }
    .status-card.status-timed_out { border-color: #757575; }
    .status-head { display: flex; gap: 14px; align-items: center; }
    .status-head .big { font-size: 44px; height: 44px; width: 44px; }
    .status-pending .big { color: #f9a825; }
    .status-approved .big { color: #2e7d32; }
    .status-rejected .big { color: #c62828; }
    .status-timed_out .big { color: #757575; }
    .status-label { font-size: 20px; font-weight: 600; }
    .status-sub { color: #607d8b; font-size: 13px; }
    .status-detail { display: flex; align-items: center; gap: 10px; margin-top: 16px; color: #455a64; }
    .status-detail.decided { font-weight: 500; }
    .status-actions { margin-top: 18px; display: flex; justify-content: flex-end; }
    .hist-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; color: #607d8b; }
    .loading { display: flex; justify-content: center; padding: 24px; }
    .empty { text-align: center; color: #90a4ae; padding: 24px; }
    .hist-item { padding: 12px 14px; margin-bottom: 8px; }
    .hist-row { display: flex; justify-content: space-between; align-items: center; }
    .hist-name { font-weight: 500; }
    .hist-meta { font-size: 12px; color: #90a4ae; margin-top: 2px; }
    .chip { font-size: 11px; font-weight: 600; padding: 2px 8px; border-radius: 12px; }
    .chip-pending { background: #fff8e1; color: #f57f17; }
    .chip-approved { background: #e8f5e9; color: #2e7d32; }
    .chip-rejected { background: #ffebee; color: #c62828; }
    .chip-timed_out { background: #eceff1; color: #546e7a; }
  `]
})
export class GateHomeComponent implements OnInit, OnDestroy {
  guardName = '';
  form: { unitNumber: string; visitorName: string; company: string; purpose: string; photo: File | null } =
    { unitNumber: '', visitorName: '', company: '', purpose: '', photo: null };
  photoName = '';
  submitting = false;

  /** The request currently being tracked (after submit). */
  active: GateVisitorRequest | null = null;

  history: GateVisitorRequest[] = [];
  historyLoading = false;

  private pollSub?: Subscription;

  constructor(
    private gateService: GateVisitorService,
    private authService: AuthService,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.guardName = this.authService.getUser()?.fullName || '';
  }

  ngOnDestroy(): void {
    this.pollSub?.unsubscribe();
  }

  onPhoto(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files && input.files[0];
    if (file) {
      this.form.photo = file;
      this.photoName = file.name;
    }
  }

  clearPhoto(): void {
    this.form.photo = null;
    this.photoName = '';
  }

  submit(): void {
    if (!this.form.unitNumber || !this.form.visitorName) {
      return;
    }
    this.submitting = true;
    this.gateService.create({
      unitNumber: this.form.unitNumber.trim(),
      visitorName: this.form.visitorName.trim(),
      company: this.form.company || undefined,
      purpose: this.form.purpose || undefined,
      photo: this.form.photo
    }).subscribe({
      next: (res: any) => {
        this.submitting = false;
        if (res.success) {
          this.active = res.data;
          this.startPolling();
        }
      },
      error: (err: any) => {
        this.submitting = false;
        this.snackBar.open(err.error?.message || 'Could not send the request. Check the flat number.',
          'Close', { duration: 5000 });
      }
    });
  }

  private startPolling(): void {
    this.pollSub?.unsubscribe();
    if (!this.active) {
      return;
    }
    const id = this.active.visitorRequestId;
    this.pollSub = interval(3000).subscribe(() => {
      this.gateService.getById(id).subscribe({
        next: (res: any) => {
          if (res.success) {
            this.active = res.data;
            if (this.active && this.active.status !== 'PENDING') {
              this.pollSub?.unsubscribe();
            }
          }
        },
        error: () => { /* transient; keep polling */ }
      });
    });
  }

  newEntry(): void {
    this.pollSub?.unsubscribe();
    this.active = null;
    this.form = { unitNumber: '', visitorName: '', company: '', purpose: '', photo: null };
    this.photoName = '';
  }

  onTabChange(index: number): void {
    if (index === 1) {
      this.loadHistory();
    }
  }

  loadHistory(): void {
    this.historyLoading = true;
    this.gateService.list().subscribe({
      next: (res: any) => {
        this.historyLoading = false;
        if (res.success) {
          this.history = res.data || [];
        }
      },
      error: () => { this.historyLoading = false; }
    });
  }

  statusLabel(status: string): string {
    switch (status) {
      case 'PENDING': return 'Awaiting approval';
      case 'APPROVED': return 'Approved — allow entry';
      case 'REJECTED': return 'Rejected — deny entry';
      case 'TIMED_OUT': return 'Timed out';
      default: return status;
    }
  }

  statusIcon(status: string): string {
    switch (status) {
      case 'PENDING': return 'hourglass_top';
      case 'APPROVED': return 'check_circle';
      case 'REJECTED': return 'cancel';
      case 'TIMED_OUT': return 'timer_off';
      default: return 'help';
    }
  }

  logout(): void {
    this.authService.logout();
  }
}
