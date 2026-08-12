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
import { VoucherService } from '@core/services/voucher.service';
import { Voucher } from '@core/models/voucher.model';

@Component({
  selector: 'app-voucher-list',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, MatTableModule, MatPaginatorModule,
    MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  template: `
    <div class="container">
      <div class="page-header">
        <h2>Voucher Management</h2>
        <div style="display: flex; gap: 8px;">
          <button mat-raised-button color="accent" (click)="showBulkDownload = !showBulkDownload">
            <mat-icon>picture_as_pdf</mat-icon> Bulk PDF
          </button>
          <a mat-raised-button color="primary" routerLink="/vouchers/create">
            <mat-icon>add</mat-icon> Create Voucher
          </a>
        </div>
      </div>

      <!-- Bulk Download Panel -->
      <div *ngIf="showBulkDownload" class="bulk-download-panel">
        <h4>Download All Vouchers as PDF</h4>
        <div class="search-bar">
          <mat-form-field appearance="outline">
            <mat-label>Financial Year</mat-label>
            <mat-select [(ngModel)]="bulkFinancialYear">
              <mat-option value="">-- Select FY --</mat-option>
              <mat-option value="2024-25">2024-25</mat-option>
              <mat-option value="2025-26">2025-26</mat-option>
              <mat-option value="2026-27">2026-27</mat-option>
              <mat-option value="2027-28">2027-28</mat-option>
            </mat-select>
          </mat-form-field>
          <span style="padding: 10px;">OR</span>
          <mat-form-field appearance="outline">
            <mat-label>From Date</mat-label>
            <input matInput type="date" [(ngModel)]="bulkStartDate">
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>To Date</mat-label>
            <input matInput type="date" [(ngModel)]="bulkEndDate">
          </mat-form-field>
          <button mat-raised-button color="primary" (click)="downloadBulkPdf()"
                  [disabled]="!canDownloadBulk()">
            <mat-icon>download</mat-icon> Download PDF
          </button>
        </div>
      </div>

      <div class="search-bar">
        <mat-form-field appearance="outline">
          <mat-label>Search</mat-label>
          <input matInput [(ngModel)]="searchTerm" (keyup.enter)="loadVouchers()" placeholder="Voucher No, Description">
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Type</mat-label>
          <mat-select [(ngModel)]="typeFilter" (selectionChange)="loadVouchers()">
            <mat-option value="">All</mat-option>
            <mat-option value="PAYMENT">Payment</mat-option>
            <mat-option value="RECEIPT">Receipt</mat-option>
            <mat-option value="JOURNAL">Journal</mat-option>
            <mat-option value="CONTRA">Contra</mat-option>
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Status</mat-label>
          <mat-select [(ngModel)]="statusFilter" (selectionChange)="loadVouchers()">
            <mat-option value="">All</mat-option>
            <mat-option value="DRAFT">Draft</mat-option>
            <mat-option value="FINAL">Final</mat-option>
            <mat-option value="CANCELLED">Cancelled</mat-option>
          </mat-select>
        </mat-form-field>
      </div>
      <table mat-table [dataSource]="vouchers" class="mat-elevation-z2">
        <ng-container matColumnDef="voucherNumber"><th mat-header-cell *matHeaderCellDef>Voucher No.</th><td mat-cell *matCellDef="let v">{{ v.voucherNumber }}</td></ng-container>
        <ng-container matColumnDef="voucherDate"><th mat-header-cell *matHeaderCellDef>Date</th><td mat-cell *matCellDef="let v">{{ v.voucherDate }}</td></ng-container>
        <ng-container matColumnDef="voucherType"><th mat-header-cell *matHeaderCellDef>Type</th><td mat-cell *matCellDef="let v">{{ v.voucherType }}</td></ng-container>
        <ng-container matColumnDef="description"><th mat-header-cell *matHeaderCellDef>Description</th><td mat-cell *matCellDef="let v">{{ v.description | slice:0:40 }}...</td></ng-container>
        <ng-container matColumnDef="amount"><th mat-header-cell *matHeaderCellDef>Amount</th><td mat-cell *matCellDef="let v">{{ v.amount | currency:'INR' }}</td></ng-container>
        <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Status</th><td mat-cell *matCellDef="let v"><span class="status-badge" [ngClass]="v.status.toLowerCase()">{{ v.status }}</span></td></ng-container>
        <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef>Actions</th><td mat-cell *matCellDef="let v">
          <a mat-icon-button [routerLink]="['/vouchers', v.voucherId]"><mat-icon>visibility</mat-icon></a>
          <a mat-icon-button [routerLink]="['/vouchers/edit', v.voucherId]" *ngIf="v.status !== 'CANCELLED'"><mat-icon>edit</mat-icon></a>
        </td></ng-container>
        <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
      </table>
      <mat-paginator [length]="totalElements" [pageSize]="pageSize" [pageSizeOptions]="[10,20,50]" (page)="onPageChange($event)"></mat-paginator>
    </div>
  `,
  styles: [`
    .bulk-download-panel {
      background: #f5f5f5;
      border: 1px solid #e0e0e0;
      border-radius: 8px;
      padding: 16px;
      margin-bottom: 16px;
    }
    .bulk-download-panel h4 { margin: 0 0 12px; color: #1976d2; }
  `]
})
export class VoucherListComponent implements OnInit {
  vouchers: Voucher[] = [];
  displayedColumns = ['voucherNumber', 'voucherDate', 'voucherType', 'description', 'amount', 'status', 'actions'];
  totalElements = 0; pageSize = 20; currentPage = 0;
  searchTerm = ''; typeFilter = ''; statusFilter = '';

  // Bulk download
  showBulkDownload = false;
  bulkFinancialYear = '';
  bulkStartDate = '';
  bulkEndDate = '';

  constructor(private voucherService: VoucherService) {}
  ngOnInit(): void { this.loadVouchers(); }

  loadVouchers(): void {
    this.voucherService.getAllVouchers(this.currentPage, this.pageSize, this.typeFilter || undefined,
      this.statusFilter || undefined, undefined, undefined, undefined, undefined, this.searchTerm || undefined)
      .subscribe(res => { if (res.success) { this.vouchers = res.data.content; this.totalElements = res.data.totalElements; }});
  }

  onPageChange(event: PageEvent): void { this.currentPage = event.pageIndex; this.pageSize = event.pageSize; this.loadVouchers(); }

  canDownloadBulk(): boolean {
    return !!this.bulkFinancialYear || (!!this.bulkStartDate && !!this.bulkEndDate);
  }

  downloadBulkPdf(): void {
    const url = this.voucherService.getBulkPdfUrl(
      this.bulkStartDate || undefined,
      this.bulkEndDate || undefined,
      this.bulkFinancialYear || undefined
    );
    window.open(url, '_blank');
  }
}
