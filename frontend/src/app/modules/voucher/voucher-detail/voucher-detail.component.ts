import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { VoucherService } from '@core/services/voucher.service';
import { AuthService } from '@core/services/auth.service';
import { Voucher, VoucherAudit } from '@core/models/voucher.model';
import { environment } from '@env/environment';

@Component({
  selector: 'app-voucher-detail',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterModule, MatCardModule, MatButtonModule,
    MatIconModule, MatTableModule, MatChipsModule, MatDividerModule,
    MatTooltipModule, MatSnackBarModule
  ],
  template: `
    <div class="container" *ngIf="voucher">
      <div class="page-header">
        <h2>Voucher: {{ voucher.voucherNumber }}</h2>
        <div class="header-actions">
          <button mat-raised-button color="primary" (click)="downloadPdf()">
            <mat-icon>download</mat-icon> Download PDF
          </button>
          <button mat-raised-button color="accent" (click)="printVoucher()">
            <mat-icon>print</mat-icon> Print Voucher
          </button>
          <a mat-button routerLink="/vouchers">Back to List</a>
        </div>
      </div>

      <!-- Voucher Info Card -->
      <mat-card>
        <mat-card-header>
          <mat-card-title>
            {{ getVoucherTypeLabel(voucher.voucherType) }}
          </mat-card-title>
          <span class="status-badge" [ngClass]="voucher.status.toLowerCase()">{{ voucher.status }}</span>
        </mat-card-header>
        <mat-card-content>
          <div class="detail-grid">
            <div><strong>Voucher No:</strong> {{ voucher.voucherNumber }}</div>
            <div><strong>Date:</strong> {{ voucher.voucherDate }}</div>
            <div><strong>Type:</strong> {{ voucher.voucherType }}</div>
            <div><strong>Financial Year:</strong> {{ voucher.financialYear }}</div>
            <div><strong>Category:</strong> {{ voucher.category.replace('_', ' ') }}</div>
            <div><strong>Vendor:</strong> {{ voucher.vendorName || 'N/A' }}</div>
            <div><strong>Amount:</strong> <span class="amount">₹ {{ voucher.amount | number:'1.2-2' }}</span></div>
            <div><strong>Payment Mode:</strong> {{ voucher.paymentMode || 'N/A' }}</div>
            <div><strong>Cheque/Txn Ref:</strong> {{ voucher.referenceNumber || 'N/A' }}</div>
            <div><strong>Bill/Invoice No:</strong> {{ voucher.billInvoiceNumber || 'N/A' }}</div>
            <div><strong>Bill Date:</strong> {{ voucher.billDate || 'N/A' }}</div>
            <div><strong>Created By:</strong> {{ voucher.createdBy }}</div>
          </div>
          <mat-divider></mat-divider>
          <div class="description-section">
            <strong>Description / Narration:</strong>
            <p>{{ voucher.description }}</p>
          </div>

          <div *ngIf="voucher.status === 'CANCELLED'" class="cancelled-section">
            <mat-divider></mat-divider>
            <div class="detail-grid" style="margin-top: 12px;">
              <div><strong>Cancelled By:</strong> {{ voucher.cancelledBy }}</div>
              <div><strong>Cancelled On:</strong> {{ voucher.cancelledOn }}</div>
              <div class="full-width"><strong>Reason:</strong> {{ voucher.cancellationReason }}</div>
            </div>
          </div>
        </mat-card-content>
      </mat-card>

      <!-- Uploaded Documents / Invoice / Bill -->
      <mat-card style="margin-top: 16px">
        <mat-card-header>
          <mat-card-title>Attached Documents / Invoice</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div *ngIf="documents.length === 0" class="no-docs">
            <mat-icon>folder_open</mat-icon>
            <p>No documents attached to this voucher</p>
          </div>

          <div *ngIf="documents.length > 0" class="doc-list">
            <div *ngFor="let doc of documents" class="doc-item">
              <mat-icon class="doc-icon">{{ getDocIcon(doc.documentName) }}</mat-icon>
              <div class="doc-info">
                <span class="doc-name">{{ doc.documentName }}</span>
                <span class="doc-meta">{{ doc.documentType }} | Uploaded: {{ doc.uploadedOn | date:'dd-MM-yyyy HH:mm' }}</span>
              </div>
              <div class="doc-actions">
                <button mat-icon-button color="primary" (click)="viewDocument(doc)"
                        matTooltip="View Document">
                  <mat-icon>visibility</mat-icon>
                </button>
                <button mat-icon-button (click)="downloadDocument(doc)"
                        matTooltip="Download Document">
                  <mat-icon>download</mat-icon>
                </button>
              </div>
            </div>
          </div>

          <!-- Upload more documents -->
          <div class="upload-more" *ngIf="voucher.status !== 'CANCELLED'">
            <mat-divider style="margin: 12px 0;"></mat-divider>
            <input type="file" #docInput (change)="onDocumentSelected($event)"
                   accept=".pdf,.jpg,.jpeg,.png,.doc,.docx" style="display:none">
            <button mat-stroked-button (click)="docInput.click()">
              <mat-icon>attach_file</mat-icon> Attach Invoice / Bill
            </button>
            <span class="file-name" *ngIf="newDocFile">{{ newDocFile.name }}</span>
            <button mat-raised-button color="primary" *ngIf="newDocFile" (click)="uploadDocument()">
              <mat-icon>cloud_upload</mat-icon> Upload
            </button>
          </div>
        </mat-card-content>
      </mat-card>

      <!-- Approval Status Card -->
      <mat-card style="margin-top: 16px" *ngIf="voucher.status === 'PENDING_APPROVAL' || voucher.status === 'FINAL'">
        <mat-card-header>
          <mat-card-title>Approval Workflow</mat-card-title>
          <span class="status-badge" [ngClass]="voucher.status.toLowerCase()">{{ voucher.status }}</span>
        </mat-card-header>
        <mat-card-content>
          <div class="approval-grid">
            <!-- Treasurer -->
            <div class="approval-step" [class.done]="voucher.viewedByTreasurer">
              <mat-icon class="step-icon" [class.done]="voucher.viewedByTreasurer">
                {{ voucher.viewedByTreasurer ? 'check_circle' : 'radio_button_unchecked' }}
              </mat-icon>
              <div class="step-info">
                <strong>Treasurer View</strong>
                <span *ngIf="voucher.viewedByTreasurer">
                  {{ voucher.treasurerName }} | {{ voucher.treasurerViewedOn | date:'dd-MM-yyyy HH:mm' }}
                </span>
                <span *ngIf="!voucher.viewedByTreasurer" class="pending">Pending</span>
              </div>
            </div>

            <!-- Secretary -->
            <div class="approval-step" [class.done]="voucher.verifiedBySecretary">
              <mat-icon class="step-icon" [class.done]="voucher.verifiedBySecretary">
                {{ voucher.verifiedBySecretary ? 'check_circle' : 'radio_button_unchecked' }}
              </mat-icon>
              <div class="step-info">
                <strong>Secretary Verification</strong>
                <span *ngIf="voucher.verifiedBySecretary">
                  {{ voucher.secretaryName }} | {{ voucher.secretaryVerifiedOn | date:'dd-MM-yyyy HH:mm' }}
                </span>
                <span *ngIf="!voucher.verifiedBySecretary" class="pending">Pending</span>
              </div>
            </div>

            <!-- Chairman -->
            <div class="approval-step" [class.done]="voucher.approvedByChairman">
              <mat-icon class="step-icon" [class.done]="voucher.approvedByChairman">
                {{ voucher.approvedByChairman ? 'check_circle' : 'radio_button_unchecked' }}
              </mat-icon>
              <div class="step-info">
                <strong>Chairman Approval</strong>
                <span *ngIf="voucher.approvedByChairman">
                  {{ voucher.chairmanName }} | {{ voucher.chairmanApprovedOn | date:'dd-MM-yyyy HH:mm' }}
                </span>
                <span *ngIf="!voucher.approvedByChairman" class="pending">Pending</span>
              </div>
            </div>
          </div>

          <!-- Approval Action Buttons -->
          <div class="approval-actions" *ngIf="voucher.status === 'PENDING_APPROVAL'">
            <mat-divider style="margin: 12px 0;"></mat-divider>
            <button mat-raised-button color="primary" (click)="treasurerView()"
                    *ngIf="!voucher.viewedByTreasurer && hasAnyRole(['SUPER_ADMIN', 'TREASURER'])">
              <mat-icon>visibility</mat-icon> Mark as Viewed (Treasurer)
            </button>
            <button mat-raised-button color="accent" (click)="secretaryVerify()"
                    *ngIf="!voucher.verifiedBySecretary && hasAnyRole(['SUPER_ADMIN', 'SECRETARY'])">
              <mat-icon>verified</mat-icon> Verify (Secretary)
            </button>
            <button mat-raised-button style="background: #2e7d32; color: white;" (click)="chairmanApprove()"
                    *ngIf="!voucher.approvedByChairman && hasAnyRole(['SUPER_ADMIN', 'CHAIRMAN'])">
              <mat-icon>thumb_up</mat-icon> Approve (Chairman)
            </button>
          </div>
        </mat-card-content>
      </mat-card>

      <!-- Action Buttons -->
      <mat-card style="margin-top: 16px" *ngIf="voucher.status !== 'CANCELLED'">
        <mat-card-content>
          <div class="action-buttons" style="justify-content: flex-start;">
            <a mat-raised-button color="primary" [routerLink]="['/vouchers/edit', voucher.voucherId]"
               *ngIf="voucher.status === 'DRAFT'">
              <mat-icon>edit</mat-icon> Edit Voucher
            </a>
            <button mat-raised-button color="accent" (click)="submitForApproval()"
                    *ngIf="voucher.status === 'DRAFT'">
              <mat-icon>send</mat-icon> Submit for Approval
            </button>
            <button mat-raised-button color="warn" (click)="finalizeVoucher()"
                    *ngIf="voucher.status !== 'FINAL' && hasAnyRole(['SUPER_ADMIN'])">
              <mat-icon>check_circle</mat-icon> Force Finalize (Admin)
            </button>
            <button mat-raised-button color="warn" (click)="showCancelDialog = true"
                    *ngIf="voucher.status !== 'FINAL'">
              <mat-icon>cancel</mat-icon> Cancel Voucher
            </button>
          </div>
        </mat-card-content>
      </mat-card>

      <!-- Cancel Dialog (inline) -->
      <mat-card style="margin-top: 16px" *ngIf="showCancelDialog">
        <mat-card-header><mat-card-title>Cancel Voucher</mat-card-title></mat-card-header>
        <mat-card-content>
          <p>Are you sure you want to cancel voucher <strong>{{ voucher.voucherNumber }}</strong>?</p>
          <textarea [(ngModel)]="cancelReason" placeholder="Enter cancellation reason (mandatory)"
                    rows="3" style="width: 100%; padding: 8px; margin-bottom: 12px;"></textarea>
          <div class="action-buttons">
            <button mat-button (click)="showCancelDialog = false">Back</button>
            <button mat-raised-button color="warn" (click)="cancelVoucher()"
                    [disabled]="!cancelReason.trim()">
              Confirm Cancel
            </button>
          </div>
        </mat-card-content>
      </mat-card>

      <!-- Audit Trail -->
      <mat-card style="margin-top: 16px" *ngIf="auditTrail.length > 0">
        <mat-card-header><mat-card-title>Audit Trail</mat-card-title></mat-card-header>
        <mat-card-content>
          <table mat-table [dataSource]="auditTrail" class="mat-elevation-z1">
            <ng-container matColumnDef="changedOn">
              <th mat-header-cell *matHeaderCellDef>Date/Time</th>
              <td mat-cell *matCellDef="let a">{{ a.changedOn | date:'dd-MM-yyyy HH:mm' }}</td>
            </ng-container>
            <ng-container matColumnDef="fieldChanged">
              <th mat-header-cell *matHeaderCellDef>Field</th>
              <td mat-cell *matCellDef="let a">{{ a.fieldChanged }}</td>
            </ng-container>
            <ng-container matColumnDef="oldValue">
              <th mat-header-cell *matHeaderCellDef>Old Value</th>
              <td mat-cell *matCellDef="let a">{{ a.oldValue || '-' }}</td>
            </ng-container>
            <ng-container matColumnDef="newValue">
              <th mat-header-cell *matHeaderCellDef>New Value</th>
              <td mat-cell *matCellDef="let a">{{ a.newValue }}</td>
            </ng-container>
            <ng-container matColumnDef="changeReason">
              <th mat-header-cell *matHeaderCellDef>Reason</th>
              <td mat-cell *matCellDef="let a">{{ a.changeReason || '-' }}</td>
            </ng-container>
            <ng-container matColumnDef="changedBy">
              <th mat-header-cell *matHeaderCellDef>By</th>
              <td mat-cell *matCellDef="let a">{{ a.changedBy }}</td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="auditColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: auditColumns;"></tr>
          </table>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .header-actions { display: flex; gap: 8px; align-items: center; }
    .detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; padding: 16px 0; }
    .full-width { grid-column: span 2; }
    .amount { font-size: 1.2rem; font-weight: 500; color: #1976d2; }
    .description-section { padding: 16px 0; }
    .description-section p { margin: 8px 0 0; color: #333; }
    .cancelled-section { background: #fff3f3; padding: 12px; border-radius: 4px; margin-top: 12px; }
    mat-card-header { display: flex; justify-content: space-between; align-items: center; }
    textarea { border: 1px solid #ccc; border-radius: 4px; font-family: inherit; }
    .no-docs { text-align: center; padding: 20px; color: #999; }
    .no-docs mat-icon { font-size: 40px; height: 40px; width: 40px; }
    .doc-list { display: flex; flex-direction: column; gap: 8px; }
    .doc-item { display: flex; align-items: center; gap: 12px; padding: 10px 12px; background: #fafafa; border-radius: 8px; border: 1px solid #e0e0e0; }
    .doc-icon { color: #1976d2; font-size: 28px; height: 28px; width: 28px; }
    .doc-info { flex: 1; display: flex; flex-direction: column; }
    .doc-name { font-weight: 500; font-size: 14px; }
    .doc-meta { font-size: 12px; color: #666; margin-top: 2px; }
    .doc-actions { display: flex; gap: 4px; }
    .upload-more { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
    .file-name { font-weight: 500; color: #1976d2; font-size: 13px; }
    .approval-grid { display: flex; gap: 24px; padding: 16px 0; flex-wrap: wrap; }
    .approval-step { display: flex; align-items: flex-start; gap: 10px; min-width: 220px; padding: 12px; border-radius: 8px; background: #f5f5f5; border: 1px solid #e0e0e0; }
    .approval-step.done { background: #e8f5e9; border-color: #a5d6a7; }
    .step-icon { color: #bdbdbd; font-size: 28px; height: 28px; width: 28px; }
    .step-icon.done { color: #2e7d32; }
    .step-info { display: flex; flex-direction: column; }
    .step-info strong { font-size: 13px; }
    .step-info span { font-size: 12px; color: #666; margin-top: 2px; }
    .step-info .pending { color: #f57c00; font-style: italic; }
    .approval-actions { display: flex; gap: 12px; flex-wrap: wrap; }
    .status-badge.pending_approval { background: #fff3e0; color: #e65100; padding: 4px 10px; border-radius: 12px; font-size: 12px; font-weight: 500; }
  `]
})
export class VoucherDetailComponent implements OnInit {
  voucher?: Voucher;
  auditTrail: VoucherAudit[] = [];
  documents: any[] = [];
  auditColumns = ['changedOn', 'fieldChanged', 'oldValue', 'newValue', 'changeReason', 'changedBy'];
  showCancelDialog = false;
  cancelReason = '';
  newDocFile: File | null = null;

  private apiUrl = environment.apiUrl;

  constructor(
    private route: ActivatedRoute,
    private http: HttpClient,
    private voucherService: VoucherService,
    private authService: AuthService,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    const id = +this.route.snapshot.paramMap.get('id')!;
    this.loadVoucher(id);
    this.loadAuditTrail(id);
    this.loadDocuments(id);
  }

  loadVoucher(id: number): void {
    this.voucherService.getVoucherById(id).subscribe(res => {
      if (res.success) this.voucher = res.data;
    });
  }

  loadAuditTrail(id: number): void {
    this.voucherService.getAuditTrail(id).subscribe(res => {
      if (res.success) this.auditTrail = res.data;
    });
  }

  downloadPdf(): void {
    if (!this.voucher) return;
    this.http.get(`${this.apiUrl}/vouchers/${this.voucher.voucherId}/pdf`, { responseType: 'blob' })
      .subscribe(blob => {
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `Voucher_${this.voucher!.voucherNumber}.pdf`;
        link.click();
        window.URL.revokeObjectURL(url);
      });
  }

  printVoucher(): void {
    if (!this.voucher) return;
    this.http.get(`${this.apiUrl}/vouchers/${this.voucher.voucherId}/pdf/view`, { responseType: 'blob' })
      .subscribe(blob => {
        const url = window.URL.createObjectURL(blob);
        window.open(url, '_blank');
      });
  }

  finalizeVoucher(): void {
    if (!this.voucher) return;
    this.voucherService.finalizeVoucher(this.voucher.voucherId).subscribe(res => {
      if (res.success) {
        this.snackBar.open('Voucher finalized successfully', 'Close', { duration: 3000 });
        this.voucher = res.data;
        this.loadAuditTrail(this.voucher!.voucherId);
      }
    });
  }

  submitForApproval(): void {
    if (!this.voucher) return;
    this.voucherService.submitForApproval(this.voucher.voucherId).subscribe({
      next: (res) => {
        if (res.success) {
          this.snackBar.open('Voucher submitted for approval', 'Close', { duration: 3000 });
          this.voucher = res.data;
          this.loadAuditTrail(this.voucher!.voucherId);
        }
      },
      error: (err) => this.snackBar.open(err.error?.message || 'Failed', 'Close', { duration: 5000 })
    });
  }

  treasurerView(): void {
    if (!this.voucher) return;
    this.voucherService.treasurerView(this.voucher.voucherId).subscribe({
      next: (res) => {
        if (res.success) {
          this.snackBar.open('Marked as viewed by Treasurer', 'Close', { duration: 3000 });
          this.voucher = res.data;
          this.loadAuditTrail(this.voucher!.voucherId);
        }
      },
      error: (err) => this.snackBar.open(err.error?.message || 'Failed', 'Close', { duration: 5000 })
    });
  }

  secretaryVerify(): void {
    if (!this.voucher) return;
    this.voucherService.secretaryVerify(this.voucher.voucherId).subscribe({
      next: (res) => {
        if (res.success) {
          this.snackBar.open('Verified by Secretary', 'Close', { duration: 3000 });
          this.voucher = res.data;
          this.loadAuditTrail(this.voucher!.voucherId);
        }
      },
      error: (err) => this.snackBar.open(err.error?.message || 'Failed', 'Close', { duration: 5000 })
    });
  }

  chairmanApprove(): void {
    if (!this.voucher) return;
    this.voucherService.chairmanApprove(this.voucher.voucherId).subscribe({
      next: (res) => {
        if (res.success) {
          this.snackBar.open('Approved by Chairman', 'Close', { duration: 3000 });
          this.voucher = res.data;
          this.loadAuditTrail(this.voucher!.voucherId);
        }
      },
      error: (err) => this.snackBar.open(err.error?.message || 'Failed', 'Close', { duration: 5000 })
    });
  }

  hasAnyRole(roles: string[]): boolean {
    return this.authService.hasAnyRole(roles);
  }

  cancelVoucher(): void {
    if (!this.voucher || !this.cancelReason?.trim()) return;
    this.voucherService.cancelVoucher(this.voucher.voucherId, {
      cancellationReason: this.cancelReason.trim()
    }).subscribe(res => {
      if (res.success) {
        this.snackBar.open('Voucher cancelled', 'Close', { duration: 3000 });
        this.voucher = res.data;
        this.showCancelDialog = false;
        this.cancelReason = '';
        this.loadAuditTrail(this.voucher!.voucherId);
      }
    });
  }

  getVoucherTypeLabel(type: string): string {
    const labels: Record<string, string> = {
      'PAYMENT': 'Payment Voucher',
      'RECEIPT': 'Receipt Voucher',
      'JOURNAL': 'Journal Voucher',
      'CONTRA': 'Contra Voucher'
    };
    return labels[type] || type;
  }

  // ===== DOCUMENT METHODS =====

  loadDocuments(id: number): void {
    this.http.get<any>(`${this.apiUrl}/vouchers/${id}/documents`).subscribe(res => {
      if (res.success) this.documents = res.data;
    });
  }

  viewDocument(doc: any): void {
    this.http.get(`${this.apiUrl}/files/view/${doc.filePath}`, { responseType: 'blob' })
      .subscribe({
        next: (blob) => {
          const url = window.URL.createObjectURL(blob);
          window.open(url, '_blank');
        },
        error: (err) => {
          this.snackBar.open('Failed to load document: ' + (err.status === 404 ? 'File not found' : err.statusText), 'Close', { duration: 5000 });
        }
      });
  }

  downloadDocument(doc: any): void {
    this.http.get(`${this.apiUrl}/files/download/${doc.filePath}`, { responseType: 'blob' })
      .subscribe({
        next: (blob) => {
          const url = window.URL.createObjectURL(blob);
          const link = document.createElement('a');
          link.href = url;
          link.download = doc.documentName;
          link.click();
          window.URL.revokeObjectURL(url);
        },
        error: (err) => {
          this.snackBar.open('Failed to download: ' + (err.status === 404 ? 'File not found' : err.statusText), 'Close', { duration: 5000 });
        }
      });
  }

  onDocumentSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.newDocFile = input.files[0];
    }
  }

  uploadDocument(): void {
    if (!this.newDocFile || !this.voucher) return;

    const formData = new FormData();
    formData.append('file', this.newDocFile);
    formData.append('documentType', 'BILL');

    this.http.post<any>(`${this.apiUrl}/vouchers/${this.voucher.voucherId}/documents/upload`, formData)
      .subscribe({
        next: (res) => {
          if (res.success) {
            this.snackBar.open('Document uploaded successfully', 'Close', { duration: 3000 });
            this.newDocFile = null;
            this.loadDocuments(this.voucher!.voucherId);
          }
        },
        error: () => this.snackBar.open('Upload failed', 'Close', { duration: 3000 })
      });
  }

  getDocIcon(filename: string): string {
    if (!filename) return 'description';
    const ext = filename.split('.').pop()?.toLowerCase();
    switch (ext) {
      case 'pdf': return 'picture_as_pdf';
      case 'jpg': case 'jpeg': case 'png': return 'image';
      case 'doc': case 'docx': return 'article';
      default: return 'description';
    }
  }
}
