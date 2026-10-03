import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { environment } from '@env/environment';

@Component({
  selector: 'app-registration-requests',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    MatCardModule, MatButtonModule, MatIconModule, MatSnackBarModule,
    MatTabsModule, MatTableModule, MatFormFieldModule, MatInputModule, MatSelectModule
  ],
  template: `
    <div class="container">
      <h2>Member Registration Requests</h2>
      <p class="subtitle">Members who registered their email and mobile from the portal. Link them to an existing owner to approve.</p>

      <mat-tab-group (selectedTabChange)="onTabChange($event)">
        <!-- Pending -->
        <mat-tab>
          <ng-template mat-tab-label>
            <mat-icon>pending_actions</mat-icon>&nbsp;Pending
            <span class="badge" *ngIf="pendingRequests.length > 0">{{ pendingRequests.length }}</span>
          </ng-template>

          <div class="tab-content">
            <div *ngIf="pendingRequests.length === 0" class="empty-state">
              <mat-icon>check_circle_outline</mat-icon>
              <p>No pending registration requests.</p>
            </div>

            <div *ngFor="let req of pendingRequests" class="request-card">
              <mat-card>
                <mat-card-content>
                  <div class="request-header-row">
                    <div>
                      <div class="detail-row">
                        <mat-icon>email</mat-icon> <strong>{{ req.email }}</strong>
                      </div>
                      <div class="detail-row">
                        <mat-icon>phone</mat-icon> {{ req.mobile }}
                      </div>
                      <div class="detail-row meta">
                        <mat-icon>schedule</mat-icon> {{ req.createdOn | date:'dd MMM yyyy, HH:mm' }}
                        <span class="verified-badge" *ngIf="req.emailVerified">
                          <mat-icon>verified</mat-icon> Email Verified
                        </span>
                      </div>
                    </div>
                    <span class="status-chip pending">PENDING</span>
                  </div>

                  <!-- Approve: Shows unit info, owner selection if multiple -->
                  <div class="approve-section" *ngIf="!req.showReject">
                    <div class="unit-info" *ngIf="req.unitNumber">
                      <mat-icon>apartment</mat-icon>
                      <strong>Claimed Unit: {{ req.unitNumber }}</strong>
                    </div>

                    <!-- Owner selection (shown when unit has multiple owners) -->
                    <div *ngIf="req.unitOwners?.length > 1" class="owner-select-section">
                      <p class="owner-hint">This unit has multiple owners. Select who is registering:</p>
                      <mat-form-field appearance="outline" class="full-width">
                        <mat-label>Select Owner / Co-owner</mat-label>
                        <mat-select [(value)]="req.selectedOwnerId">
                          <mat-option *ngFor="let o of req.unitOwners" [value]="o.ownerId"
                                      [disabled]="o.hasPhone">
                            {{ o.fullName }}
                            {{ o.isPrimary ? '(Primary)' : '(Co-owner)' }}
                            <span *ngIf="o.hasPhone" class="already-registered"> - Already registered</span>
                          </mat-option>
                        </mat-select>
                      </mat-form-field>
                    </div>

                    <div class="actions">
                      <button mat-raised-button color="primary"
                              [disabled]="req.processing || (req.unitOwners?.length > 1 && !req.selectedOwnerId)"
                              (click)="approve(req)">
                        <mat-icon>check</mat-icon> Approve
                      </button>
                      <button mat-raised-button color="warn"
                              [disabled]="req.processing"
                              (click)="req.showReject = true">
                        <mat-icon>close</mat-icon> Reject
                      </button>
                    </div>
                  </div>

                  <!-- Reject: Reason input -->
                  <div class="reject-section" *ngIf="req.showReject">
                    <mat-form-field appearance="outline" class="full-width">
                      <mat-label>Reason for Rejection</mat-label>
                      <input matInput [(ngModel)]="req.rejectionReason"
                             placeholder="e.g., Not a society member">
                    </mat-form-field>
                    <div class="actions">
                      <button mat-button (click)="req.showReject = false">Cancel</button>
                      <button mat-raised-button color="warn"
                              [disabled]="!req.rejectionReason || req.processing"
                              (click)="reject(req)">
                        <mat-icon>close</mat-icon> Reject
                      </button>
                    </div>
                  </div>
                </mat-card-content>
              </mat-card>
            </div>
          </div>
        </mat-tab>

        <!-- History -->
        <mat-tab>
          <ng-template mat-tab-label>
            <mat-icon>history</mat-icon>&nbsp;All Requests
          </ng-template>

          <div class="tab-content">
            <div *ngIf="allRequests.length === 0" class="empty-state">
              <mat-icon>inbox</mat-icon>
              <p>No registration requests yet.</p>
            </div>

            <table mat-table [dataSource]="allRequests" *ngIf="allRequests.length > 0" class="full-width">
              <ng-container matColumnDef="email">
                <th mat-header-cell *matHeaderCellDef>Email</th>
                <td mat-cell *matCellDef="let r">{{ r.email }}</td>
              </ng-container>
              <ng-container matColumnDef="mobile">
                <th mat-header-cell *matHeaderCellDef>Mobile</th>
                <td mat-cell *matCellDef="let r">{{ r.mobile }}</td>
              </ng-container>
              <ng-container matColumnDef="linkedOwner">
                <th mat-header-cell *matHeaderCellDef>Linked Owner</th>
                <td mat-cell *matCellDef="let r">{{ r.linkedOwnerName || '-' }}</td>
              </ng-container>
              <ng-container matColumnDef="status">
                <th mat-header-cell *matHeaderCellDef>Status</th>
                <td mat-cell *matCellDef="let r">
                  <span class="status-chip" [class]="r.status.toLowerCase()">{{ r.status }}</span>
                </td>
              </ng-container>
              <ng-container matColumnDef="date">
                <th mat-header-cell *matHeaderCellDef>Date</th>
                <td mat-cell *matCellDef="let r">{{ r.createdOn | date:'dd MMM yyyy' }}</td>
              </ng-container>
              <ng-container matColumnDef="reviewedBy">
                <th mat-header-cell *matHeaderCellDef>Reviewed By</th>
                <td mat-cell *matCellDef="let r">{{ r.reviewedBy || '-' }}</td>
              </ng-container>

              <tr mat-header-row *matHeaderRowDef="historyColumns"></tr>
              <tr mat-row *matRowDef="let row; columns: historyColumns;"></tr>
            </table>
          </div>
        </mat-tab>
      </mat-tab-group>
    </div>
  `,
  styles: [`
    .container { padding: 20px; max-width: 900px; margin: 0 auto; }
    .subtitle { color: #666; font-size: 14px; margin-bottom: 20px; }
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
    .request-header-row { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; }
    .detail-row { display: flex; align-items: center; gap: 8px; font-size: 14px; margin-bottom: 4px; }
    .detail-row mat-icon { font-size: 18px; height: 18px; width: 18px; color: #888; }
    .detail-row.meta { font-size: 12px; color: #999; }
    .verified-badge {
      display: inline-flex; align-items: center; gap: 2px;
      color: #2e7d32; font-size: 11px; margin-left: 12px;
    }
    .verified-badge mat-icon { font-size: 14px; height: 14px; width: 14px; }

    .approve-section { background: #f5f5f5; border-radius: 8px; padding: 16px; }
    .unit-info { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; color: #1565c0; }
    .unit-info mat-icon { font-size: 20px; height: 20px; width: 20px; }
    .owner-select-section { margin-bottom: 12px; }
    .owner-hint { font-size: 13px; color: #666; margin-bottom: 8px; }
    .already-registered { color: #999; font-style: italic; }
    .owner-select { width: 100%; margin-bottom: 8px; }
    .reject-section { background: #fff3e0; border-radius: 8px; padding: 16px; }
    .actions { display: flex; gap: 8px; justify-content: flex-end; }

    .status-chip { font-size: 11px; font-weight: 500; padding: 2px 8px; border-radius: 12px; }
    .pending { background: #fff3e0; color: #e65100; }
    .approved { background: #e8f5e9; color: #2e7d32; }
    .rejected { background: #ffebee; color: #c62828; }
  `]
})
export class RegistrationRequestsComponent implements OnInit {
  pendingRequests: any[] = [];
  allRequests: any[] = [];
  owners: any[] = [];
  historyColumns = ['email', 'mobile', 'linkedOwner', 'status', 'date', 'reviewedBy'];

  constructor(private http: HttpClient, private snackBar: MatSnackBar) {}

  ngOnInit(): void {
    this.loadPending();
  }

  onTabChange(event: any): void {
    if (event.index === 1 && this.allRequests.length === 0) this.loadAll();
  }

  loadPending(): void {
    this.http.get<any>(`${environment.apiUrl}/admin/registration-requests/pending`).subscribe({
      next: (res) => {
        if (res.success) {
          this.pendingRequests = res.data;
          // Load unit owners for each request
          this.pendingRequests.forEach(req => {
            this.http.get<any>(`${environment.apiUrl}/admin/registration-requests/${req.requestId}/unit-owners`).subscribe({
              next: (ownerRes) => {
                if (ownerRes.success) req.unitOwners = ownerRes.data;
              }
            });
          });
        }
      }
    });
  }

  loadAll(): void {
    this.http.get<any>(`${environment.apiUrl}/admin/registration-requests`).subscribe({
      next: (res) => { if (res.success) this.allRequests = res.data; }
    });
  }

  approve(req: any): void {
    req.processing = true;
    const body: any = { adminName: 'Admin' };
    if (req.selectedOwnerId) body.ownerId = req.selectedOwnerId;
    this.http.post<any>(`${environment.apiUrl}/admin/registration-requests/${req.requestId}/approve`, body).subscribe({
      next: (res) => {
        req.processing = false;
        if (res.success) {
          this.snackBar.open('Approved! Owner details updated.', 'Close', { duration: 3000 });
          this.pendingRequests = this.pendingRequests.filter(r => r.requestId !== req.requestId);
          this.allRequests = [];
        }
      },
      error: (err) => {
        req.processing = false;
        this.snackBar.open(err.error?.message || 'Failed', 'Close', { duration: 3000 });
      }
    });
  }

  reject(req: any): void {
    req.processing = true;
    this.http.post<any>(`${environment.apiUrl}/admin/registration-requests/${req.requestId}/reject`, {
      adminName: 'Admin', reason: req.rejectionReason
    }).subscribe({
      next: (res) => {
        req.processing = false;
        if (res.success) {
          this.snackBar.open('Request rejected.', 'Close', { duration: 3000 });
          this.pendingRequests = this.pendingRequests.filter(r => r.requestId !== req.requestId);
          this.allRequests = [];
        }
      },
      error: (err) => {
        req.processing = false;
        this.snackBar.open(err.error?.message || 'Failed', 'Close', { duration: 3000 });
      }
    });
  }
}
