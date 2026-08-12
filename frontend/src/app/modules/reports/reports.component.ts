import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { environment } from '@env/environment';

@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatTabsModule,
    MatFormFieldModule,
    MatInputModule
  ],
  template: `
    <div class="reports-container">
      <h2>Reports</h2>

      <mat-tab-group>

        <!-- Financial Report Tab -->
        <mat-tab label="Financial Report">
          <div class="tab-content">
            <div class="filter-row">
              <mat-form-field>
                <mat-label>Start Date</mat-label>
                <input matInput type="date" [(ngModel)]="finStartDate" />
              </mat-form-field>
              <mat-form-field>
                <mat-label>End Date</mat-label>
                <input matInput type="date" [(ngModel)]="finEndDate" />
              </mat-form-field>
              <button mat-raised-button color="primary" (click)="loadFinancialReport()">
                <mat-icon>assessment</mat-icon> Generate
              </button>
            </div>

            <div class="card-grid" *ngIf="voucherSummary">
              <mat-card>
                <mat-card-content>
                  <div class="summary-label">Total Payments</div>
                  <div class="summary-value expense">{{ voucherSummary.totalPayments }}</div>
                </mat-card-content>
              </mat-card>
              <mat-card>
                <mat-card-content>
                  <div class="summary-label">Total Receipts</div>
                  <div class="summary-value income">{{ voucherSummary.totalReceipts }}</div>
                </mat-card-content>
              </mat-card>
              <mat-card>
                <mat-card-content>
                  <div class="summary-label">Final Count</div>
                  <div class="summary-value">{{ voucherSummary.finalCount }}</div>
                </mat-card-content>
              </mat-card>
              <mat-card>
                <mat-card-content>
                  <div class="summary-label">Draft Count</div>
                  <div class="summary-value">{{ voucherSummary.draftCount }}</div>
                </mat-card-content>
              </mat-card>
            </div>

            <!-- Category-wise Expenses Table -->
            <h3 *ngIf="categoryWiseData.length">Category-wise Expenses</h3>
            <table mat-table [dataSource]="categoryWiseData" *ngIf="categoryWiseData.length" class="full-width-table">
              <ng-container matColumnDef="category">
                <th mat-header-cell *matHeaderCellDef>Category</th>
                <td mat-cell *matCellDef="let row">{{ row.category }}</td>
              </ng-container>
              <ng-container matColumnDef="amount">
                <th mat-header-cell *matHeaderCellDef>Amount</th>
                <td mat-cell *matCellDef="let row">{{ row.amount }}</td>
              </ng-container>
              <ng-container matColumnDef="count">
                <th mat-header-cell *matHeaderCellDef>Count</th>
                <td mat-cell *matCellDef="let row">{{ row.count }}</td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="categoryColumns"></tr>
              <tr mat-row *matRowDef="let row; columns: categoryColumns"></tr>
            </table>

            <!-- Vendor-wise Table -->
            <h3 *ngIf="vendorWiseData.length">Vendor-wise Expenses</h3>
            <table mat-table [dataSource]="vendorWiseData" *ngIf="vendorWiseData.length" class="full-width-table">
              <ng-container matColumnDef="vendor">
                <th mat-header-cell *matHeaderCellDef>Vendor</th>
                <td mat-cell *matCellDef="let row">{{ row.vendor }}</td>
              </ng-container>
              <ng-container matColumnDef="amount">
                <th mat-header-cell *matHeaderCellDef>Amount</th>
                <td mat-cell *matCellDef="let row">{{ row.amount }}</td>
              </ng-container>
              <ng-container matColumnDef="count">
                <th mat-header-cell *matHeaderCellDef>Count</th>
                <td mat-cell *matCellDef="let row">{{ row.count }}</td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="vendorColumns"></tr>
              <tr mat-row *matRowDef="let row; columns: vendorColumns"></tr>
            </table>
          </div>
        </mat-tab>

        <!-- Occupancy Report Tab -->
        <mat-tab label="Occupancy Report">
          <div class="tab-content">
            <div class="card-grid" *ngIf="occupancySummary">
              <mat-card>
                <mat-card-content>
                  <div class="summary-label">Total Units</div>
                  <div class="summary-value">{{ occupancySummary.total }}</div>
                </mat-card-content>
              </mat-card>
              <mat-card>
                <mat-card-content>
                  <div class="summary-label">Self Occupied</div>
                  <div class="summary-value">{{ occupancySummary.selfOccupied }}</div>
                </mat-card-content>
              </mat-card>
              <mat-card>
                <mat-card-content>
                  <div class="summary-label">Rented</div>
                  <div class="summary-value">{{ occupancySummary.rented }}</div>
                </mat-card-content>
              </mat-card>
              <mat-card>
                <mat-card-content>
                  <div class="summary-label">Vacant</div>
                  <div class="summary-value">{{ occupancySummary.vacant }}</div>
                </mat-card-content>
              </mat-card>
              <mat-card>
                <mat-card-content>
                  <div class="summary-label">Total Flats</div>
                  <div class="summary-value">{{ occupancySummary.totalFlats }}</div>
                </mat-card-content>
              </mat-card>
              <mat-card>
                <mat-card-content>
                  <div class="summary-label">Total Shops</div>
                  <div class="summary-value">{{ occupancySummary.totalShops }}</div>
                </mat-card-content>
              </mat-card>
            </div>
          </div>
        </mat-tab>

        <!-- Tenant Compliance Tab -->
        <mat-tab label="Tenant Compliance">
          <div class="tab-content">
            <div class="card-grid" *ngIf="tenantSummary">
              <mat-card>
                <mat-card-content>
                  <div class="summary-label">Total Active</div>
                  <div class="summary-value">{{ tenantSummary.totalActive }}</div>
                </mat-card-content>
              </mat-card>
              <mat-card>
                <mat-card-content>
                  <div class="summary-label">Notice Period</div>
                  <div class="summary-value">{{ tenantSummary.totalNoticePeriod }}</div>
                </mat-card-content>
              </mat-card>
              <mat-card>
                <mat-card-content>
                  <div class="summary-label">Pending NOC</div>
                  <div class="summary-value">{{ tenantSummary.pendingNoc }}</div>
                </mat-card-content>
              </mat-card>
              <mat-card>
                <mat-card-content>
                  <div class="summary-label">Pending Police Verification</div>
                  <div class="summary-value">{{ tenantSummary.pendingPoliceVerification }}</div>
                </mat-card-content>
              </mat-card>
              <mat-card>
                <mat-card-content>
                  <div class="summary-label">Expiring Agreements (30 Days)</div>
                  <div class="summary-value">{{ tenantSummary.expiringAgreements30Days }}</div>
                </mat-card-content>
              </mat-card>
            </div>
          </div>
        </mat-tab>

        <!-- Vendor Contracts Tab -->
        <mat-tab label="Vendor Contracts">
          <div class="tab-content">
            <div class="card-grid" *ngIf="vendorSummary">
              <mat-card>
                <mat-card-content>
                  <div class="summary-label">Total Active</div>
                  <div class="summary-value">{{ vendorSummary.totalActive }}</div>
                </mat-card-content>
              </mat-card>
              <mat-card>
                <mat-card-content>
                  <div class="summary-label">Expiring in 30 Days</div>
                  <div class="summary-value">{{ vendorSummary.expiringIn30Days }}</div>
                </mat-card-content>
              </mat-card>
              <mat-card>
                <mat-card-content>
                  <div class="summary-label">Expired Contracts</div>
                  <div class="summary-value expense">{{ vendorSummary.expiredContracts }}</div>
                </mat-card-content>
              </mat-card>
              <mat-card>
                <mat-card-content>
                  <div class="summary-label">Total Inactive</div>
                  <div class="summary-value">{{ vendorSummary.totalInactive }}</div>
                </mat-card-content>
              </mat-card>
            </div>
          </div>
        </mat-tab>

      </mat-tab-group>
    </div>
  `,
  styles: [`
    .reports-container {
      padding: 20px;
    }

    .tab-content {
      padding: 20px 0;
    }

    .filter-row {
      display: flex;
      gap: 16px;
      align-items: center;
      margin-bottom: 20px;
    }

    .card-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
      gap: 16px;
      margin-bottom: 24px;
    }

    .summary-label {
      font-size: 0.85rem;
      color: #666;
      margin-bottom: 4px;
    }

    .summary-value {
      font-size: 1.5rem;
      font-weight: 500;
    }

    .expense {
      color: #c62828;
    }

    .income {
      color: #2e7d32;
    }

    .full-width-table {
      width: 100%;
      margin-bottom: 24px;
    }

    h3 {
      margin-top: 24px;
      margin-bottom: 12px;
    }
  `]
})
export class ReportsComponent implements OnInit {
  finStartDate: string = '';
  finEndDate: string = '';

  voucherSummary: any = null;
  occupancySummary: any = null;
  tenantSummary: any = null;
  vendorSummary: any = null;

  categoryWiseData: any[] = [];
  vendorWiseData: any[] = [];

  categoryColumns: string[] = ['category', 'amount', 'count'];
  vendorColumns: string[] = ['vendor', 'amount', 'count'];

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.loadVoucherSummary();
    this.loadOccupancySummary();
    this.loadTenantSummary();
    this.loadVendorSummary();
  }

  private loadVoucherSummary(): void {
    this.http.get<any>(`${environment.apiUrl}/vouchers/summary`).subscribe({
      next: (response) => {
        this.voucherSummary = response.data || response;
      }
    });
  }

  private loadOccupancySummary(): void {
    this.http.get<any>(`${environment.apiUrl}/units/summary`).subscribe({
      next: (response) => {
        this.occupancySummary = response.data || response;
      }
    });
  }

  private loadTenantSummary(): void {
    this.http.get<any>(`${environment.apiUrl}/tenants/summary`).subscribe({
      next: (response) => {
        this.tenantSummary = response.data || response;
      }
    });
  }

  private loadVendorSummary(): void {
    this.http.get<any>(`${environment.apiUrl}/vendors/summary`).subscribe({
      next: (response) => {
        this.vendorSummary = response.data || response;
      }
    });
  }

  loadFinancialReport(): void {
    let params = new HttpParams();
    if (this.finStartDate) {
      params = params.set('startDate', this.finStartDate);
    }
    if (this.finEndDate) {
      params = params.set('endDate', this.finEndDate);
    }

    this.http.get<any>(`${environment.apiUrl}/vouchers/reports/category-wise`, { params }).subscribe({
      next: (response) => {
        this.categoryWiseData = response.data || response;
      }
    });

    this.http.get<any>(`${environment.apiUrl}/vouchers/reports/vendor-wise`, { params }).subscribe({
      next: (response) => {
        this.vendorWiseData = response.data || response;
      }
    });
  }
}
