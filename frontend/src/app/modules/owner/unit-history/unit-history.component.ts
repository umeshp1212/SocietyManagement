import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatChipsModule } from '@angular/material/chips';
import { OwnerService } from '@core/services/owner.service';
import { TenantService } from '@core/services/tenant.service';
import { Unit, OwnershipHistory } from '@core/models/owner.model';
import { Tenant } from '@core/models/tenant.model';

@Component({
  selector: 'app-unit-history',
  standalone: true,
  imports: [
    CommonModule, RouterModule, MatCardModule, MatButtonModule,
    MatIconModule, MatTableModule, MatTabsModule, MatChipsModule
  ],
  template: `
    <div class="container" *ngIf="unit">
      <div class="page-header">
        <h2>History - Unit {{ unit.unitNumber }}</h2>
        <a mat-button routerLink="/units">Back to Units</a>
      </div>

      <!-- Unit Summary -->
      <mat-card class="unit-summary">
        <mat-card-content>
          <div class="summary-row">
            <span><strong>Unit:</strong> {{ unit.unitNumber }}</span>
            <span><strong>Type:</strong> {{ unit.unitType }}</span>
            <span><strong>Wing:</strong> {{ unit.wing || '-' }}</span>
            <span><strong>Current Owner(s):</strong> {{ unit.allOwnerNames || 'None' }}</span>
            <span class="status-badge" [ngClass]="unit.occupancyStatus.toLowerCase().replace('_','')">
              {{ unit.occupancyStatus.replace('_', ' ') }}
            </span>
          </div>
        </mat-card-content>
      </mat-card>

      <!-- Tabs: Ownership History & Tenant History -->
      <mat-tab-group style="margin-top: 16px;">

        <!-- Ownership History Tab -->
        <mat-tab label="Ownership History ({{ ownerHistory.length }})">
          <div class="tab-content">
            <div *ngIf="ownerHistory.length === 0" class="empty-state">
              <mat-icon>history</mat-icon>
              <p>No ownership transfer history for this unit</p>
            </div>

            <table mat-table [dataSource]="ownerHistory" *ngIf="ownerHistory.length > 0"
                   class="mat-elevation-z1">
              <ng-container matColumnDef="ownerName">
                <th mat-header-cell *matHeaderCellDef>Owner</th>
                <td mat-cell *matCellDef="let h">{{ h.ownerName }}</td>
              </ng-container>
              <ng-container matColumnDef="ownershipStartDate">
                <th mat-header-cell *matHeaderCellDef>From</th>
                <td mat-cell *matCellDef="let h">{{ h.ownershipStartDate }}</td>
              </ng-container>
              <ng-container matColumnDef="ownershipEndDate">
                <th mat-header-cell *matHeaderCellDef>To</th>
                <td mat-cell *matCellDef="let h">
                  <span *ngIf="h.ownershipEndDate">{{ h.ownershipEndDate }}</span>
                  <span *ngIf="!h.ownershipEndDate" class="status-badge active">Current</span>
                </td>
              </ng-container>
              <ng-container matColumnDef="transferType">
                <th mat-header-cell *matHeaderCellDef>Transfer Type</th>
                <td mat-cell *matCellDef="let h">{{ h.transferType }}</td>
              </ng-container>
              <ng-container matColumnDef="remarks">
                <th mat-header-cell *matHeaderCellDef>Remarks</th>
                <td mat-cell *matCellDef="let h">{{ h.remarks || '-' }}</td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="ownerColumns"></tr>
              <tr mat-row *matRowDef="let row; columns: ownerColumns;"></tr>
            </table>
          </div>
        </mat-tab>

        <!-- Tenant History Tab -->
        <mat-tab label="Tenant History ({{ tenantHistory.length }})">
          <div class="tab-content">
            <div *ngIf="tenantHistory.length === 0" class="empty-state">
              <mat-icon>person_off</mat-icon>
              <p>No tenant history for this unit</p>
            </div>

            <table mat-table [dataSource]="tenantHistory" *ngIf="tenantHistory.length > 0"
                   class="mat-elevation-z1">
              <ng-container matColumnDef="tenantName">
                <th mat-header-cell *matHeaderCellDef>Tenant Name</th>
                <td mat-cell *matCellDef="let t">
                  <a [routerLink]="['/tenants', t.tenantId]">{{ t.tenantName }}</a>
                </td>
              </ng-container>
              <ng-container matColumnDef="contactNumber">
                <th mat-header-cell *matHeaderCellDef>Contact</th>
                <td mat-cell *matCellDef="let t">{{ t.contactNumber }}</td>
              </ng-container>
              <ng-container matColumnDef="rentStartDate">
                <th mat-header-cell *matHeaderCellDef>Move In</th>
                <td mat-cell *matCellDef="let t">{{ t.rentStartDate }}</td>
              </ng-container>
              <ng-container matColumnDef="rentEndDate">
                <th mat-header-cell *matHeaderCellDef>Agreement End</th>
                <td mat-cell *matCellDef="let t">{{ t.rentEndDate || '-' }}</td>
              </ng-container>
              <ng-container matColumnDef="moveOutDate">
                <th mat-header-cell *matHeaderCellDef>Move Out</th>
                <td mat-cell *matCellDef="let t">{{ t.moveOutDate || '-' }}</td>
              </ng-container>
              <ng-container matColumnDef="status">
                <th mat-header-cell *matHeaderCellDef>Status</th>
                <td mat-cell *matCellDef="let t">
                  <span class="status-badge" [ngClass]="t.status.toLowerCase().replace('_','')">
                    {{ t.status.replace('_', ' ') }}
                  </span>
                </td>
              </ng-container>
              <ng-container matColumnDef="nocStatus">
                <th mat-header-cell *matHeaderCellDef>NOC</th>
                <td mat-cell *matCellDef="let t">
                  <span class="status-badge" [ngClass]="t.nocStatus.toLowerCase()">
                    {{ t.nocStatus }}
                  </span>
                </td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="tenantColumns"></tr>
              <tr mat-row *matRowDef="let row; columns: tenantColumns;"></tr>
            </table>
          </div>
        </mat-tab>

      </mat-tab-group>
    </div>
  `,
  styles: [`
    .unit-summary .summary-row { display: flex; gap: 24px; flex-wrap: wrap; align-items: center; }
    .tab-content { padding: 16px 0; }
    .empty-state { text-align: center; padding: 40px; color: #999; }
    .empty-state mat-icon { font-size: 48px; height: 48px; width: 48px; }
    table { width: 100%; }
    a { color: #1976d2; text-decoration: none; font-weight: 500; }
    a:hover { text-decoration: underline; }
  `]
})
export class UnitHistoryComponent implements OnInit {
  unitId!: number;
  unit?: Unit;
  ownerHistory: OwnershipHistory[] = [];
  tenantHistory: Tenant[] = [];

  ownerColumns = ['ownerName', 'ownershipStartDate', 'ownershipEndDate', 'transferType', 'remarks'];
  tenantColumns = ['tenantName', 'contactNumber', 'rentStartDate', 'rentEndDate', 'moveOutDate', 'status', 'nocStatus'];

  constructor(
    private route: ActivatedRoute,
    private ownerService: OwnerService,
    private tenantService: TenantService
  ) {}

  ngOnInit(): void {
    this.unitId = +this.route.snapshot.paramMap.get('id')!;
    this.loadUnit();
    this.loadOwnerHistory();
    this.loadTenantHistory();
  }

  loadUnit(): void {
    this.ownerService.getUnitById(this.unitId).subscribe(res => {
      if (res.success) this.unit = res.data;
    });
  }

  loadOwnerHistory(): void {
    this.ownerService.getHistoryByUnit(this.unitId).subscribe(res => {
      if (res.success) this.ownerHistory = res.data;
    });
  }

  loadTenantHistory(): void {
    this.tenantService.getTenantHistoryByUnit(this.unitId).subscribe(res => {
      if (res.success) this.tenantHistory = res.data;
    });
  }
}
