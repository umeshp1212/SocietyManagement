import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { environment } from '@env/environment';

interface SuspenseEntry {
  suspenseId: number;
  amount: number;
  receivedDate: string;
  paymentMode: string;
  referenceNumber: string;
  description: string;
  status: string;
  assignedToUnitId: number | null;
  assignedToUnitNumber: string | null;
  assignedToOwnerName: string | null;
  assignedBy: string | null;
  assignedOn: string | null;
  assignmentRemarks: string | null;
  applyToOpeningBalance: boolean;
  createdOn: string;
  createdBy: string;
  auditTrail: any[];
}

interface UnitOption {
  unitId: number;
  unitNumber: string;
  ownerNames: string;
}

@Component({
  selector: 'app-suspense-account',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatCardModule, MatTableModule, MatPaginatorModule,
    MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatCheckboxModule, MatChipsModule, MatDividerModule,
    MatTooltipModule, MatSnackBarModule
  ],
  template: `
    <div class="container">
      <div class="page-header">
        <h2>Suspense Account</h2>
        <button mat-raised-button color="primary" (click)="showCreateForm = !showCreateForm">
          <mat-icon>{{ showCreateForm ? 'close' : 'add' }}</mat-icon>
          {{ showCreateForm ? 'Cancel' : 'Add Suspense Entry' }}
        </button>
      </div>

      <p class="subtitle">
        Unidentified payments are held here until the payer is identified and assigned to a unit.
      </p>

      <!-- Summary Cards -->
      <div class="summary-cards" *ngIf="summary">
        <mat-card class="summary-card unassigned">
          <mat-card-content>
            <div class="summary-label">Unassigned</div>
            <div class="summary-value">{{ summary.unassignedCount }}</div>
            <div class="summary-amount">{{ summary.unassignedAmount | currency:'INR' }}</div>
          </mat-card-content>
        </mat-card>
        <mat-card class="summary-card assigned">
          <mat-card-content>
            <div class="summary-label">Assigned</div>
            <div class="summary-value">{{ summary.assignedCount }}</div>
            <div class="summary-amount">{{ summary.assignedAmount | currency:'INR' }}</div>
          </mat-card-content>
        </mat-card>
        <mat-card class="summary-card reversed">
          <mat-card-content>
            <div class="summary-label">Reversed</div>
            <div class="summary-value">{{ summary.reversedCount }}</div>
          </mat-card-content>
        </mat-card>
        <mat-card class="summary-card total">
          <mat-card-content>
            <div class="summary-label">Total Entries</div>
            <div class="summary-value">{{ summary.totalEntries }}</div>
          </mat-card-content>
        </mat-card>
      </div>

      <!-- Create Form -->
      <mat-card *ngIf="showCreateForm" class="form-card">
        <mat-card-header><mat-card-title>Add Unidentified Payment</mat-card-title></mat-card-header>
        <mat-card-content>
          <div class="form-row">
            <mat-form-field appearance="outline">
              <mat-label>Amount (Rs)</mat-label>
              <input matInput type="number" [(ngModel)]="createForm.amount">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Received Date</mat-label>
              <input matInput type="date" [(ngModel)]="createForm.receivedDate">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Payment Mode</mat-label>
              <mat-select [(ngModel)]="createForm.paymentMode">
                <mat-option value="">-- Select --</mat-option>
                <mat-option value="CASH">Cash</mat-option>
                <mat-option value="CHEQUE">Cheque</mat-option>
                <mat-option value="BANK_TRANSFER">Bank Transfer</mat-option>
                <mat-option value="UPI">UPI</mat-option>
                <mat-option value="NEFT">NEFT</mat-option>
              </mat-select>
            </mat-form-field>
          </div>
          <div class="form-row">
            <mat-form-field appearance="outline">
              <mat-label>Reference / Cheque No</mat-label>
              <input matInput [(ngModel)]="createForm.referenceNumber" placeholder="NEFT ref, cheque no, UPI ID">
            </mat-form-field>
            <mat-form-field appearance="outline" style="flex:2">
              <mat-label>Description</mat-label>
              <input matInput [(ngModel)]="createForm.description" placeholder="Any details about this payment">
            </mat-form-field>
          </div>
          <div class="action-buttons">
            <button mat-button (click)="showCreateForm = false">Cancel</button>
            <button mat-raised-button color="primary" (click)="createEntry()"
                    [disabled]="!createForm.amount">
              <mat-icon>save</mat-icon> Save Entry
            </button>
          </div>
        </mat-card-content>
      </mat-card>

      <!-- Filters -->
      <div class="search-bar">
        <mat-form-field appearance="outline">
          <mat-label>Search</mat-label>
          <input matInput [(ngModel)]="searchTerm" (keyup.enter)="loadEntries()" placeholder="Ref no, description">
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Status</mat-label>
          <mat-select [(ngModel)]="statusFilter" (selectionChange)="loadEntries()">
            <mat-option value="">All</mat-option>
            <mat-option value="UNASSIGNED">Unassigned</mat-option>
            <mat-option value="ASSIGNED">Assigned</mat-option>
            <mat-option value="REVERSED">Reversed</mat-option>
          </mat-select>
        </mat-form-field>
      </div>

      <!-- Entries Table -->
      <mat-card>
        <mat-card-content>
          <table mat-table [dataSource]="entries" class="mat-elevation-z0">
            <ng-container matColumnDef="receivedDate">
              <th mat-header-cell *matHeaderCellDef>Date</th>
              <td mat-cell *matCellDef="let e">{{ e.receivedDate }}</td>
            </ng-container>
            <ng-container matColumnDef="amount">
              <th mat-header-cell *matHeaderCellDef>Amount</th>
              <td mat-cell *matCellDef="let e"><strong>{{ e.amount | currency:'INR' }}</strong></td>
            </ng-container>
            <ng-container matColumnDef="paymentMode">
              <th mat-header-cell *matHeaderCellDef>Mode</th>
              <td mat-cell *matCellDef="let e">{{ e.paymentMode || '-' }}</td>
            </ng-container>
            <ng-container matColumnDef="referenceNumber">
              <th mat-header-cell *matHeaderCellDef>Reference</th>
              <td mat-cell *matCellDef="let e">{{ e.referenceNumber || '-' }}</td>
            </ng-container>
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>Status</th>
              <td mat-cell *matCellDef="let e">
                <span class="status-badge" [ngClass]="e.status.toLowerCase()">{{ e.status }}</span>
              </td>
            </ng-container>
            <ng-container matColumnDef="assignedTo">
              <th mat-header-cell *matHeaderCellDef>Assigned To</th>
              <td mat-cell *matCellDef="let e">
                <span *ngIf="e.assignedToUnitNumber">{{ e.assignedToUnitNumber }} ({{ e.assignedToOwnerName }})</span>
                <span *ngIf="!e.assignedToUnitNumber" class="text-muted">-</span>
              </td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef>Actions</th>
              <td mat-cell *matCellDef="let e">
                <button mat-icon-button (click)="openAssignDialog(e)"
                        *ngIf="e.status === 'UNASSIGNED' || e.status === 'REVERSED'"
                        matTooltip="Assign to Unit" color="primary">
                  <mat-icon>person_add</mat-icon>
                </button>
                <button mat-icon-button (click)="openReverseDialog(e)"
                        *ngIf="e.status === 'ASSIGNED'"
                        matTooltip="Reverse Assignment" color="warn">
                  <mat-icon>undo</mat-icon>
                </button>
                <button mat-icon-button (click)="viewAuditTrail(e)"
                        matTooltip="View History">
                  <mat-icon>history</mat-icon>
                </button>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
          </table>

          <div *ngIf="entries.length === 0" class="no-data">
            <mat-icon>inbox</mat-icon>
            <p>No suspense entries found</p>
          </div>

          <mat-paginator [length]="totalElements" [pageSize]="pageSize"
                         [pageSizeOptions]="[10, 20, 50]" (page)="onPageChange($event)">
          </mat-paginator>
        </mat-card-content>
      </mat-card>

      <!-- Assign Dialog (inline) -->
      <mat-card *ngIf="showAssignDialog" class="dialog-card">
        <mat-card-header>
          <mat-card-title>Assign Suspense Entry</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <p>Amount: <strong>{{ selectedEntry?.amount | currency:'INR' }}</strong>
            | Ref: {{ selectedEntry?.referenceNumber || 'N/A' }}
            | Date: {{ selectedEntry?.receivedDate }}</p>

          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Assign to Unit</mat-label>
            <mat-select [(ngModel)]="assignForm.unitId">
              <mat-option *ngFor="let unit of units" [value]="unit.unitId">
                {{ unit.unitNumber }} - {{ unit.ownerNames || 'No owner' }}
              </mat-option>
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Remarks</mat-label>
            <input matInput [(ngModel)]="assignForm.remarks"
                   placeholder="e.g., Owner confirmed this is their NEFT payment">
          </mat-form-field>

          <mat-checkbox [(ngModel)]="assignForm.applyToOpeningBalance" color="primary">
            Apply to Opening Balance (reduces legacy arrears)
          </mat-checkbox>

          <div class="action-buttons">
            <button mat-button (click)="showAssignDialog = false">Cancel</button>
            <button mat-raised-button color="primary" (click)="assignEntry()"
                    [disabled]="!assignForm.unitId">
              <mat-icon>check</mat-icon> Confirm Assignment
            </button>
          </div>
        </mat-card-content>
      </mat-card>

      <!-- Reverse Dialog (inline) -->
      <mat-card *ngIf="showReverseDialog" class="dialog-card warn-card">
        <mat-card-header>
          <mat-card-title>Reverse Assignment</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <p>Amount: <strong>{{ selectedEntry?.amount | currency:'INR' }}</strong>
            | Currently assigned to: <strong>{{ selectedEntry?.assignedToUnitNumber }}</strong>
            ({{ selectedEntry?.assignedToOwnerName }})</p>

          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Reason for Reversal (mandatory)</mat-label>
            <textarea matInput [(ngModel)]="reverseForm.reason" rows="3"
                      placeholder="e.g., Owner denied this payment, need to reassign"></textarea>
          </mat-form-field>

          <div class="action-buttons">
            <button mat-button (click)="showReverseDialog = false">Cancel</button>
            <button mat-raised-button color="warn" (click)="reverseEntry()"
                    [disabled]="!reverseForm.reason.trim()">
              <mat-icon>undo</mat-icon> Confirm Reversal
            </button>
          </div>
        </mat-card-content>
      </mat-card>

      <!-- Audit Trail Dialog (inline) -->
      <mat-card *ngIf="showAuditTrail" class="dialog-card">
        <mat-card-header>
          <mat-card-title>Audit Trail - Entry #{{ selectedEntry?.suspenseId }}</mat-card-title>
          <button mat-icon-button (click)="showAuditTrail = false"><mat-icon>close</mat-icon></button>
        </mat-card-header>
        <mat-card-content>
          <div *ngIf="auditTrail.length === 0" class="text-muted">No audit trail available.</div>
          <div class="audit-list">
            <div *ngFor="let a of auditTrail" class="audit-item">
              <div class="audit-header">
                <span class="audit-action" [ngClass]="a.action.toLowerCase()">{{ a.action }}</span>
                <span class="audit-date">{{ a.performedOn | date:'dd-MM-yyyy HH:mm' }}</span>
              </div>
              <div class="audit-detail">
                <span *ngIf="a.unitNumber">Unit: {{ a.unitNumber }}</span>
                <span>By: {{ a.performedBy }}</span>
              </div>
              <div class="audit-reason" *ngIf="a.reason">{{ a.reason }}</div>
            </div>
          </div>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .subtitle { color: #666; margin-bottom: 16px; }
    .summary-cards { display: flex; gap: 12px; margin-bottom: 20px; flex-wrap: wrap; }
    .summary-card { flex: 1; min-width: 140px; text-align: center; }
    .summary-card.unassigned { border-left: 4px solid #f57c00; }
    .summary-card.assigned { border-left: 4px solid #2e7d32; }
    .summary-card.reversed { border-left: 4px solid #c62828; }
    .summary-card.total { border-left: 4px solid #1976d2; }
    .summary-label { font-size: 12px; color: #666; }
    .summary-value { font-size: 1.5rem; font-weight: 500; }
    .summary-amount { font-size: 12px; color: #1976d2; }
    .form-card { margin-bottom: 20px; }
    .form-row { display: flex; gap: 12px; }
    .form-row mat-form-field { flex: 1; }
    .full-width { width: 100%; }
    .dialog-card { margin-top: 16px; border: 2px solid #1976d2; }
    .warn-card { border-color: #c62828; }
    .text-muted { color: #999; }
    .no-data { text-align: center; padding: 32px; color: #999; }
    .no-data mat-icon { font-size: 48px; height: 48px; width: 48px; }
    .status-badge.unassigned { background: #fff3e0; color: #e65100; }
    .status-badge.assigned { background: #e8f5e9; color: #2e7d32; }
    .status-badge.reversed { background: #ffebee; color: #c62828; }
    .audit-list { display: flex; flex-direction: column; gap: 12px; }
    .audit-item { padding: 10px; background: #fafafa; border-radius: 6px; border-left: 3px solid #1976d2; }
    .audit-header { display: flex; justify-content: space-between; align-items: center; }
    .audit-action { font-weight: 600; font-size: 12px; padding: 2px 8px; border-radius: 4px; }
    .audit-action.created { background: #e3f2fd; color: #1565c0; }
    .audit-action.assigned { background: #e8f5e9; color: #2e7d32; }
    .audit-action.reversed { background: #ffebee; color: #c62828; }
    .audit-action.reassigned { background: #fff3e0; color: #e65100; }
    .audit-date { font-size: 12px; color: #999; }
    .audit-detail { font-size: 13px; color: #555; margin-top: 4px; display: flex; gap: 16px; }
    .audit-reason { font-size: 13px; color: #333; margin-top: 4px; font-style: italic; }

    @media (max-width: 768px) {
      .form-row { flex-direction: column; gap: 0; }
      .summary-cards { flex-direction: column; }
      .audit-header { flex-direction: column; align-items: flex-start; gap: 4px; }
    }
  `]
})
export class SuspenseAccountComponent implements OnInit {
  entries: SuspenseEntry[] = [];
  units: UnitOption[] = [];
  summary: any = null;
  auditTrail: any[] = [];

  displayedColumns = ['receivedDate', 'amount', 'paymentMode', 'referenceNumber', 'status', 'assignedTo', 'actions'];
  totalElements = 0;
  pageSize = 20;
  currentPage = 0;
  searchTerm = '';
  statusFilter = '';

  showCreateForm = false;
  showAssignDialog = false;
  showReverseDialog = false;
  showAuditTrail = false;
  selectedEntry: SuspenseEntry | null = null;

  createForm = { amount: null as number | null, receivedDate: '', paymentMode: '', referenceNumber: '', description: '' };
  assignForm = { unitId: null as number | null, remarks: '', applyToOpeningBalance: false };
  reverseForm = { reason: '' };

  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient, private snackBar: MatSnackBar) {}

  ngOnInit(): void {
    this.loadEntries();
    this.loadUnits();
    this.loadSummary();
  }

  loadEntries(): void {
    let params = new HttpParams().set('page', this.currentPage).set('size', this.pageSize);
    if (this.statusFilter) params = params.set('status', this.statusFilter);
    if (this.searchTerm) params = params.set('search', this.searchTerm);

    this.http.get<any>(`${this.apiUrl}/suspense`, { params }).subscribe(res => {
      if (res.success) {
        this.entries = res.data.content;
        this.totalElements = res.data.totalElements;
      }
    });
  }

  loadUnits(): void {
    this.http.get<any>(`${this.apiUrl}/units?page=0&size=500`).subscribe(res => {
      if (res.success) this.units = res.data.content;
    });
  }

  loadSummary(): void {
    this.http.get<any>(`${this.apiUrl}/suspense/summary`).subscribe(res => {
      if (res.success) this.summary = res.data;
    });
  }

  onPageChange(event: PageEvent): void {
    this.currentPage = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadEntries();
  }

  // ===== CREATE =====
  createEntry(): void {
    if (!this.createForm.amount) return;
    this.http.post<any>(`${this.apiUrl}/suspense`, this.createForm).subscribe({
      next: (res) => {
        if (res.success) {
          this.snackBar.open('Suspense entry created', 'Close', { duration: 3000 });
          this.showCreateForm = false;
          this.createForm = { amount: null, receivedDate: '', paymentMode: '', referenceNumber: '', description: '' };
          this.loadEntries();
          this.loadSummary();
        }
      },
      error: (err) => this.snackBar.open(err.error?.message || 'Failed', 'Close', { duration: 5000 })
    });
  }

  // ===== ASSIGN =====
  openAssignDialog(entry: SuspenseEntry): void {
    this.selectedEntry = entry;
    this.assignForm = { unitId: null, remarks: '', applyToOpeningBalance: false };
    this.showAssignDialog = true;
    this.showReverseDialog = false;
    this.showAuditTrail = false;
  }

  assignEntry(): void {
    if (!this.selectedEntry || !this.assignForm.unitId) return;
    this.http.patch<any>(`${this.apiUrl}/suspense/${this.selectedEntry.suspenseId}/assign`, this.assignForm)
      .subscribe({
        next: (res) => {
          if (res.success) {
            this.snackBar.open('Entry assigned successfully', 'Close', { duration: 3000 });
            this.showAssignDialog = false;
            this.loadEntries();
            this.loadSummary();
          }
        },
        error: (err) => this.snackBar.open(err.error?.message || 'Failed to assign', 'Close', { duration: 5000 })
      });
  }

  // ===== REVERSE =====
  openReverseDialog(entry: SuspenseEntry): void {
    this.selectedEntry = entry;
    this.reverseForm = { reason: '' };
    this.showReverseDialog = true;
    this.showAssignDialog = false;
    this.showAuditTrail = false;
  }

  reverseEntry(): void {
    if (!this.selectedEntry || !this.reverseForm.reason?.trim()) return;
    this.http.patch<any>(`${this.apiUrl}/suspense/${this.selectedEntry.suspenseId}/reverse`, this.reverseForm)
      .subscribe({
        next: (res) => {
          if (res.success) {
            this.snackBar.open('Assignment reversed', 'Close', { duration: 3000 });
            this.showReverseDialog = false;
            this.loadEntries();
            this.loadSummary();
          }
        },
        error: (err) => this.snackBar.open(err.error?.message || 'Failed to reverse', 'Close', { duration: 5000 })
      });
  }

  // ===== AUDIT TRAIL =====
  viewAuditTrail(entry: SuspenseEntry): void {
    this.selectedEntry = entry;
    this.showAuditTrail = true;
    this.showAssignDialog = false;
    this.showReverseDialog = false;

    this.http.get<any>(`${this.apiUrl}/suspense/${entry.suspenseId}`).subscribe(res => {
      if (res.success) {
        this.auditTrail = res.data.auditTrail || [];
      }
    });
  }
}
