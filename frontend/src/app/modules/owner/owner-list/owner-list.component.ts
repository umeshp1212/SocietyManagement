import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { OwnerService } from '@core/services/owner.service';
import { AuthService } from '@core/services/auth.service';
import { Owner } from '@core/models/owner.model';

@Component({
  selector: 'app-owner-list',
  standalone: true,
  imports: [
    CommonModule, RouterModule, FormsModule, MatTableModule, MatPaginatorModule,
    MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatChipsModule, MatTooltipModule
  ],
  template: `
    <div class="container">
      <div class="page-header">
        <h2>Owner Management</h2>
        <div style="display: flex; gap: 8px;">
          <a mat-raised-button routerLink="/units" matTooltip="Add/Remove co-owners for any unit"
             *ngIf="hasPermission('UNIT_MANAGE_OWNERS')">
            <mat-icon>group</mat-icon> Manage Unit Owners
          </a>
          <a mat-raised-button color="accent" routerLink="/owners/bulk-upload"
             *ngIf="hasPermission('OWNER_BULK_UPLOAD')">
            <mat-icon>upload_file</mat-icon> Bulk Upload
          </a>
          <a mat-raised-button color="primary" routerLink="/owners/add"
             *ngIf="hasPermission('OWNER_CREATE')">
            <mat-icon>add</mat-icon> Add Owner
          </a>
        </div>
      </div>

      <div class="search-bar">
        <mat-form-field appearance="outline">
          <mat-label>Search</mat-label>
          <input matInput [(ngModel)]="searchTerm" (keyup.enter)="loadOwners()"
                 placeholder="Name, Phone, Email">
          <mat-icon matSuffix>search</mat-icon>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Status</mat-label>
          <mat-select [(ngModel)]="statusFilter" (selectionChange)="loadOwners()">
            <mat-option value="">All</mat-option>
            <mat-option value="ACTIVE">Active</mat-option>
            <mat-option value="TRANSFERRED">Transferred</mat-option>
          </mat-select>
        </mat-form-field>
      </div>

      <table mat-table [dataSource]="owners" class="mat-elevation-z2">
        <ng-container matColumnDef="fullName">
          <th mat-header-cell *matHeaderCellDef>Name</th>
          <td mat-cell *matCellDef="let owner">{{ owner.fullName }}</td>
        </ng-container>

        <ng-container matColumnDef="contactNumber">
          <th mat-header-cell *matHeaderCellDef>Contact</th>
          <td mat-cell *matCellDef="let owner">{{ owner.contactNumber }}</td>
        </ng-container>

        <ng-container matColumnDef="unitNumbers">
          <th mat-header-cell *matHeaderCellDef>Unit No</th>
          <td mat-cell *matCellDef="let owner">{{ owner.unitNumbers || '-' }}</td>
        </ng-container>

        <ng-container matColumnDef="email">
          <th mat-header-cell *matHeaderCellDef>Email</th>
          <td mat-cell *matCellDef="let owner">{{ owner.email || '-' }}</td>
        </ng-container>

        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let owner">
            <span class="status-badge" [ngClass]="owner.status.toLowerCase()">
              {{ owner.status }}
            </span>
          </td>
        </ng-container>

        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef>Actions</th>
          <td mat-cell *matCellDef="let owner">
            <a mat-icon-button [routerLink]="['/owners', owner.ownerId]" matTooltip="View">
              <mat-icon>visibility</mat-icon>
            </a>
            <a mat-icon-button [routerLink]="['/owners/edit', owner.ownerId]" matTooltip="Edit"
               *ngIf="hasPermission('OWNER_UPDATE')">
              <mat-icon>edit</mat-icon>
            </a>
          </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
      </table>

      <mat-paginator
        [length]="totalElements"
        [pageSize]="pageSize"
        [pageSizeOptions]="[10, 20, 50]"
        (page)="onPageChange($event)">
      </mat-paginator>
    </div>
  `
})
export class OwnerListComponent implements OnInit {
  owners: Owner[] = [];
  displayedColumns = ['fullName', 'contactNumber', 'unitNumbers', 'email', 'status', 'actions'];
  totalElements = 0;
  pageSize = 20;
  currentPage = 0;
  searchTerm = '';
  statusFilter = '';

  constructor(private ownerService: OwnerService, private authService: AuthService) {}

  ngOnInit(): void {
    this.loadOwners();
  }

  loadOwners(): void {
    this.ownerService.getAllOwners(
      this.currentPage, this.pageSize,
      this.statusFilter || undefined,
      this.searchTerm || undefined
    ).subscribe(res => {
      if (res.success) {
        this.owners = res.data.content;
        this.totalElements = res.data.totalElements;
      }
    });
  }

  onPageChange(event: PageEvent): void {
    this.currentPage = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadOwners();
  }

  hasPermission(permission: string): boolean {
    return this.authService.hasPermission(permission);
  }
}
