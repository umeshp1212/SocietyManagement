import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Subscription, interval } from 'rxjs';
import { MemberAuthService } from '@core/services/member-auth.service';
import { VisitorRequest, VisitorService } from '@core/services/visitor.service';

/**
 * Owner-facing screen listing visitor/delivery requests awaiting approval for the
 * member's flats, with Approve / Reject actions. Polls every few seconds so a new
 * request appears promptly even without push, and so a request decided elsewhere
 * (another owner, or a timeout) drops off the list.
 */
@Component({
  selector: 'app-member-visitor-approvals',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    MatCardModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatSnackBarModule, MatProgressSpinnerModule
  ],
  template: `
    <div class="va-page">
      <div class="page-header">
        <button mat-icon-button (click)="goBack()" aria-label="Back"><mat-icon>arrow_back</mat-icon></button>
        <h2>Visitor Approvals</h2>
        <span class="spacer"></span>
        <button mat-icon-button (click)="load()" aria-label="Refresh"><mat-icon>refresh</mat-icon></button>
      </div>

      <div class="info-banner">
        <mat-icon>info</mat-icon>
        <span>A security guard sends a request when a delivery or visitor arrives for your flat.
          Approve to allow entry, or reject to deny it.</span>
      </div>

      <div class="loading" *ngIf="loading && requests.length === 0">
        <mat-spinner diameter="36"></mat-spinner>
      </div>

      <div class="empty" *ngIf="!loading && requests.length === 0">
        <mat-icon>verified_user</mat-icon>
        <p>No visitors waiting for approval right now.</p>
      </div>

      <mat-card *ngFor="let r of requests" class="req-card">
        <div class="req-body">
          <img *ngIf="photoUrl(r) as url" [src]="url" alt="Visitor photo" class="req-photo" />
          <div class="req-photo placeholder" *ngIf="!photoUrl(r)"><mat-icon>person</mat-icon></div>
          <div class="req-info">
            <div class="req-name">{{ r.visitorName }}</div>
            <div class="req-sub" *ngIf="r.company">{{ r.company }}</div>
            <div class="req-sub" *ngIf="r.purpose">{{ r.purpose }}</div>
            <div class="req-meta">
              Flat {{ r.unitNumber }}
              <span *ngIf="r.createdOn"> · {{ r.createdOn | date:'dd MMM, HH:mm' }}</span>
            </div>
          </div>
        </div>

        <div class="reject-row" *ngIf="rejectingId === r.visitorRequestId">
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Reason (optional)</mat-label>
            <input matInput [(ngModel)]="rejectReason" name="rejectReason" placeholder="e.g. Not expecting a delivery">
          </mat-form-field>
        </div>

        <div class="req-actions">
          <ng-container *ngIf="rejectingId !== r.visitorRequestId">
            <button mat-stroked-button color="warn" [disabled]="actingId === r.visitorRequestId"
                    (click)="startReject(r)">
              <mat-icon>block</mat-icon> Reject
            </button>
            <button mat-raised-button color="primary" [disabled]="actingId === r.visitorRequestId"
                    (click)="approve(r)">
              <mat-icon>check_circle</mat-icon> Approve
            </button>
          </ng-container>
          <ng-container *ngIf="rejectingId === r.visitorRequestId">
            <button mat-button (click)="cancelReject()">Cancel</button>
            <button mat-raised-button color="warn" [disabled]="actingId === r.visitorRequestId"
                    (click)="confirmReject(r)">
              Confirm Reject
            </button>
          </ng-container>
        </div>
      </mat-card>
    </div>
  `,
  styles: [`
    .va-page { max-width: 640px; margin: 0 auto; padding: 20px; }
    .page-header { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
    .page-header h2 { margin: 0; }
    .spacer { flex: 1; }
    .info-banner { display: flex; align-items: flex-start; gap: 8px; padding: 12px 16px;
      background: #e3f2fd; border-radius: 8px; color: #0d47a1; font-size: 13px; margin-bottom: 16px; }
    .info-banner mat-icon { flex-shrink: 0; }
    .loading { display: flex; justify-content: center; padding: 32px; }
    .empty { text-align: center; color: #888; padding: 40px 16px; }
    .empty mat-icon { font-size: 48px; height: 48px; width: 48px; color: #c8e6c9; }
    .req-card { margin-bottom: 12px; }
    .req-body { display: flex; gap: 12px; align-items: center; }
    .req-photo { width: 64px; height: 64px; border-radius: 8px; object-fit: cover; flex-shrink: 0; }
    .req-photo.placeholder { display: flex; align-items: center; justify-content: center;
      background: #eceff1; color: #90a4ae; }
    .req-info { min-width: 0; }
    .req-name { font-weight: 600; font-size: 16px; }
    .req-sub { color: #546e7a; font-size: 13px; }
    .req-meta { color: #90a4ae; font-size: 12px; margin-top: 2px; }
    .reject-row { margin-top: 10px; }
    .full-width { width: 100%; }
    .req-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 10px; }
  `]
})
export class MemberVisitorApprovalsComponent implements OnInit, OnDestroy {
  requests: VisitorRequest[] = [];
  loading = false;
  actingId: number | null = null;
  rejectingId: number | null = null;
  rejectReason = '';

  private pollSub?: Subscription;

  constructor(
    private visitorService: VisitorService,
    private memberAuth: MemberAuthService,
    private router: Router,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    if (!this.memberAuth.isLoggedIn()) {
      this.router.navigate(['/login']);
      return;
    }
    this.load();
    // Poll every 5s so new/decided requests reflect promptly without push.
    this.pollSub = interval(5000).subscribe(() => this.load(true));
  }

  ngOnDestroy(): void {
    this.pollSub?.unsubscribe();
  }

  load(silent = false): void {
    if (!silent) {
      this.loading = true;
    }
    this.visitorService.getPending().subscribe({
      next: (res: any) => {
        this.loading = false;
        if (res.success) {
          this.requests = res.data || [];
        }
      },
      error: () => { this.loading = false; }
    });
  }

  photoUrl(r: VisitorRequest): string | null {
    return this.visitorService.photoUrl(r.photoPath);
  }

  approve(r: VisitorRequest): void {
    this.actingId = r.visitorRequestId;
    this.visitorService.approve(r.visitorRequestId).subscribe({
      next: (res: any) => {
        this.actingId = null;
        this.snackBar.open(res.message || 'Entry approved.', 'Close', { duration: 3000 });
        this.removeLocally(r.visitorRequestId);
      },
      error: (err: any) => {
        this.actingId = null;
        this.snackBar.open(err.error?.message || 'Could not approve. It may already be decided.', 'Close', { duration: 4000 });
        this.load(true);
      }
    });
  }

  startReject(r: VisitorRequest): void {
    this.rejectingId = r.visitorRequestId;
    this.rejectReason = '';
  }

  cancelReject(): void {
    this.rejectingId = null;
    this.rejectReason = '';
  }

  confirmReject(r: VisitorRequest): void {
    this.actingId = r.visitorRequestId;
    this.visitorService.reject(r.visitorRequestId, this.rejectReason || undefined).subscribe({
      next: (res: any) => {
        this.actingId = null;
        this.rejectingId = null;
        this.snackBar.open(res.message || 'Entry rejected.', 'Close', { duration: 3000 });
        this.removeLocally(r.visitorRequestId);
      },
      error: (err: any) => {
        this.actingId = null;
        this.rejectingId = null;
        this.snackBar.open(err.error?.message || 'Could not reject. It may already be decided.', 'Close', { duration: 4000 });
        this.load(true);
      }
    });
  }

  private removeLocally(requestId: number): void {
    this.requests = this.requests.filter(r => r.visitorRequestId !== requestId);
  }

  goBack(): void {
    this.router.navigate(['/dashboard']);
  }
}
