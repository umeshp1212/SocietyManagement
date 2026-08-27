import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { environment } from '@env/environment';

@Component({
  selector: 'app-member-requests',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    MatCardModule, MatButtonModule, MatIconModule, MatSnackBarModule,
    MatTabsModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatTableModule
  ],
  template: `
    <div class="container">
      <h2>Member Requests</h2>

      <mat-tab-group>
        <!-- ==================== REGISTRATION REQUESTS ==================== -->
        <mat-tab>
          <ng-template mat-tab-label>
            <mat-icon>person_add</mat-icon>&nbsp;Registration
            <span class="badge" *ngIf="regPending.length">{{ regPending.length }}</span>
          </ng-template>
          <div class="tab-content">
            <p class="hint">New members registering their email and mobile from the portal.</p>

            <div *ngIf="regPending.length === 0" class="empty">
              <mat-icon>check_circle_outline</mat-icon>
              <p>No pending registration requests.</p>
            </div>

            <div *ngFor="let req of regPending" class="request-card">
              <mat-card>
                <mat-card-content>
                  <div class="row-between">
                    <div>
                      <div class="detail"><mat-icon>apartment</mat-icon> <strong>{{ req.unitNumber }}</strong></div>
                      <div class="detail"><mat-icon>email</mat-icon> {{ req.email }}</div>
                      <div class="detail"><mat-icon>phone</mat-icon> {{ req.mobile }}</div>
                      <div class="detail meta">
                        <mat-icon>schedule</mat-icon> {{ req.createdOn | date:'dd MMM yyyy, HH:mm' }}
                        <span class="verified" *ngIf="req.emailVerified"><mat-icon>verified</mat-icon> Email Verified</span>
                      </div>
                    </div>
                    <span class="chip pending">PENDING</span>
                  </div>

                  <div class="action-box" *ngIf="!req.showReject">
                    <div *ngIf="req.unitOwners?.length > 1" class="owner-pick">
                      <p class="pick-hint">Multiple owners — select who is registering:</p>
                      <mat-form-field appearance="outline" class="full-width">
                        <mat-label>Owner / Co-owner</mat-label>
                        <mat-select [(value)]="req.selectedOwnerId">
                          <mat-option *ngFor="let o of req.unitOwners" [value]="o.ownerId" [disabled]="o.hasPhone">
                            {{ o.fullName }} {{ o.isPrimary ? '(Primary)' : '(Co-owner)' }}
                            <span *ngIf="o.hasPhone" class="disabled-note"> — Already registered</span>
                          </mat-option>
                        </mat-select>
                      </mat-form-field>
                    </div>
                    <div class="actions">
                      <button mat-raised-button color="primary"
                              [disabled]="req.processing || (req.unitOwners?.length > 1 && !req.selectedOwnerId)"
                              (click)="approveReg(req)">
                        <mat-icon>check</mat-icon> Approve
                      </button>
                      <button mat-raised-button color="warn" [disabled]="req.processing"
                              (click)="req.showReject = true">
                        <mat-icon>close</mat-icon> Reject
                      </button>
                    </div>
                  </div>

                  <div class="reject-box" *ngIf="req.showReject">
                    <mat-form-field appearance="outline" class="full-width">
                      <mat-label>Rejection Reason</mat-label>
                      <input matInput [(ngModel)]="req.rejectionReason">
                    </mat-form-field>
                    <div class="actions">
                      <button mat-button (click)="req.showReject = false">Cancel</button>
                      <button mat-raised-button color="warn" [disabled]="!req.rejectionReason"
                              (click)="rejectReg(req)">Reject</button>
                    </div>
                  </div>
                </mat-card-content>
              </mat-card>
            </div>
          </div>
        </mat-tab>

        <!-- ==================== PROFILE UPDATE REQUESTS ==================== -->
        <mat-tab>
          <ng-template mat-tab-label>
            <mat-icon>edit</mat-icon>&nbsp;Profile Updates
            <span class="badge" *ngIf="profilePending.length">{{ profilePending.length }}</span>
          </ng-template>
          <div class="tab-content">
            <p class="hint">Existing members requesting to change their mobile or email.</p>

            <div *ngIf="profilePending.length === 0" class="empty">
              <mat-icon>check_circle_outline</mat-icon>
              <p>No pending profile update requests.</p>
            </div>

            <div *ngFor="let req of profilePending" class="request-card">
              <mat-card>
                <mat-card-content>
                  <div class="row-between">
                    <div>
                      <div class="detail"><strong>{{ req.ownerName }}</strong></div>
                      <div class="detail meta">{{ req.unitNumber }} | {{ req.createdOn | date:'dd MMM yyyy, HH:mm' }}</div>
                    </div>
                    <span class="chip pending">PENDING</span>
                  </div>

                  <div class="changes">
                    <div *ngIf="req.newMobile" class="change-row">
                      <mat-icon>phone</mat-icon>
                      <span>Mobile:</span>
                      <span class="old">{{ req.oldMobile || 'Not set' }}</span>
                      <mat-icon class="arrow">arrow_forward</mat-icon>
                      <span class="new">{{ req.newMobile }}</span>
                    </div>
                    <div *ngIf="req.newEmail" class="change-row">
                      <mat-icon>email</mat-icon>
                      <span>Email:</span>
                      <span class="old">{{ req.oldEmail || 'Not set' }}</span>
                      <mat-icon class="arrow">arrow_forward</mat-icon>
                      <span class="new">{{ req.newEmail }}</span>
                    </div>
                    <div *ngIf="req.reason" class="reason"><strong>Reason:</strong> {{ req.reason }}</div>
                  </div>

                  <div class="actions" *ngIf="!req.showReject">
                    <button mat-raised-button color="primary" [disabled]="req.processing"
                            (click)="approveProfile(req)">
                      <mat-icon>check</mat-icon> Approve
                    </button>
                    <button mat-raised-button color="warn" [disabled]="req.processing"
                            (click)="req.showReject = true">
                      <mat-icon>close</mat-icon> Reject
                    </button>
                  </div>

                  <div class="reject-box" *ngIf="req.showReject">
                    <mat-form-field appearance="outline" class="full-width">
                      <mat-label>Rejection Reason</mat-label>
                      <input matInput [(ngModel)]="req.rejectionReason">
                    </mat-form-field>
                    <div class="actions">
                      <button mat-button (click)="req.showReject = false">Cancel</button>
                      <button mat-raised-button color="warn" [disabled]="!req.rejectionReason"
                              (click)="rejectProfile(req)">Reject</button>
                    </div>
                  </div>
                </mat-card-content>
              </mat-card>
            </div>
          </div>
        </mat-tab>
      </mat-tab-group>
    </div>
  `,
  styles: [`
    .container { padding: 20px; max-width: 900px; margin: 0 auto; }
    .tab-content { padding: 16px 0; }
    .hint { color: #666; font-size: 13px; margin-bottom: 16px; }
    .empty { text-align: center; padding: 40px; color: #999; }
    .empty mat-icon { font-size: 48px; height: 48px; width: 48px; margin-bottom: 8px; }
    .full-width { width: 100%; }
    .badge { display: inline-flex; align-items: center; justify-content: center; background: #e65100; color: white; border-radius: 50%; min-width: 20px; height: 20px; font-size: 11px; margin-left: 6px; padding: 0 4px; }

    .request-card { margin-bottom: 12px; }
    .row-between { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
    .detail { display: flex; align-items: center; gap: 8px; font-size: 14px; margin-bottom: 4px; }
    .detail mat-icon { font-size: 18px; height: 18px; width: 18px; color: #888; }
    .detail.meta { font-size: 12px; color: #999; }
    .verified { display: inline-flex; align-items: center; gap: 2px; color: #2e7d32; font-size: 11px; margin-left: 12px; }
    .verified mat-icon { font-size: 14px; height: 14px; width: 14px; }

    .chip { font-size: 11px; font-weight: 500; padding: 2px 8px; border-radius: 12px; }
    .pending { background: #fff3e0; color: #e65100; }

    .action-box, .reject-box { background: #f5f5f5; border-radius: 8px; padding: 16px; }
    .reject-box { background: #fff3e0; }
    .actions { display: flex; gap: 8px; justify-content: flex-end; margin-top: 8px; }
    .owner-pick { margin-bottom: 12px; }
    .pick-hint { font-size: 13px; color: #666; margin-bottom: 8px; }
    .disabled-note { color: #999; font-style: italic; }

    .changes { background: #f5f5f5; border-radius: 8px; padding: 12px; margin-bottom: 12px; }
    .change-row { display: flex; align-items: center; gap: 8px; font-size: 14px; margin-bottom: 6px; }
    .change-row mat-icon { font-size: 18px; height: 18px; width: 18px; color: #888; }
    .change-row .arrow { color: #1976d2; }
    .old { color: #999; text-decoration: line-through; }
    .new { color: #2e7d32; font-weight: 500; }
    .reason { font-size: 13px; color: #666; margin-top: 8px; }
  `]
})
export class MemberRequestsComponent implements OnInit {
  regPending: any[] = [];
  profilePending: any[] = [];

  constructor(private http: HttpClient, private snackBar: MatSnackBar) {}

  ngOnInit(): void {
    this.loadRegPending();
    this.loadProfilePending();
  }

  // ===== Registration =====

  loadRegPending(): void {
    this.http.get<any>(`${environment.apiUrl}/admin/registration-requests/pending`).subscribe({
      next: (res) => {
        if (res.success) {
          this.regPending = res.data;
          this.regPending.forEach(req => {
            this.http.get<any>(`${environment.apiUrl}/admin/registration-requests/${req.requestId}/unit-owners`).subscribe({
              next: (r) => { if (r.success) req.unitOwners = r.data; }
            });
          });
        }
      }
    });
  }

  approveReg(req: any): void {
    req.processing = true;
    const body: any = { adminName: 'Admin' };
    if (req.selectedOwnerId) body.ownerId = req.selectedOwnerId;
    this.http.post<any>(`${environment.apiUrl}/admin/registration-requests/${req.requestId}/approve`, body).subscribe({
      next: (res) => {
        req.processing = false;
        if (res.success) {
          this.snackBar.open('Approved! Owner details updated.', 'Close', { duration: 3000 });
          this.regPending = this.regPending.filter(r => r.requestId !== req.requestId);
        }
      },
      error: (err) => { req.processing = false; this.snackBar.open(err.error?.message || 'Failed', 'Close', { duration: 3000 }); }
    });
  }

  rejectReg(req: any): void {
    req.processing = true;
    this.http.post<any>(`${environment.apiUrl}/admin/registration-requests/${req.requestId}/reject`, {
      adminName: 'Admin', reason: req.rejectionReason
    }).subscribe({
      next: (res) => {
        req.processing = false;
        if (res.success) {
          this.snackBar.open('Rejected.', 'Close', { duration: 3000 });
          this.regPending = this.regPending.filter(r => r.requestId !== req.requestId);
        }
      },
      error: (err) => { req.processing = false; this.snackBar.open(err.error?.message || 'Failed', 'Close', { duration: 3000 }); }
    });
  }

  // ===== Profile Updates =====

  loadProfilePending(): void {
    this.http.get<any>(`${environment.apiUrl}/admin/profile-requests/pending`).subscribe({
      next: (res) => { if (res.success) this.profilePending = res.data; }
    });
  }

  approveProfile(req: any): void {
    req.processing = true;
    this.http.post<any>(`${environment.apiUrl}/admin/profile-requests/${req.requestId}/approve?adminName=Admin`, {}).subscribe({
      next: (res) => {
        req.processing = false;
        if (res.success) {
          this.snackBar.open('Approved. Owner details updated.', 'Close', { duration: 3000 });
          this.profilePending = this.profilePending.filter(r => r.requestId !== req.requestId);
        }
      },
      error: (err) => { req.processing = false; this.snackBar.open(err.error?.message || 'Failed', 'Close', { duration: 3000 }); }
    });
  }

  rejectProfile(req: any): void {
    req.processing = true;
    this.http.post<any>(`${environment.apiUrl}/admin/profile-requests/${req.requestId}/reject`, {
      adminName: 'Admin', reason: req.rejectionReason
    }).subscribe({
      next: (res) => {
        req.processing = false;
        if (res.success) {
          this.snackBar.open('Rejected.', 'Close', { duration: 3000 });
          this.profilePending = this.profilePending.filter(r => r.requestId !== req.requestId);
        }
      },
      error: (err) => { req.processing = false; this.snackBar.open(err.error?.message || 'Failed', 'Close', { duration: 3000 }); }
    });
  }
}
