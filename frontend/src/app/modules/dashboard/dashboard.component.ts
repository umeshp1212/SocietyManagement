import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { OwnerService } from '@core/services/owner.service';
import { VendorService } from '@core/services/vendor.service';
import { TenantService } from '@core/services/tenant.service';
import { VoucherService } from '@core/services/voucher.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule, MatCardModule, MatIconModule, MatButtonModule],
  template: `
    <div class="container">
      <h2>Dashboard</h2>

      <div class="card-grid">
        <mat-card class="summary-card">
          <mat-card-content>
            <mat-icon color="primary">apartment</mat-icon>
            <div class="summary-value">{{ unitSummary?.total || 0 }}</div>
            <div class="summary-label">Total Units</div>
          </mat-card-content>
        </mat-card>

        <mat-card class="summary-card">
          <mat-card-content>
            <mat-icon color="accent">people</mat-icon>
            <div class="summary-value">{{ unitSummary?.selfOccupied || 0 }}</div>
            <div class="summary-label">Self Occupied</div>
          </mat-card-content>
        </mat-card>

        <mat-card class="summary-card">
          <mat-card-content>
            <mat-icon color="warn">person_add</mat-icon>
            <div class="summary-value">{{ unitSummary?.rented || 0 }}</div>
            <div class="summary-label">Rented</div>
          </mat-card-content>
        </mat-card>

        <mat-card class="summary-card">
          <mat-card-content>
            <mat-icon>home_work</mat-icon>
            <div class="summary-value">{{ unitSummary?.vacant || 0 }}</div>
            <div class="summary-label">Vacant</div>
          </mat-card-content>
        </mat-card>
      </div>

      <div class="card-grid">
        <mat-card class="summary-card">
          <mat-card-content>
            <mat-icon color="primary">store</mat-icon>
            <div class="summary-value">{{ vendorSummary?.totalActive || 0 }}</div>
            <div class="summary-label">Active Vendors</div>
          </mat-card-content>
        </mat-card>

        <mat-card class="summary-card">
          <mat-card-content>
            <mat-icon color="warn">warning</mat-icon>
            <div class="summary-value">{{ vendorSummary?.expiringIn30Days || 0 }}</div>
            <div class="summary-label">Contracts Expiring (30d)</div>
          </mat-card-content>
        </mat-card>

        <mat-card class="summary-card">
          <mat-card-content>
            <mat-icon color="primary">pending_actions</mat-icon>
            <div class="summary-value">{{ tenantSummary?.pendingNoc || 0 }}</div>
            <div class="summary-label">Pending NOC</div>
          </mat-card-content>
        </mat-card>

        <mat-card class="summary-card">
          <mat-card-content>
            <mat-icon color="accent">receipt_long</mat-icon>
            <div class="summary-value">{{ voucherSummary?.draftCount || 0 }}</div>
            <div class="summary-label">Draft Vouchers</div>
          </mat-card-content>
        </mat-card>
      </div>

      <div class="quick-actions">
        <h3>Quick Actions</h3>
        <div class="card-grid">
          <mat-card>
            <mat-card-content>
              <a mat-raised-button color="primary" routerLink="/owners/add">
                <mat-icon>person_add</mat-icon> Add Owner
              </a>
            </mat-card-content>
          </mat-card>
          <mat-card>
            <mat-card-content>
              <a mat-raised-button color="primary" routerLink="/vendors/add">
                <mat-icon>store</mat-icon> Add Vendor
              </a>
            </mat-card-content>
          </mat-card>
          <mat-card>
            <mat-card-content>
              <a mat-raised-button color="primary" routerLink="/tenants/register">
                <mat-icon>person_add</mat-icon> Register Tenant
              </a>
            </mat-card-content>
          </mat-card>
          <mat-card>
            <mat-card-content>
              <a mat-raised-button color="primary" routerLink="/vouchers/create">
                <mat-icon>receipt_long</mat-icon> Create Voucher
              </a>
            </mat-card-content>
          </mat-card>
        </div>
      </div>
    </div>
  `,
  styles: [`
    mat-icon { font-size: 36px; height: 36px; width: 36px; margin-bottom: 8px; }
    mat-card-content { display: flex; flex-direction: column; align-items: center; padding: 16px; }
    .quick-actions mat-card-content { padding: 12px; }
    .quick-actions a { width: 100%; }
  `]
})
export class DashboardComponent implements OnInit {
  unitSummary: any = {};
  vendorSummary: any = {};
  tenantSummary: any = {};
  voucherSummary: any = {};

  constructor(
    private ownerService: OwnerService,
    private vendorService: VendorService,
    private tenantService: TenantService,
    private voucherService: VoucherService
  ) {}

  ngOnInit(): void {
    this.ownerService.getOccupancySummary().subscribe(res => {
      if (res.success) this.unitSummary = res.data;
    });
    this.vendorService.getVendorSummary().subscribe(res => {
      if (res.success) this.vendorSummary = res.data;
    });
    this.tenantService.getTenantSummary().subscribe(res => {
      if (res.success) this.tenantSummary = res.data;
    });
    this.voucherService.getVoucherSummary().subscribe(res => {
      if (res.success) this.voucherSummary = res.data;
    });
  }
}
