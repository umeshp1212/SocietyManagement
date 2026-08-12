import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatChipsModule } from '@angular/material/chips';
import { OwnerService } from '@core/services/owner.service';
import { Owner, Unit, OwnershipHistory } from '@core/models/owner.model';

@Component({
  selector: 'app-owner-detail',
  standalone: true,
  imports: [CommonModule, RouterModule, MatCardModule, MatButtonModule, MatIconModule, MatTableModule, MatChipsModule],
  template: `
    <div class="container" *ngIf="owner">
      <div class="page-header">
        <h2>{{ owner.fullName }}</h2>
        <div>
          <a mat-raised-button [routerLink]="['/owners/edit', owner.ownerId]">
            <mat-icon>edit</mat-icon> Edit
          </a>
          <a mat-button routerLink="/owners">Back to List</a>
        </div>
      </div>

      <mat-card>
        <mat-card-header>
          <mat-card-title>Owner Details</mat-card-title>
          <span class="status-badge" [ngClass]="owner.status.toLowerCase()">{{ owner.status }}</span>
        </mat-card-header>
        <mat-card-content>
          <div class="detail-grid">
            <div><strong>Contact:</strong> {{ owner.contactNumber }}</div>
            <div><strong>Email:</strong> {{ owner.email || '-' }}</div>
            <div><strong>Alternate:</strong> {{ owner.alternateNumber || '-' }}</div>
            <div><strong>PAN:</strong> {{ owner.panNumber || '-' }}</div>
            <div><strong>Occupation:</strong> {{ owner.occupation || '-' }}</div>
            <div><strong>Emergency:</strong> {{ owner.emergencyContactName || '-' }} ({{ owner.emergencyContactPhone || '-' }})</div>
            <div class="full-width"><strong>Address:</strong> {{ owner.permanentAddress || '-' }}</div>
          </div>
        </mat-card-content>
      </mat-card>

      <mat-card *ngIf="units.length > 0" style="margin-top: 16px">
        <mat-card-header><mat-card-title>Owned Units</mat-card-title></mat-card-header>
        <mat-card-content>
          <table mat-table [dataSource]="units">
            <ng-container matColumnDef="unitNumber">
              <th mat-header-cell *matHeaderCellDef>Unit</th>
              <td mat-cell *matCellDef="let u">{{ u.unitNumber }}</td>
            </ng-container>
            <ng-container matColumnDef="unitType">
              <th mat-header-cell *matHeaderCellDef>Type</th>
              <td mat-cell *matCellDef="let u">{{ u.unitType }}</td>
            </ng-container>
            <ng-container matColumnDef="occupancyStatus">
              <th mat-header-cell *matHeaderCellDef>Occupancy</th>
              <td mat-cell *matCellDef="let u">
                <span class="status-badge" [ngClass]="u.occupancyStatus.toLowerCase().replace('_', '')">
                  {{ u.occupancyStatus.replace('_', ' ') }}
                </span>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="['unitNumber','unitType','occupancyStatus']"></tr>
            <tr mat-row *matRowDef="let row; columns: ['unitNumber','unitType','occupancyStatus']"></tr>
          </table>
        </mat-card-content>
      </mat-card>

      <mat-card *ngIf="history.length > 0" style="margin-top: 16px">
        <mat-card-header><mat-card-title>Ownership History</mat-card-title></mat-card-header>
        <mat-card-content>
          <table mat-table [dataSource]="history">
            <ng-container matColumnDef="unitNumber">
              <th mat-header-cell *matHeaderCellDef>Unit</th>
              <td mat-cell *matCellDef="let h">{{ h.unitNumber }}</td>
            </ng-container>
            <ng-container matColumnDef="ownershipStartDate">
              <th mat-header-cell *matHeaderCellDef>From</th>
              <td mat-cell *matCellDef="let h">{{ h.ownershipStartDate }}</td>
            </ng-container>
            <ng-container matColumnDef="ownershipEndDate">
              <th mat-header-cell *matHeaderCellDef>To</th>
              <td mat-cell *matCellDef="let h">{{ h.ownershipEndDate || 'Current' }}</td>
            </ng-container>
            <ng-container matColumnDef="transferType">
              <th mat-header-cell *matHeaderCellDef>Transfer Type</th>
              <td mat-cell *matCellDef="let h">{{ h.transferType }}</td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="['unitNumber','ownershipStartDate','ownershipEndDate','transferType']"></tr>
            <tr mat-row *matRowDef="let row; columns: ['unitNumber','ownershipStartDate','ownershipEndDate','transferType']"></tr>
          </table>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; padding: 16px 0; }
    .full-width { grid-column: span 2; }
    mat-card-header { display: flex; justify-content: space-between; align-items: center; }
  `]
})
export class OwnerDetailComponent implements OnInit {
  owner?: Owner;
  units: Unit[] = [];
  history: OwnershipHistory[] = [];

  constructor(private route: ActivatedRoute, private ownerService: OwnerService) {}

  ngOnInit(): void {
    const id = +this.route.snapshot.paramMap.get('id')!;
    this.ownerService.getOwnerById(id).subscribe(res => {
      if (res.success) this.owner = res.data;
    });
    this.ownerService.getUnitsByOwner(id).subscribe(res => {
      if (res.success) this.units = res.data;
    });
    this.ownerService.getHistoryByOwner(id).subscribe(res => {
      if (res.success) this.history = res.data;
    });
  }
}
