import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { TenantService } from '@core/services/tenant.service';
import { Tenant } from '@core/models/tenant.model';

@Component({
  selector: 'app-tenant-detail',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterModule, MatCardModule, MatButtonModule,
    MatIconModule, MatTableModule, MatChipsModule, MatDividerModule, MatSnackBarModule
  ],
  template: `
    <div class="container" *ngIf="tenant">
      <div class="page-header">
        <h2>{{ tenant.tenantName }}</h2>
        <div class="header-actions">
          <a mat-raised-button color="primary"
             [routerLink]="['/tenants/edit', tenant.tenantId]"
             *ngIf="tenant.status !== 'VACATED'">
            <mat-icon>edit</mat-icon> Edit
          </a>
          <a mat-button routerLink="/tenants">Back to List</a>
        </div>
      </div>

      <!-- Status Banner -->
      <div class="status-banner" [ngClass]="tenant.status.toLowerCase().replace('_','')">
        <mat-icon>{{ getStatusIcon() }}</mat-icon>
        <span>{{ tenant.status.replace('_', ' ') }}</span>
        <span *ngIf="tenant.isAgreementExpired" class="expired-tag">Agreement Expired</span>
      </div>

      <!-- Tenant Info -->
      <mat-card>
        <mat-card-header>
          <mat-card-title>Tenant Information</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div class="detail-grid">
            <div><strong>Unit:</strong> {{ tenant.unitNumber }}</div>
            <div><strong>Owner:</strong> {{ tenant.ownerName || '-' }}</div>
            <div><strong>Contact:</strong> {{ tenant.contactNumber }}</div>
            <div><strong>Email:</strong> {{ tenant.email || '-' }}</div>
            <div><strong>Aadhar:</strong> {{ tenant.aadharNumber || '-' }}</div>
            <div><strong>PAN:</strong> {{ tenant.panNumber || '-' }}</div>
            <div class="full-width"><strong>Permanent Address:</strong> {{ tenant.permanentAddress || '-' }}</div>
          </div>
        </mat-card-content>
      </mat-card>

      <!-- Rental Details -->
      <mat-card style="margin-top: 16px">
        <mat-card-header>
          <mat-card-title>Rental Details</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div class="detail-grid">
            <div><strong>Rent Start:</strong> {{ tenant.rentStartDate }}</div>
            <div><strong>Rent End:</strong> {{ tenant.rentEndDate || 'Not specified' }}</div>
            <div><strong>Monthly Rent:</strong> {{ tenant.monthlyRentAmount ? '₹ ' + tenant.monthlyRentAmount : '-' }}</div>
            <div><strong>Security Deposit:</strong> {{ tenant.securityDeposit ? '₹ ' + tenant.securityDeposit : '-' }}</div>
            <div *ngIf="tenant.daysUntilAgreementExpiry != null">
              <strong>Agreement Expiry:</strong>
              <span [ngClass]="tenant.daysUntilAgreementExpiry < 30 ? 'text-warn' : ''">
                {{ tenant.daysUntilAgreementExpiry }} days
              </span>
            </div>
          </div>
        </mat-card-content>
      </mat-card>

      <!-- NOC & Police Verification -->
      <mat-card style="margin-top: 16px">
        <mat-card-header>
          <mat-card-title>Compliance Status</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div class="detail-grid">
            <div>
              <strong>NOC Status:</strong>
              <span class="status-badge" [ngClass]="tenant.nocStatus.toLowerCase()">
                {{ tenant.nocStatus }}
              </span>
            </div>
            <div>
              <strong>Police Verification:</strong>
              <span class="status-badge" [ngClass]="getPoliceStatusClass()">
                {{ tenant.policeVerificationStatus.replace('_', ' ') }}
              </span>
            </div>
            <div *ngIf="tenant.nocApprovedBy">
              <strong>NOC Approved By:</strong> {{ tenant.nocApprovedBy }}
            </div>
            <div *ngIf="tenant.nocApprovedOn">
              <strong>NOC Approved On:</strong> {{ tenant.nocApprovedOn | date:'dd-MM-yyyy' }}
            </div>
          </div>

          <!-- Action Buttons for Compliance -->
          <div class="compliance-actions" *ngIf="tenant.status === 'ACTIVE'">
            <mat-divider style="margin: 12px 0"></mat-divider>
            <button mat-raised-button color="primary"
                    *ngIf="tenant.nocStatus === 'PENDING'"
                    (click)="approveNoc()">
              <mat-icon>verified</mat-icon> Approve NOC
            </button>
            <button mat-stroked-button color="warn"
                    *ngIf="tenant.nocStatus === 'PENDING'"
                    (click)="rejectNoc()">
              <mat-icon>cancel</mat-icon> Reject NOC
            </button>
            <button mat-raised-button
                    *ngIf="tenant.policeVerificationStatus === 'NOT_INITIATED'"
                    (click)="updatePoliceStatus('SUBMITTED')">
              <mat-icon>send</mat-icon> Mark Submitted
            </button>
            <button mat-raised-button color="primary"
                    *ngIf="tenant.policeVerificationStatus === 'SUBMITTED'"
                    (click)="updatePoliceStatus('VERIFIED')">
              <mat-icon>verified_user</mat-icon> Mark Verified
            </button>
          </div>
        </mat-card-content>
      </mat-card>

      <!-- Family Members -->
      <mat-card style="margin-top: 16px" *ngIf="tenant.familyMembers && tenant.familyMembers.length > 0">
        <mat-card-header>
          <mat-card-title>Family Members ({{ tenant.familyMembers.length }})</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <table mat-table [dataSource]="tenant.familyMembers" class="mat-elevation-z1">
            <ng-container matColumnDef="memberName">
              <th mat-header-cell *matHeaderCellDef>Name</th>
              <td mat-cell *matCellDef="let m">{{ m.memberName }}</td>
            </ng-container>
            <ng-container matColumnDef="age">
              <th mat-header-cell *matHeaderCellDef>Age</th>
              <td mat-cell *matCellDef="let m">{{ m.age || '-' }}</td>
            </ng-container>
            <ng-container matColumnDef="relation">
              <th mat-header-cell *matHeaderCellDef>Relation</th>
              <td mat-cell *matCellDef="let m">{{ m.relation }}</td>
            </ng-container>
            <ng-container matColumnDef="contactNumber">
              <th mat-header-cell *matHeaderCellDef>Contact</th>
              <td mat-cell *matCellDef="let m">{{ m.contactNumber || '-' }}</td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="['memberName','age','relation','contactNumber']"></tr>
            <tr mat-row *matRowDef="let row; columns: ['memberName','age','relation','contactNumber']"></tr>
          </table>
        </mat-card-content>
      </mat-card>

      <!-- Vehicles -->
      <mat-card style="margin-top: 16px" *ngIf="tenant.vehicles && tenant.vehicles.length > 0">
        <mat-card-header>
          <mat-card-title>Vehicles ({{ tenant.vehicles.length }})</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <table mat-table [dataSource]="tenant.vehicles" class="mat-elevation-z1">
            <ng-container matColumnDef="vehicleType">
              <th mat-header-cell *matHeaderCellDef>Type</th>
              <td mat-cell *matCellDef="let v">{{ v.vehicleType }}</td>
            </ng-container>
            <ng-container matColumnDef="vehicleNumber">
              <th mat-header-cell *matHeaderCellDef>Number</th>
              <td mat-cell *matCellDef="let v">{{ v.vehicleNumber }}</td>
            </ng-container>
            <ng-container matColumnDef="parkingSlot">
              <th mat-header-cell *matHeaderCellDef>Parking Slot</th>
              <td mat-cell *matCellDef="let v">{{ v.parkingSlot || '-' }}</td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="['vehicleType','vehicleNumber','parkingSlot']"></tr>
            <tr mat-row *matRowDef="let row; columns: ['vehicleType','vehicleNumber','parkingSlot']"></tr>
          </table>
        </mat-card-content>
      </mat-card>

      <!-- Move-out Section -->
      <mat-card style="margin-top: 16px" *ngIf="tenant.status === 'VACATED'">
        <mat-card-header>
          <mat-card-title>Move-out Details</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div class="detail-grid">
            <div><strong>Move-out Date:</strong> {{ tenant.moveOutDate }}</div>
            <div><strong>Reason:</strong> {{ tenant.moveOutReason || '-' }}</div>
          </div>
        </mat-card-content>
      </mat-card>

      <!-- Actions Card -->
      <mat-card style="margin-top: 16px" *ngIf="tenant.status !== 'VACATED'">
        <mat-card-header>
          <mat-card-title>Actions</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div class="action-buttons" style="justify-content: flex-start;">
            <button mat-raised-button color="accent"
                    *ngIf="tenant.status === 'ACTIVE'"
                    (click)="markNoticePeriod()">
              <mat-icon>notifications</mat-icon> Mark Notice Period
            </button>
            <button mat-raised-button color="warn"
                    (click)="showMoveOut = true">
              <mat-icon>exit_to_app</mat-icon> Move Out Tenant
            </button>
          </div>

          <!-- Move-out Form (inline) -->
          <div *ngIf="showMoveOut" class="moveout-form">
            <mat-divider style="margin: 16px 0"></mat-divider>
            <h4>Move-out Details</h4>
            <div class="form-row">
              <input type="date" [(ngModel)]="moveOutDate" class="date-input">
              <input type="text" [(ngModel)]="moveOutReason" placeholder="Reason (optional)"
                     class="reason-input">
            </div>
            <div class="action-buttons" style="margin-top: 12px;">
              <button mat-button (click)="showMoveOut = false">Cancel</button>
              <button mat-raised-button color="warn" (click)="moveOut()"
                      [disabled]="!moveOutDate">
                Confirm Move Out
              </button>
            </div>
          </div>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .header-actions { display: flex; gap: 8px; }
    .detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; padding: 16px 0; }
    .full-width { grid-column: span 2; }
    .status-banner { display: flex; align-items: center; gap: 8px; padding: 12px 16px;
                     border-radius: 8px; margin-bottom: 16px; font-weight: 500; }
    .status-banner.active { background: #e8f5e9; color: #2e7d32; }
    .status-banner.noticeperiod { background: #fff3e0; color: #e65100; }
    .status-banner.vacated { background: #eceff1; color: #546e7a; }
    .expired-tag { background: #ffebee; color: #c62828; padding: 2px 8px; border-radius: 4px;
                   font-size: 12px; margin-left: auto; }
    .text-warn { color: #e65100; font-weight: 500; }
    .compliance-actions { display: flex; gap: 8px; flex-wrap: wrap; }
    .moveout-form { background: #fafafa; padding: 16px; border-radius: 8px; }
    .moveout-form h4 { margin: 0 0 12px; color: #c62828; }
    .form-row { display: flex; gap: 12px; }
    .date-input, .reason-input { padding: 10px; border: 1px solid #ccc; border-radius: 4px;
                                  font-size: 14px; font-family: inherit; }
    .date-input { width: 180px; }
    .reason-input { flex: 1; }
    mat-card-header { display: flex; justify-content: space-between; align-items: center; }
  `]
})
export class TenantDetailComponent implements OnInit {
  tenant?: Tenant;
  showMoveOut = false;
  moveOutDate = '';
  moveOutReason = '';

  constructor(
    private route: ActivatedRoute,
    private tenantService: TenantService,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    const id = +this.route.snapshot.paramMap.get('id')!;
    this.loadTenant(id);
  }

  loadTenant(id: number): void {
    this.tenantService.getTenantById(id).subscribe(res => {
      if (res.success) this.tenant = res.data;
    });
  }

  getStatusIcon(): string {
    switch (this.tenant?.status) {
      case 'ACTIVE': return 'check_circle';
      case 'NOTICE_PERIOD': return 'notifications_active';
      case 'VACATED': return 'logout';
      default: return 'info';
    }
  }

  getPoliceStatusClass(): string {
    switch (this.tenant?.policeVerificationStatus) {
      case 'VERIFIED': return 'approved';
      case 'SUBMITTED': return 'pending';
      case 'REJECTED': return 'rejected';
      case 'EXPIRED': return 'expired';
      default: return 'draft';
    }
  }

  approveNoc(): void {
    if (!this.tenant) return;
    this.tenantService.updateNocStatus(this.tenant.tenantId, 'APPROVED').subscribe(res => {
      if (res.success) {
        this.snackBar.open('NOC Approved', 'Close', { duration: 3000 });
        this.tenant = res.data;
      }
    });
  }

  rejectNoc(): void {
    if (!this.tenant) return;
    this.tenantService.updateNocStatus(this.tenant.tenantId, 'REJECTED').subscribe(res => {
      if (res.success) {
        this.snackBar.open('NOC Rejected', 'Close', { duration: 3000 });
        this.tenant = res.data;
      }
    });
  }

  updatePoliceStatus(status: string): void {
    if (!this.tenant) return;
    this.tenantService.updatePoliceVerification(this.tenant.tenantId, status).subscribe(res => {
      if (res.success) {
        this.snackBar.open('Police verification status updated', 'Close', { duration: 3000 });
        this.tenant = res.data;
      }
    });
  }

  markNoticePeriod(): void {
    if (!this.tenant) return;
    this.tenantService.markNoticePeriod(this.tenant.tenantId).subscribe(res => {
      if (res.success) {
        this.snackBar.open('Tenant marked for notice period', 'Close', { duration: 3000 });
        this.tenant = res.data;
      }
    });
  }

  moveOut(): void {
    if (!this.tenant || !this.moveOutDate) return;
    this.tenantService.moveOutTenant(this.tenant.tenantId, {
      moveOutDate: this.moveOutDate,
      moveOutReason: this.moveOutReason || undefined
    }).subscribe(res => {
      if (res.success) {
        this.snackBar.open('Tenant moved out successfully', 'Close', { duration: 3000 });
        this.tenant = res.data;
        this.showMoveOut = false;
      }
    });
  }
}
