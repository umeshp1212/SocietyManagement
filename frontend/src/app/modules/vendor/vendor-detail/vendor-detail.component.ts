import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { VendorService } from '@core/services/vendor.service';
import { Vendor, VendorDocument } from '@core/models/vendor.model';

@Component({
  selector: 'app-vendor-detail',
  standalone: true,
  imports: [
    CommonModule, RouterModule, MatCardModule, MatButtonModule,
    MatIconModule, MatChipsModule, MatDividerModule, MatTableModule,
    MatProgressSpinnerModule, MatTooltipModule
  ],
  template: `
    <div class="container" *ngIf="vendor; else loading">
      <!-- Header -->
      <div class="page-header">
        <div class="header-left">
          <h2>{{ vendor.vendorName }}</h2>
          <span class="status-chip" [class]="vendor.status.toLowerCase()">
            {{ vendor.status }}
          </span>
        </div>
        <div class="header-actions">
          <a mat-stroked-button [routerLink]="['/vendors', vendor.vendorId, 'ledger']">
            <mat-icon>receipt_long</mat-icon> View Ledger
          </a>
          <a mat-raised-button color="primary" [routerLink]="['/vendors/edit', vendor.vendorId]">
            <mat-icon>edit</mat-icon> Edit
          </a>
          <a mat-button routerLink="/vendors">
            <mat-icon>arrow_back</mat-icon> Back
          </a>
        </div>
      </div>

      <!-- Basic Information -->
      <mat-card>
        <mat-card-header>
          <mat-card-title>Basic Information</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div class="info-grid">
            <div class="info-item">
              <label>Category</label>
              <span>{{ formatCategory(vendor.category) }}</span>
            </div>
            <div class="info-item">
              <label>Contact Person</label>
              <span>{{ vendor.contactPerson || '-' }}</span>
            </div>
            <div class="info-item">
              <label>Phone</label>
              <span>
                <a href="tel:{{vendor.phone}}">{{ vendor.phone }}</a>
              </span>
            </div>
            <div class="info-item">
              <label>Email</label>
              <span>
                <a *ngIf="vendor.email" href="mailto:{{vendor.email}}">{{ vendor.email }}</a>
                <span *ngIf="!vendor.email">-</span>
              </span>
            </div>
            <div class="info-item full-width">
              <label>Address</label>
              <span>{{ vendor.address || '-' }}</span>
            </div>
          </div>
        </mat-card-content>
      </mat-card>

      <!-- Agreement & Contract Details -->
      <mat-card>
        <mat-card-header>
          <mat-card-title>Agreement & Contract</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div class="info-grid">
            <div class="info-item">
              <label>Agreement Start</label>
              <span>{{ vendor.agreementStartDate ? (vendor.agreementStartDate | date:'dd-MM-yyyy') : '-' }}</span>
            </div>
            <div class="info-item">
              <label>Agreement End</label>
              <span>{{ vendor.agreementEndDate ? (vendor.agreementEndDate | date:'dd-MM-yyyy') : '-' }}</span>
            </div>
            <div class="info-item">
              <label>Contracted Amount</label>
              <span *ngIf="vendor.contractedAmount">&#8377; {{ vendor.contractedAmount | number:'1.2-2' }}</span>
              <span *ngIf="!vendor.contractedAmount">-</span>
            </div>
            <div class="info-item">
              <label>Payment Frequency</label>
              <span>{{ vendor.paymentFrequency ? formatCategory(vendor.paymentFrequency) : '-' }}</span>
            </div>
            <div class="info-item" *ngIf="vendor.daysUntilExpiry !== undefined && vendor.daysUntilExpiry !== null">
              <label>Contract Status</label>
              <span *ngIf="vendor.isContractExpired" class="expired-text">
                <mat-icon class="small-icon">warning</mat-icon> Expired
              </span>
              <span *ngIf="!vendor.isContractExpired && vendor.daysUntilExpiry <= 30" class="expiring-text">
                <mat-icon class="small-icon">schedule</mat-icon> Expiring in {{ vendor.daysUntilExpiry }} days
              </span>
              <span *ngIf="!vendor.isContractExpired && vendor.daysUntilExpiry > 30" class="active-text">
                <mat-icon class="small-icon">check_circle</mat-icon> {{ vendor.daysUntilExpiry }} days remaining
              </span>
            </div>
          </div>
        </mat-card-content>
      </mat-card>

      <!-- Tax & Bank Details -->
      <mat-card>
        <mat-card-header>
          <mat-card-title>Tax & Bank Details</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div class="info-grid">
            <div class="info-item">
              <label>PAN Number</label>
              <span class="mono-text">{{ vendor.panNumber || '-' }}</span>
            </div>
            <div class="info-item">
              <label>GST Number</label>
              <span class="mono-text">{{ vendor.gstNumber || '-' }}</span>
            </div>
            <div class="info-item">
              <label>Bank Name</label>
              <span>{{ vendor.bankName || '-' }}</span>
            </div>
            <div class="info-item">
              <label>Account Number</label>
              <span class="mono-text">{{ vendor.bankAccountNumber || '-' }}</span>
            </div>
            <div class="info-item">
              <label>IFSC Code</label>
              <span class="mono-text">{{ vendor.bankIfsc || '-' }}</span>
            </div>
          </div>
        </mat-card-content>
      </mat-card>

      <!-- Documents -->
      <mat-card>
        <mat-card-header>
          <mat-card-title>Documents</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div *ngIf="documents.length > 0" class="documents-list">
            <div *ngFor="let doc of documents" class="document-item">
              <mat-icon class="doc-icon">description</mat-icon>
              <div class="doc-info">
                <span class="doc-name">{{ doc.documentName }}</span>
                <span class="doc-meta">{{ doc.documentType }} &middot; Uploaded {{ doc.uploadedOn | date:'dd-MM-yyyy' }}</span>
              </div>
              <a mat-icon-button [href]="getDocumentUrl(doc)" target="_blank" matTooltip="Download">
                <mat-icon>download</mat-icon>
              </a>
            </div>
          </div>
          <div *ngIf="documents.length === 0" class="empty-state">
            <mat-icon>folder_open</mat-icon>
            <p>No documents uploaded</p>
          </div>
        </mat-card-content>
      </mat-card>

      <!-- Audit Info -->
      <mat-card class="audit-card">
        <mat-card-content>
          <div class="audit-info">
            <span *ngIf="vendor.createdBy">Created by {{ vendor.createdBy }} on {{ vendor.createdOn | date:'dd-MM-yyyy HH:mm' }}</span>
            <span *ngIf="vendor.modifiedBy"> &middot; Last modified by {{ vendor.modifiedBy }} on {{ vendor.modifiedOn | date:'dd-MM-yyyy HH:mm' }}</span>
          </div>
        </mat-card-content>
      </mat-card>
    </div>

    <ng-template #loading>
      <div class="loading-container">
        <mat-spinner diameter="40"></mat-spinner>
        <p>Loading vendor details...</p>
      </div>
    </ng-template>
  `,
  styles: [`
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; flex-wrap: wrap; gap: 12px; }
    .header-left { display: flex; align-items: center; gap: 12px; }
    .header-left h2 { margin: 0; }
    .header-actions { display: flex; gap: 8px; align-items: center; }
    .status-chip { padding: 4px 12px; border-radius: 16px; font-size: 12px; font-weight: 500; text-transform: uppercase; }
    .status-chip.active { background: #e8f5e9; color: #2e7d32; }
    .status-chip.inactive { background: #fff3e0; color: #e65100; }
    .status-chip.blacklisted { background: #ffebee; color: #c62828; }

    mat-card { margin-bottom: 16px; }
    mat-card-header { margin-bottom: 12px; }

    .info-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(250px, 1fr)); gap: 16px; padding: 8px 0; }
    .info-item { display: flex; flex-direction: column; gap: 4px; }
    .info-item label { font-size: 12px; color: #666; font-weight: 500; text-transform: uppercase; }
    .info-item span { font-size: 14px; color: #333; }
    .info-item.full-width { grid-column: 1 / -1; }
    .mono-text { font-family: monospace; letter-spacing: 0.5px; }

    .expired-text { color: #c62828; display: flex; align-items: center; gap: 4px; }
    .expiring-text { color: #e65100; display: flex; align-items: center; gap: 4px; }
    .active-text { color: #2e7d32; display: flex; align-items: center; gap: 4px; }
    .small-icon { font-size: 16px; height: 16px; width: 16px; }

    .documents-list { display: flex; flex-direction: column; gap: 8px; }
    .document-item { display: flex; align-items: center; gap: 12px; padding: 8px 12px; background: #f9f9f9; border-radius: 8px; }
    .doc-icon { color: #1976d2; }
    .doc-info { display: flex; flex-direction: column; flex: 1; }
    .doc-name { font-weight: 500; font-size: 14px; }
    .doc-meta { font-size: 12px; color: #666; }

    .empty-state { text-align: center; padding: 24px; color: #999; }
    .empty-state mat-icon { font-size: 36px; height: 36px; width: 36px; margin-bottom: 8px; }
    .empty-state p { margin: 0; }

    .audit-card { background: #fafafa; }
    .audit-info { font-size: 12px; color: #888; }

    .loading-container { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 60px 20px; gap: 16px; color: #666; }

    @media (max-width: 768px) {
      .page-header { flex-direction: column; align-items: flex-start; }
      .header-actions { flex-wrap: wrap; }
      .info-grid { grid-template-columns: 1fr; }
    }
  `]
})
export class VendorDetailComponent implements OnInit {
  vendor: Vendor | null = null;
  documents: VendorDocument[] = [];

  constructor(
    private route: ActivatedRoute,
    private vendorService: VendorService
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.loadVendor(+id);
      this.loadDocuments(+id);
    }
  }

  loadVendor(vendorId: number): void {
    this.vendorService.getVendorById(vendorId).subscribe(res => {
      if (res.success) {
        this.vendor = res.data;
      }
    });
  }

  loadDocuments(vendorId: number): void {
    this.vendorService.getVendorDocuments(vendorId).subscribe(res => {
      if (res.success) {
        this.documents = res.data;
      }
    });
  }

  formatCategory(value: string): string {
    if (!value) return '-';
    return value.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
  }

  getDocumentUrl(doc: VendorDocument): string {
    return doc.filePath;
  }
}
