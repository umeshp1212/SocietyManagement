import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { BreakpointObserver } from '@angular/cdk/layout';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { VendorService } from '@core/services/vendor.service';
import { Vendor } from '@core/models/vendor.model';

@Component({
  selector: 'app-vendor-list',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, MatTableModule, MatPaginatorModule,
    MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatTooltipModule],
  template: `
    <div class="container">
      <div class="page-header">
        <h2>Vendor Management</h2>
        <a mat-raised-button color="primary" routerLink="/vendors/add">
          <mat-icon>add</mat-icon> Add Vendor
        </a>
      </div>
      <div class="search-bar">
        <mat-form-field appearance="outline">
          <mat-label>Search</mat-label>
          <input matInput [(ngModel)]="searchTerm" (keyup.enter)="loadVendors()" placeholder="Name, Contact">
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Status</mat-label>
          <mat-select [(ngModel)]="statusFilter" (selectionChange)="loadVendors()">
            <mat-option value="">All</mat-option>
            <mat-option value="ACTIVE">Active</mat-option>
            <mat-option value="INACTIVE">Inactive</mat-option>
            <mat-option value="BLACKLISTED">Blacklisted</mat-option>
          </mat-select>
        </mat-form-field>
      </div>
      <table mat-table [dataSource]="vendors" class="mat-elevation-z2">
        <ng-container matColumnDef="vendorName"><th mat-header-cell *matHeaderCellDef>Name</th><td mat-cell *matCellDef="let v">{{ v.vendorName }}</td></ng-container>
        <ng-container matColumnDef="category"><th mat-header-cell *matHeaderCellDef>Category</th><td mat-cell *matCellDef="let v">{{ v.category }}</td></ng-container>
        <ng-container matColumnDef="phone"><th mat-header-cell *matHeaderCellDef>Phone</th><td mat-cell *matCellDef="let v">{{ v.phone }}</td></ng-container>
        <ng-container matColumnDef="contractedAmount"><th mat-header-cell *matHeaderCellDef>Amount</th><td mat-cell *matCellDef="let v">{{ v.contractedAmount | currency:'INR' }}</td></ng-container>
        <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Status</th><td mat-cell *matCellDef="let v"><span class="status-badge" [ngClass]="v.status.toLowerCase()">{{ v.status }}</span></td></ng-container>
        <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef>Actions</th><td mat-cell *matCellDef="let v">
          <a mat-icon-button [routerLink]="['/vendors', v.vendorId]" matTooltip="View"><mat-icon>visibility</mat-icon></a>
          <a mat-icon-button [routerLink]="['/vendors', v.vendorId, 'ledger']" matTooltip="Ledger"><mat-icon>receipt_long</mat-icon></a>
          <a mat-icon-button [routerLink]="['/vendors/edit', v.vendorId]" matTooltip="Edit"><mat-icon>edit</mat-icon></a>
        </td></ng-container>
        <tr mat-header-row *matHeaderRowDef="isMobile ? mobileColumns : displayedColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: isMobile ? mobileColumns : displayedColumns;"></tr>
      </table>
      <mat-paginator [length]="totalElements" [pageSize]="pageSize" [pageSizeOptions]="[10,20,50]" (page)="onPageChange($event)"></mat-paginator>
    </div>
  `
})
export class VendorListComponent implements OnInit {
  vendors: Vendor[] = [];
  displayedColumns = ['vendorName', 'category', 'phone', 'contractedAmount', 'status', 'actions'];
  mobileColumns = ['vendorName', 'contractedAmount', 'status', 'actions'];
  totalElements = 0; pageSize = 20; currentPage = 0;
  searchTerm = ''; statusFilter = '';
  isMobile = false;

  constructor(private vendorService: VendorService, private breakpointObserver: BreakpointObserver) {}
  ngOnInit(): void {
    this.breakpointObserver.observe(['(max-width: 768px)']).subscribe(result => {
      this.isMobile = result.matches;
    });
    this.loadVendors();
  }

  loadVendors(): void {
    this.vendorService.getAllVendors(this.currentPage, this.pageSize, this.statusFilter || undefined, undefined, this.searchTerm || undefined)
      .subscribe(res => { if (res.success) { this.vendors = res.data.content; this.totalElements = res.data.totalElements; }});
  }

  onPageChange(event: PageEvent): void { this.currentPage = event.pageIndex; this.pageSize = event.pageSize; this.loadVendors(); }
}
