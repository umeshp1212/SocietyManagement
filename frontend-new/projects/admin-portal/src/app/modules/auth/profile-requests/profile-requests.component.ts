import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { environment } from '@env/environment';

@Component({
  selector: 'app-profile-requests',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    MatCardModule, MatTableModule, MatButtonModule, MatIconModule,
    MatChipsModule, MatSnackBarModule, MatTabsModule, MatTooltipModule,
    MatDialogModule, MatFormFieldModule, MatInputModule
  ],
  template: `
    <div class="container">
      <h2>Member Profile Update Requests</h2>
      <p class="subtitle">Review and approve/reject member requests to update their mobile number or email.</p>

      <mat-tab-group (selectedTabChange)="onTabChange($event)">
        <!-- Pending Tab -->
        <mat-tab>
          <ng-template mat-tab-label>
            <mat-icon>pending_actions</mat-icon>&nbsp;Pending
            <span class="badge" *ngIf="pendingRequests.length > 0">{{ pendingRequests.length }}</span>
          </ng-template>

          <div class="tab-content">
            <div *ngIf="pendingRequests.length === 0" class="empty-state">
              <mat-icon>check_circle_outline</mat-icon>
              <p>No pending requests.</p>
            </div>

            <div *ngFor="let req of pendingRequests" class="request-card">
              <mat-card>
                <mat-card-content>
                  <div class="request-row">
                    <div class="request-info">
                      <div class="owner-name">{{ req.ownerName }}</div>
                      <div class="unit-number">Unit: {{ req.unitNumber }}</div>
                      <div class="request-date">{{ req.createdOn | date:'dd MMM yyyy, HH:mm' }}</div>
                    </div>
                    <span class="status-chip pending">PENDING</span>
                  </div>

                  <div class="changes">
                    <div class="change-row" *ngIf="req.newMobile">
                      <mat-icon>phone</mat-icon>
                      <span class="label">Mobile:</span>
                      <span class="old-value">{{ req.oldMobile || 'Not set' }}</span>
                      <mat-icon class="arrow">arrow_forward</mat-icon>
                      <span class="new-value">{{ req.newMobile }}</span>
                    </div>
                    <div class="change-row" *ngIf="req.newEmail">
                      <mat-icon>email</mat-icon>
                      <span class="label">Email:</span>
                      <span class="old-value">{{ req.oldEmail || 'Not set' }}</span>
                      <mat-icon class="arrow">arrow_forward</mat-icon>
                      <span class="new-value">{{ req.newEmail }}</span>
                    </div>
                    <div class="reason" *ngIf="req.reason">
                      <strong>Reason:</strong> {{ req.reason }}
                    </div>
                  </div>

                  <div class="actions">
                    <button mat-raised-button color="primary" (click)="approve(req)"
                            [disabled]="req.processing">
                      <mat-icon>check</mat-icon> Approve
                    </button>
                    <button mat-raised-button color="warn" (click)="openRejectDialog(req)"
                            [disabled]="req.processing">
                      <mat-icon>close</mat-icon> Reject
                    </button>
                  </div>
                </mat-card-content>
              </mat-card>
            </div>
          </div>
        </mat-tab>

        <!-- All Requests Tab -->
        <mat-tab>
          <ng-template mat-tab-label>
            <mat-icon>history</mat-icon>&nbsp;All Requests
          </ng-template>

          <div class="tab-content">
            <div *ngIf="allRequests.length === 0" class="empty-state">
              <mat-icon>inbox</mat-icon>
              <p>No profile update requests yet.</p>
            </div>

            <table mat-table [dataSource]="allRequests" *ngIf="allRequests.length > 0" class="full-width">
              <ng-container matColumnDef="owner">
                <th mat-header-cell *matHeaderCellDef>Owner</th>
                <td mat-cell *matCellDef="let r">
                  <div>{{ r.ownerName }}</div>
                  <small class="text-muted">{{ r.unitNumber }}</small>
                </td>
              </ng-container>

              <ng-container matColumnDef="type">
                <th mat-header-cell *matHeaderCellDef>Type</th>
                <td mat-cell *matCellDef="let r">{{ r.fieldType }}</td>
              </ng-container>

              <ng-container matColumnDef="changes">
                <th mat-header-cell *matHeaderCellDef>Changes</th>
                <td mat-cell *matCellDef="let r">
                  <div *ngIf="r.newMobile" class="change-mini">
                    Phone: {{ r.oldMobile || '-' }} &rarr; {{ r.newMobile }}
                  </div>
                  <div *ngIf="r.newEmail" class="change-mini">
                    Email: {{ r.oldEmail || '-' }} &rarr; {{ r.newEmail }}
                  </div>
                </td>
              </ng-container>

              <ng-container matColumnDef="status">
                <th mat-header-cell *matHeaderCellDef>Status</th>
                <td mat-cell *matCellDef="let r">
                  <span class="status-chip" [class]="r.status.toLowerCase()">{{ r.status }}</span>
                </td>
              </ng-container>

              <ng-container matColumnDef="date">
                <th mat-header-cell *matHeaderCellDef>Requested</th>
                <td mat-cell *matCellDef="let r">{{ r.createdOn | date:'dd MMM yyyy' }}</td>
              </ng-container>

              <ng-container matColumnDef="reviewedBy">
                <th mat-header-cell *matHeaderCellDef>Reviewed By</th>
                <td mat-cell *matCellDef="let r">
                  {{ r.reviewedBy || '-' }}
                  <small *ngIf="r.reviewedOn" class="text-muted">
                    <br>{{ r.reviewedOn | date:'dd MMM' }}
                  </small>
                </td>
              </ng-container>

              <tr mat-header-row *matHeaderRowDef="historyColumns"></tr>
              <tr mat-row *matRowDef="let row; columns: historyColumns;"></tr>
            </table>
          </div>
        </mat-tab>
      </mat-tab-group>

      <!-- Reject Dialog (inline) -->
      <div class="reject-overlay" *ngIf="rejectingRequest">
        <mat-card class="reject-dialog">
          <h3>Reject Request</h3>
          <p>Rejecting update request from <strong>{{ rejectingRequest.ownerName }}</strong></p>
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Reason for Rejection</mat-label>
            <input matInput [(ngModel)]="rejectionReason" placeholder="e.g., Invalid phone number">
          </mat-form-field>
          <div class="dialog-actions">
            <button mat-button (click)="rejectingRequest = null">Cancel</button>
            <button mat-raised-button color="warn" (click)="reject()"
                    [disabled]="!rejectionReason">
              <mat-icon>close</mat-icon> Reject
            </button>
          </div>
        </mat-card>
      </div>
    </div>
  `,
  styles: [`
    .container { padding: 20px; max-width: 1000px; margin: 0 auto; }
    .subtitle { color: #666; margin-bottom: 20px; font-size: 14px; }
    .tab-content { padding: 16px 0; }
    .empty-state { text-align: center; padding: 40px; color: #999; }
    .empty-state mat-icon { font-size: 48px; height: 48px; width: 48px; margin-bottom: 8px; }
    .full-width { width: 100%; }

    .badge {
      display: inline-flex; align-items: center; justify-content: center;
      background: #e65100; color: white; border-radius: 50%;
      min-width: 20px; height: 20px; font-size: 11px; margin-left: 6px; padding: 0 4px;
    }

    .request-card { margin-bottom: 12px; }
    .request-row { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
    .owner-name { font-weight: 500; font-size: 16px; }
    .unit-number { font-size: 13px; color: #666; }
    .request-date { font-size: 12px; color: #999; }

    .changes { background: #f5f5f5; border-radius: 8px; padding: 12px; margin-bottom: 12px; }
    .change-row {
      display: flex; align-items: center; gap: 8px; font-size: 14px; margin-bottom: 6px;
    }
    .change-row mat-icon { font-size: 18px; height: 18px; width: 18px; color: #888; }
    .change-row .arrow { color: #1976d2; }
    .change-row .label { font-weight: 500; color: #555; min-width: 55px; }
    .old-value { color: #999; text-decoration: line-through; }
    .new-value { color: #2e7d32; font-weight: 500; }
    .reason { font-size: 13px; color: #666; margin-top: 8px; }
    .change-mini { font-size: 12px; }

    .actions { display: flex; gap: 8px; justify-content: flex-end; }

    .status-chip {
      font-size: 11px; font-weight: 500; padding: 2px 8px; border-radius: 12px;
    }
    .pending, .status-chip.pending { background: #fff3e0; color: #e65100; }
    .approved, .status-chip.approved { background: #e8f5e9; color: #2e7d32; }
    .rejected, .status-chip.rejected { background: #ffebee; color: #c62828; }

    .text-muted { color: #999; }

    .reject-overlay {
      position: fixed; top: 0; left: 0; right: 0; bottom: 0;
      background: rgba(0,0,0,0.4); display: flex; align-items: center;
      justify-content: center; z-index: 1000;
    }
    .reject-dialog { width: 400px; padding: 24px; }
    .reject-dialog h3 { margin: 0 0 8px; }
    .reject-dialog p { color: #666; margin-bottom: 16px; }
    .dialog-actions { display: flex; gap: 8px; justify-content: flex-end; }
  `]
})
export class ProfileRequestsComponent implements OnInit {
  pendingRequests: any[] = [];
  allRequests: any[] = [];
  historyColumns = ['owner', 'type', 'changes', 'status', 'date', 'reviewedBy'];
  rejectingRequest: any = null;
  rejectionReason = '';

  constructor(
    private http: HttpClient,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.loadPending();
  }

  onTabChange(event: any): void {
    if (event.index === 1 && this.allRequests.length === 0) {
      this.loadAll();
    }
  }

  loadPending(): void {
    this.http.get<any>(`${environment.apiUrl}/admin/profile-requests/pending`).subscribe({
      next: (res) => {
        if (res.success) this.pendingRequests = res.data;
      }
    });
  }

  loadAll(): void {
    this.http.get<any>(`${environment.apiUrl}/admin/profile-requests`).subscribe({
      next: (res) => {
        if (res.success) this.allRequests = res.data;
      }
    });
  }

  approve(req: any): void {
    req.processing = true;
    this.http.post<any>(
      `${environment.apiUrl}/admin/profile-requests/${req.requestId}/approve?adminName=Admin`, {}
    ).subscribe({
      next: (res) => {
        req.processing = false;
        if (res.success) {
          this.snackBar.open('Request approved. Owner details updated.', 'Close', { duration: 3000 });
          this.pendingRequests = this.pendingRequests.filter(r => r.requestId !== req.requestId);
          this.allRequests = []; // reload on tab switch
        }
      },
      error: (err) => {
        req.processing = false;
        this.snackBar.open(err.error?.message || 'Failed to approve', 'Close', { duration: 3000 });
      }
    });
  }

  openRejectDialog(req: any): void {
    this.rejectingRequest = req;
    this.rejectionReason = '';
  }

  reject(): void {
    if (!this.rejectingRequest) return;
    const req = this.rejectingRequest;
    req.processing = true;

    this.http.post<any>(
      `${environment.apiUrl}/admin/profile-requests/${req.requestId}/reject`,
      { adminName: 'Admin', reason: this.rejectionReason }
    ).subscribe({
      next: (res) => {
        req.processing = false;
        this.rejectingRequest = null;
        if (res.success) {
          this.snackBar.open('Request rejected.', 'Close', { duration: 3000 });
          this.pendingRequests = this.pendingRequests.filter(r => r.requestId !== req.requestId);
          this.allRequests = [];
        }
      },
      error: (err) => {
        req.processing = false;
        this.snackBar.open(err.error?.message || 'Failed to reject', 'Close', { duration: 3000 });
      }
    });
  }
}
