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
import { TenantService } from '@core/services/tenant.service';
import { Tenant } from '@core/models/tenant.model';

@Component({
  selector: 'app-tenant-list',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, MatTableModule, MatPaginatorModule,
    MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  template: `
    <div class="container">
      <div class="page-header">
        <h2>Tenant Management</h2>
        <div style="display: flex; gap: 8px;">
          <a mat-raised-button color="accent" routerLink="/tenants/bulk-upload">
            <mat-icon>upload_file</mat-icon> Bulk Upload
          </a>
          <a mat-raised-button color="primary" routerLink="/tenants/register">
            <mat-icon>add</mat-icon> Register Tenant
          </a>
        </div>
      </div>
      <div class="search-bar">
        <mat-form-field appearance="outline">
          <mat-label>Search</mat-label>
          <input matInput [(ngModel)]="searchTerm" (keyup.enter)="loadTenants()" placeholder="Name, Phone">
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Status</mat-label>
          <mat-select [(ngModel)]="statusFilter" (selectionChange)="loadTenants()">
            <mat-option value="">All</mat-option>
            <mat-option value="ACTIVE">Active</mat-option>
            <mat-option value="NOTICE_PERIOD">Notice Period</mat-option>
            <mat-option value="VACATED">Vacated</mat-option>
          </mat-select>
        </mat-form-field>
      </div>
      <table mat-table [dataSource]="tenants" class="mat-elevation-z2">
        <ng-container matColumnDef="tenantName"><th mat-header-cell *matHeaderCellDef>Tenant</th><td mat-cell *matCellDef="let t">{{ t.tenantName }}</td></ng-container>
        <ng-container matColumnDef="unitNumber"><th mat-header-cell *matHeaderCellDef>Unit</th><td mat-cell *matCellDef="let t">{{ t.unitNumber }}</td></ng-container>
        <ng-container matColumnDef="contactNumber"><th mat-header-cell *matHeaderCellDef>Contact</th><td mat-cell *matCellDef="let t">{{ t.contactNumber }}</td></ng-container>
        <ng-container matColumnDef="nocStatus"><th mat-header-cell *matHeaderCellDef>NOC</th><td mat-cell *matCellDef="let t"><span class="status-badge" [ngClass]="t.nocStatus.toLowerCase()">{{ t.nocStatus }}</span></td></ng-container>
        <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Status</th><td mat-cell *matCellDef="let t"><span class="status-badge" [ngClass]="t.status.toLowerCase().replace('_','')">{{ t.status.replace('_', ' ') }}</span></td></ng-container>
        <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef>Actions</th><td mat-cell *matCellDef="let t">
          <a mat-icon-button [routerLink]="['/tenants', t.tenantId]"><mat-icon>visibility</mat-icon></a>
          <a mat-icon-button [routerLink]="['/tenants/edit', t.tenantId]"><mat-icon>edit</mat-icon></a>
        </td></ng-container>
        <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
      </table>
      <mat-paginator [length]="totalElements" [pageSize]="pageSize" [pageSizeOptions]="[10,20,50]" (page)="onPageChange($event)"></mat-paginator>
    </div>
  `
})
export class TenantListComponent implements OnInit {
  tenants: Tenant[] = [];
  displayedColumns = ['tenantName', 'unitNumber', 'contactNumber', 'nocStatus', 'status', 'actions'];
  totalElements = 0; pageSize = 20; currentPage = 0;
  searchTerm = ''; statusFilter = '';

  constructor(private tenantService: TenantService) {}
  ngOnInit(): void { this.loadTenants(); }

  loadTenants(): void {
    this.tenantService.getAllTenants(this.currentPage, this.pageSize, this.statusFilter || undefined, undefined, this.searchTerm || undefined)
      .subscribe(res => { if (res.success) { this.tenants = res.data.content; this.totalElements = res.data.totalElements; }});
  }

  onPageChange(event: PageEvent): void { this.currentPage = event.pageIndex; this.pageSize = event.pageSize; this.loadTenants(); }
}
