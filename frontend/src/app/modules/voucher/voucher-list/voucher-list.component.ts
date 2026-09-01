import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { BreakpointObserver } from '@angular/cdk/layout';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { VoucherService } from '@core/services/voucher.service';
import { AuthService } from '@core/services/auth.service';
import { Voucher } from '@core/models/voucher.model';
import { environment } from '@env/environment';

@Component({
  selector: 'app-voucher-list',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, MatTableModule, MatPaginatorModule,
    MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatSlideToggleModule],
  template: `
    <div class="container">
      <div class="page-header">
        <h2>Voucher Management</h2>
        <div style="display: flex; gap: 8px;">
          <a mat-button routerLink="/vouchers/categories"
             *ngIf="hasPermission('VOUCHER_CREATE')">
            <mat-icon>category</mat-icon> Categories
          </a>
          <a mat-button routerLink="/vouchers/tds-config"
             *ngIf="hasPermission('VOUCHER_CREATE')">
            <mat-icon>percent</mat-icon> TDS Config
          </a>
          <button mat-raised-button color="accent" (click)="showBulkDownload = !showBulkDownload"
                  *ngIf="hasPermission('VOUCHER_DOWNLOAD_PDF')">
            <mat-icon>picture_as_pdf</mat-icon> Bulk PDF
          </button>
          <a mat-raised-button color="primary" routerLink="/vouchers/create"
             *ngIf="hasPermission('VOUCHER_CREATE')">
            <mat-icon>add</mat-icon> Create Voucher
          </a>
        </div>
      </div>

      <!-- Bulk Download Panel -->
      <div *ngIf="showBulkDownload" class="bulk-download-panel">
        <h4>Download Vouchers as PDF</h4>
        <p style="font-size:13px; color:#666; margin:0 0 12px;">Downloads all vouchers matching the current filters (search, type, status) as a PDF with summary page.</p>
        <div class="search-bar">
          <mat-form-field appearance="outline">
            <mat-label>Financial Year</mat-label>
            <mat-select [(ngModel)]="bulkFinancialYear">
              <mat-option value="">-- All FY --</mat-option>
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
        </div>
        <div style="display: flex; gap: 12px; align-items: center; margin-top: 12px;">
          <mat-slide-toggle [(ngModel)]="bulkIncludeBills" color="primary">
            Include Attached Bills/Invoices
          </mat-slide-toggle>
          <button mat-raised-button color="primary" (click)="downloadBulkPdf(false)"
                  [disabled]="!canDownloadBulk()">
            <mat-icon>download</mat-icon> Download Without Bills
          </button>
          <button mat-raised-button color="accent" (click)="downloadBulkPdf(true)"
                  [disabled]="!canDownloadBulk()">
            <mat-icon>attach_file</mat-icon> Download With Bills
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
            <mat-option value="PENDING_APPROVAL">Pending Approval</mat-option>
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
        <ng-container matColumnDef="referenceNumber"><th mat-header-cell *matHeaderCellDef>Cheque No.</th><td mat-cell *matCellDef="let v">{{ v.referenceNumber || '-' }}</td></ng-container>
        <ng-container matColumnDef="amount"><th mat-header-cell *matHeaderCellDef>Amount</th><td mat-cell *matCellDef="let v">{{ v.amount | currency:'INR' }}</td></ng-container>
        <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Status</th><td mat-cell *matCellDef="let v"><span class="status-badge" [ngClass]="v.status.toLowerCase()">{{ v.status }}</span></td></ng-container>
        <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef>Actions</th><td mat-cell *matCellDef="let v">
          <a mat-icon-button [routerLink]="['/vouchers', v.voucherId]"><mat-icon>visibility</mat-icon></a>
          <a mat-icon-button [routerLink]="['/vouchers/edit', v.voucherId]"
             *ngIf="v.status === 'DRAFT' && hasPermission('VOUCHER_UPDATE')"><mat-icon>edit</mat-icon></a>
        </td></ng-container>
        <tr mat-header-row *matHeaderRowDef="isMobile ? mobileColumns : displayedColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: isMobile ? mobileColumns : displayedColumns;"></tr>
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

    @media (max-width: 768px) {
      .bulk-download-panel { padding: 12px; }
    }
  `]
})
export class VoucherListComponent implements OnInit {
  vouchers: Voucher[] = [];
  displayedColumns = ['voucherNumber', 'voucherDate', 'voucherType', 'description', 'referenceNumber', 'amount', 'status', 'actions'];
  mobileColumns = ['voucherNumber', 'amount', 'status', 'actions'];
  totalElements = 0; pageSize = 20; currentPage = 0;
  searchTerm = ''; typeFilter = ''; statusFilter = '';
  isMobile = false;

  // Bulk download
  showBulkDownload = false;
  bulkFinancialYear = '';
  bulkStartDate = '';
  bulkEndDate = '';
  bulkIncludeBills = false;

  constructor(private voucherService: VoucherService, private http: HttpClient, private breakpointObserver: BreakpointObserver, private authService: AuthService) {}
  ngOnInit(): void {
    this.breakpointObserver.observe(['(max-width: 768px)']).subscribe(result => {
      this.isMobile = result.matches;
    });
    this.loadVouchers();
  }

  loadVouchers(): void {
    this.voucherService.getAllVouchers(this.currentPage, this.pageSize, this.typeFilter || undefined,
      this.statusFilter || undefined, undefined, undefined, undefined, undefined, this.searchTerm || undefined)
      .subscribe(res => { if (res.success) { this.vouchers = res.data.content; this.totalElements = res.data.totalElements; }});
  }

  onPageChange(event: PageEvent): void { this.currentPage = event.pageIndex; this.pageSize = event.pageSize; this.loadVouchers(); }

  canDownloadBulk(): boolean {
    return !!this.bulkFinancialYear || (!!this.bulkStartDate && !!this.bulkEndDate);
  }

  downloadBulkPdf(includeBills: boolean): void {
    let url = `${environment.apiUrl}/vouchers/pdf/bulk?`;
    const params: string[] = [];
    if (this.bulkFinancialYear) params.push(`financialYear=${this.bulkFinancialYear}`);
    if (this.bulkStartDate) params.push(`startDate=${this.bulkStartDate}`);
    if (this.bulkEndDate) params.push(`endDate=${this.bulkEndDate}`);
    if (this.typeFilter) params.push(`type=${this.typeFilter}`);
    if (this.statusFilter) params.push(`status=${this.statusFilter}`);
    if (includeBills) params.push(`includeBills=true`);
    url += params.join('&');

    this.http.get(url, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const blobUrl = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = blobUrl;
        const suffix = includeBills ? '_with_bills' : '';
        link.download = this.bulkFinancialYear
          ? `Vouchers_FY_${this.bulkFinancialYear}${suffix}.pdf`
          : `Vouchers_${this.bulkStartDate}_to_${this.bulkEndDate}${suffix}.pdf`;
        link.click();
        window.URL.revokeObjectURL(blobUrl);
      },
      error: () => {}
    });
  }

  hasPermission(permission: string): boolean {
    return this.authService.hasPermission(permission);
  }
}
