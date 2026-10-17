import { Component, OnInit } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { environment } from '@env/environment';

interface VisitorRequestRow {
  visitorRequestId: number;
  unitNumber: string;
  visitorName: string;
  company?: string;
  purpose?: string;
  photoPath?: string;
  status: string;
  createdBy?: string;
  createdOn?: string;
  decidedBy?: string;
  decidedOn?: string;
  rejectionReason?: string;
}

/**
 * Read-only admin view of the visitor/delivery gate log for audit. Lists every gate
 * request (newest first) with who the guard was, the flat, the decision, and who
 * decided. Backed by GET /api/gate/visitor-requests (VISITOR_VIEW authority, granted
 * to society-wide admin roles).
 */
@Component({
  selector: 'app-visitor-log',
  standalone: true,
  imports: [
    CommonModule, FormsModule, DatePipe,
    MatCardModule, MatTableModule, MatIconModule, MatButtonModule,
    MatFormFieldModule, MatSelectModule, MatProgressSpinnerModule, MatSnackBarModule
  ],
  template: `
    <div class="log-page">
      <div class="header">
        <h2>Visitor / Delivery Gate Log</h2>
        <div class="controls">
          <mat-form-field appearance="outline" class="status-filter">
            <mat-label>Status</mat-label>
            <mat-select [(ngModel)]="status" (selectionChange)="load()">
              <mat-option [value]="''">All</mat-option>
              <mat-option value="PENDING">Pending</mat-option>
              <mat-option value="APPROVED">Approved</mat-option>
              <mat-option value="REJECTED">Rejected</mat-option>
              <mat-option value="TIMED_OUT">Timed Out</mat-option>
            </mat-select>
          </mat-form-field>
          <button mat-icon-button (click)="load()" aria-label="Refresh"><mat-icon>refresh</mat-icon></button>
        </div>
      </div>

      <div class="loading" *ngIf="loading"><mat-spinner diameter="36"></mat-spinner></div>

      <mat-card *ngIf="!loading">
        <mat-card-content>
          <div class="empty" *ngIf="rows.length === 0">No gate requests found.</div>

          <table mat-table [dataSource]="rows" *ngIf="rows.length > 0" class="full-width">
            <ng-container matColumnDef="when">
              <th mat-header-cell *matHeaderCellDef>When</th>
              <td mat-cell *matCellDef="let r">{{ r.createdOn | date:'dd MMM yyyy, HH:mm' }}</td>
            </ng-container>
            <ng-container matColumnDef="flat">
              <th mat-header-cell *matHeaderCellDef>Flat</th>
              <td mat-cell *matCellDef="let r">{{ r.unitNumber }}</td>
            </ng-container>
            <ng-container matColumnDef="visitor">
              <th mat-header-cell *matHeaderCellDef>Visitor</th>
              <td mat-cell *matCellDef="let r">
                {{ r.visitorName }}<span *ngIf="r.company" class="muted"> · {{ r.company }}</span>
              </td>
            </ng-container>
            <ng-container matColumnDef="guard">
              <th mat-header-cell *matHeaderCellDef>Guard</th>
              <td mat-cell *matCellDef="let r">{{ r.createdBy || '-' }}</td>
            </ng-container>
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>Status</th>
              <td mat-cell *matCellDef="let r">
                <span class="chip" [class]="'chip-' + r.status.toLowerCase()">{{ label(r.status) }}</span>
              </td>
            </ng-container>
            <ng-container matColumnDef="decidedBy">
              <th mat-header-cell *matHeaderCellDef>Decided By</th>
              <td mat-cell *matCellDef="let r">
                {{ r.decidedBy || '-' }}
                <span *ngIf="r.rejectionReason" class="muted"> ({{ r.rejectionReason }})</span>
              </td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="cols"></tr>
            <tr mat-row *matRowDef="let row; columns: cols;"></tr>
          </table>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .log-page { padding: 20px; max-width: 1100px; margin: 0 auto; }
    .header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; }
    .header h2 { margin: 0; }
    .controls { display: flex; align-items: center; gap: 8px; }
    .status-filter { width: 160px; }
    .loading { display: flex; justify-content: center; padding: 40px; }
    .empty { text-align: center; color: #888; padding: 32px; }
    .full-width { width: 100%; }
    .muted { color: #90a4ae; }
    .chip { font-size: 11px; font-weight: 600; padding: 2px 8px; border-radius: 12px; }
    .chip-pending { background: #fff8e1; color: #f57f17; }
    .chip-approved { background: #e8f5e9; color: #2e7d32; }
    .chip-rejected { background: #ffebee; color: #c62828; }
    .chip-timed_out { background: #eceff1; color: #546e7a; }
  `]
})
export class VisitorLogComponent implements OnInit {
  rows: VisitorRequestRow[] = [];
  cols = ['when', 'flat', 'visitor', 'guard', 'status', 'decidedBy'];
  status = '';
  loading = false;

  constructor(private http: HttpClient, private snackBar: MatSnackBar) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    const url = this.status
      ? `${environment.apiUrl}/gate/visitor-requests?status=${this.status}`
      : `${environment.apiUrl}/gate/visitor-requests`;
    this.http.get<any>(url).subscribe({
      next: (res: any) => {
        this.loading = false;
        if (res.success) {
          this.rows = res.data || [];
        }
      },
      error: (err: any) => {
        this.loading = false;
        this.snackBar.open(err.error?.message || 'Failed to load the visitor log', 'Close', { duration: 4000 });
      }
    });
  }

  label(status: string): string {
    switch (status) {
      case 'PENDING': return 'Pending';
      case 'APPROVED': return 'Approved';
      case 'REJECTED': return 'Rejected';
      case 'TIMED_OUT': return 'Timed Out';
      default: return status;
    }
  }
}
