import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { OwnerService } from '@core/services/owner.service';
import { AuthService } from '@core/services/auth.service';
import { Unit } from '@core/models/owner.model';

@Component({
  selector: 'app-unit-list',
  standalone: true,
  imports: [
    CommonModule, RouterModule, FormsModule, MatTableModule, MatPaginatorModule,
    MatButtonModule, MatIconModule, MatFormFieldModule, MatSelectModule, MatTooltipModule
  ],
  template: `
    <div class="container">
      <div class="page-header">
        <h2>Unit Management</h2>
        <a mat-raised-button color="primary" routerLink="/units/add"
           *ngIf="hasPermission('UNIT_CREATE')">
          <mat-icon>add</mat-icon> Add Unit
        </a>
      </div>

      <p class="page-subtitle">
        Click the <mat-icon style="vertical-align: middle; font-size: 18px;">group</mat-icon>
        icon in the Actions column to add or remove co-owners for a unit.
      </p>

      <div class="search-bar">
        <mat-form-field appearance="outline">
          <mat-label>Unit Type</mat-label>
          <mat-select [(ngModel)]="typeFilter" (selectionChange)="loadUnits(true)">
            <mat-option value="">All</mat-option>
            <mat-option value="FLAT">Flat</mat-option>
            <mat-option value="SHOP">Shop</mat-option>
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Occupancy</mat-label>
          <mat-select [(ngModel)]="occupancyFilter" (selectionChange)="loadUnits(true)">
            <mat-option value="">All</mat-option>
            <mat-option value="SELF_OCCUPIED">Self Occupied</mat-option>
            <mat-option value="RENTED">Rented</mat-option>
            <mat-option value="VACANT">Vacant</mat-option>
          </mat-select>
        </mat-form-field>
      </div>

      <table mat-table [dataSource]="units" class="mat-elevation-z2">
        <ng-container matColumnDef="unitNumber">
          <th mat-header-cell *matHeaderCellDef>Unit No.</th>
          <td mat-cell *matCellDef="let u">{{ u.unitNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="wing">
          <th mat-header-cell *matHeaderCellDef>Wing</th>
          <td mat-cell *matCellDef="let u">{{ u.wing || '-' }}</td>
        </ng-container>
        <ng-container matColumnDef="unitType">
          <th mat-header-cell *matHeaderCellDef>Type</th>
          <td mat-cell *matCellDef="let u">{{ u.unitType }}</td>
        </ng-container>
        <ng-container matColumnDef="owners">
          <th mat-header-cell *matHeaderCellDef>Owner(s)</th>
          <td mat-cell *matCellDef="let u">{{ u.allOwnerNames || 'Not assigned' }}</td>
        </ng-container>
        <ng-container matColumnDef="occupancyStatus">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let u">
            <span class="status-badge" [ngClass]="u.occupancyStatus.toLowerCase().replace('_','')">
              {{ u.occupancyStatus.replace('_', ' ') }}
            </span>
          </td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef>Actions</th>
          <td mat-cell *matCellDef="let u">
            <a mat-icon-button [routerLink]="['/units', u.unitId, 'history']" matTooltip="Ownership & Tenant History">
              <mat-icon>history</mat-icon>
            </a>
            <a mat-icon-button [routerLink]="['/units', u.unitId, 'owners']" matTooltip="Manage Owners"
               *ngIf="hasPermission('UNIT_MANAGE_OWNERS')">
              <mat-icon>group</mat-icon>
            </a>
            <a mat-icon-button [routerLink]="['/units/edit', u.unitId]" matTooltip="Edit Unit"
               *ngIf="hasPermission('UNIT_UPDATE')">
              <mat-icon>edit</mat-icon>
            </a>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
      </table>

      <mat-paginator [length]="totalElements" [pageSize]="pageSize"
        [pageSizeOptions]="[20, 50, 100]" (page)="onPageChange($event)">
      </mat-paginator>
    </div>
  `
})
export class UnitListComponent implements OnInit {
  units: Unit[] = [];
  displayedColumns = ['unitNumber', 'wing', 'unitType', 'owners', 'occupancyStatus', 'actions'];
  totalElements = 0;
  pageSize = 20;
  currentPage = 0;
  typeFilter = '';
  occupancyFilter = '';

  constructor(private ownerService: OwnerService, private authService: AuthService) {}

  ngOnInit(): void { this.loadUnits(); }

  loadUnits(resetPage = false): void {
    if (resetPage) this.currentPage = 0;
    this.ownerService.getAllUnits(this.currentPage, this.pageSize,
      undefined,
      this.typeFilter || undefined,
      this.occupancyFilter || undefined
    ).subscribe(res => {
      if (res.success) {
        this.units = res.data.content;
        this.totalElements = res.data.totalElements;
      }
    });
  }

  onPageChange(event: PageEvent): void {
    this.currentPage = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadUnits();
  }

  hasPermission(permission: string): boolean { return this.authService.hasPermission(permission); }
}
