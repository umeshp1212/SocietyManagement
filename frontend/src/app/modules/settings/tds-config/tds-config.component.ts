import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { VendorCategoryService } from '@core/services/vendor-category.service';
import { VendorCategoryModel } from '@core/models/vendor-category.model';
import { environment } from '@env/environment';

interface TdsConfig {
  tdsConfigId: number;
  vendorCategory: string;
  tdsSection: string;
  tdsRate: number;
  thresholdAmount: number;
  description: string;
  isActive: boolean;
  editing?: boolean;
}

@Component({
  selector: 'app-tds-config',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatCardModule, MatTableModule, MatButtonModule,
    MatIconModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatSlideToggleModule, MatSnackBarModule, MatTooltipModule
  ],
  template: `
    <div class="container">
      <div class="page-header">
        <h2>TDS Configuration</h2>
        <button mat-raised-button color="primary" (click)="showAddForm = !showAddForm"
                *ngIf="unconfiguredCategories.length > 0">
          <mat-icon>{{ showAddForm ? 'close' : 'add' }}</mat-icon>
          {{ showAddForm ? 'Cancel' : 'Add TDS Config' }}
        </button>
      </div>

      <p class="subtitle">
        Configure TDS (Tax Deducted at Source) rates per vendor category.
        TDS is auto-calculated when creating vouchers if the amount exceeds the threshold.
      </p>

      <!-- Add New TDS Config -->
      <mat-card *ngIf="showAddForm" class="add-form-card">
        <mat-card-header>
          <mat-card-title>Add TDS Config for Vendor Category</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div class="add-form-row">
            <mat-form-field appearance="outline">
              <mat-label>Vendor Category</mat-label>
              <mat-select [(value)]="newConfig.vendorCategory">
                <mat-option *ngFor="let cat of unconfiguredCategories" [value]="cat.code">
                  {{ cat.name }}
                </mat-option>
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>TDS Section</mat-label>
              <input matInput [(ngModel)]="newConfig.tdsSection" placeholder="194C">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Rate (%)</mat-label>
              <input matInput [(ngModel)]="newConfig.tdsRate" type="number" step="0.5" placeholder="2.0">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Threshold (Rs)</mat-label>
              <input matInput [(ngModel)]="newConfig.thresholdAmount" type="number" placeholder="30000">
            </mat-form-field>
          </div>
          <div class="add-form-row">
            <mat-form-field appearance="outline" class="wide-field">
              <mat-label>Description</mat-label>
              <input matInput [(ngModel)]="newConfig.description" placeholder="TDS description">
            </mat-form-field>
            <button mat-raised-button color="primary" (click)="addTdsConfig()"
                    [disabled]="!newConfig.vendorCategory || !newConfig.tdsRate">
              <mat-icon>save</mat-icon> Save
            </button>
          </div>
        </mat-card-content>
      </mat-card>

      <mat-card>
        <mat-card-content>
          <div class="table-responsive">
            <table mat-table [dataSource]="configs" class="mat-elevation-z0">
              <ng-container matColumnDef="vendorCategory">
                <th mat-header-cell *matHeaderCellDef>Vendor Category</th>
                <td mat-cell *matCellDef="let c">{{ formatCategory(c.vendorCategory) }}</td>
              </ng-container>

              <ng-container matColumnDef="tdsSection">
                <th mat-header-cell *matHeaderCellDef>Section</th>
                <td mat-cell *matCellDef="let c">
                  <span *ngIf="!c.editing">{{ c.tdsSection }}</span>
                  <input *ngIf="c.editing" [(ngModel)]="c.tdsSection"
                         class="inline-input" placeholder="194C">
                </td>
              </ng-container>

              <ng-container matColumnDef="tdsRate">
                <th mat-header-cell *matHeaderCellDef>Rate (%)</th>
                <td mat-cell *matCellDef="let c">
                  <span *ngIf="!c.editing">{{ c.tdsRate }}%</span>
                  <input *ngIf="c.editing" [(ngModel)]="c.tdsRate" type="number" step="0.5"
                         class="inline-input narrow" placeholder="2.0">
                </td>
              </ng-container>

              <ng-container matColumnDef="thresholdAmount">
                <th mat-header-cell *matHeaderCellDef>Threshold (Rs)</th>
                <td mat-cell *matCellDef="let c">
                  <span *ngIf="!c.editing">{{ c.thresholdAmount | number:'1.0-0' }}</span>
                  <input *ngIf="c.editing" [(ngModel)]="c.thresholdAmount" type="number"
                         class="inline-input" placeholder="30000">
                </td>
              </ng-container>

              <ng-container matColumnDef="description">
                <th mat-header-cell *matHeaderCellDef>Description</th>
                <td mat-cell *matCellDef="let c">
                  <span *ngIf="!c.editing" class="desc-text">{{ c.description }}</span>
                  <input *ngIf="c.editing" [(ngModel)]="c.description"
                         class="inline-input wide" placeholder="Description">
                </td>
              </ng-container>

              <ng-container matColumnDef="isActive">
                <th mat-header-cell *matHeaderCellDef>Active</th>
                <td mat-cell *matCellDef="let c">
                  <mat-slide-toggle [(ngModel)]="c.isActive" (change)="saveConfig(c)"
                                    [disabled]="!c.editing" color="primary">
                  </mat-slide-toggle>
                </td>
              </ng-container>

              <ng-container matColumnDef="actions">
                <th mat-header-cell *matHeaderCellDef>Actions</th>
                <td mat-cell *matCellDef="let c">
                  <button mat-icon-button *ngIf="!c.editing" (click)="c.editing = true"
                          matTooltip="Edit" color="primary">
                    <mat-icon>edit</mat-icon>
                  </button>
                  <button mat-icon-button *ngIf="c.editing" (click)="saveConfig(c)"
                          matTooltip="Save" color="primary">
                    <mat-icon>save</mat-icon>
                  </button>
                  <button mat-icon-button *ngIf="c.editing" (click)="cancelEdit(c)"
                          matTooltip="Cancel">
                    <mat-icon>close</mat-icon>
                  </button>
                </td>
              </ng-container>

              <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
              <tr mat-row *matRowDef="let row; columns: displayedColumns;"
                  [class.inactive-row]="!row.isActive"></tr>
            </table>
          </div>
        </mat-card-content>
      </mat-card>

      <!-- Info Card -->
      <mat-card style="margin-top: 16px;">
        <mat-card-header>
          <mat-card-title>How TDS Works</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div class="info-grid">
            <div class="info-item">
              <mat-icon>info</mat-icon>
              <p>When a voucher is created for a vendor, the system checks if TDS is applicable based on the vendor's category.</p>
            </div>
            <div class="info-item">
              <mat-icon>filter_alt</mat-icon>
              <p>TDS is only deducted if the voucher amount exceeds the <strong>threshold amount</strong> (default Rs 30,000 per transaction).</p>
            </div>
            <div class="info-item">
              <mat-icon>calculate</mat-icon>
              <p><strong>Net Payable = Bill Amount - TDS Amount</strong>. The cheque/payment is issued for the net payable amount.</p>
            </div>
            <div class="info-item">
              <mat-icon>toggle_off</mat-icon>
              <p>Set a category to <strong>Inactive</strong> to disable TDS deduction for that type of vendor.</p>
            </div>
          </div>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
    .page-header h2 { margin: 0; }
    .subtitle { color: #666; margin-bottom: 20px; }
    .add-form-card { margin-bottom: 20px; }
    .add-form-row { display: flex; gap: 12px; flex-wrap: wrap; align-items: center; margin-bottom: 8px; }
    .add-form-row mat-form-field { flex: 1; min-width: 140px; }
    .add-form-row .wide-field { flex: 2; }
    .table-responsive { overflow-x: auto; }
    .inline-input { border: 1px solid #ccc; border-radius: 4px; padding: 6px 8px; font-size: 13px; width: 80px; }
    .inline-input.narrow { width: 60px; }
    .inline-input.wide { width: 180px; }
    .desc-text { font-size: 12px; color: #666; }
    .inactive-row { opacity: 0.5; background: #fafafa; }
    .info-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; padding: 8px 0; }
    .info-item { display: flex; gap: 10px; align-items: flex-start; }
    .info-item mat-icon { color: #1976d2; margin-top: 2px; }
    .info-item p { margin: 0; font-size: 13px; color: #555; }

    @media (max-width: 768px) {
      .page-header { flex-direction: column; align-items: flex-start; gap: 12px; }
      .add-form-row { flex-direction: column; }
      .info-grid { grid-template-columns: 1fr; }
      .inline-input.wide { width: 120px; }
    }
  `]
})
export class TdsConfigComponent implements OnInit {
  configs: TdsConfig[] = [];
  originalConfigs: Map<number, TdsConfig> = new Map();
  displayedColumns = ['vendorCategory', 'tdsSection', 'tdsRate', 'thresholdAmount', 'description', 'isActive', 'actions'];

  vendorCategories: VendorCategoryModel[] = [];
  unconfiguredCategories: VendorCategoryModel[] = [];
  showAddForm = false;
  newConfig = { vendorCategory: '', tdsSection: '194C', tdsRate: 2.0, thresholdAmount: 30000, description: '' };

  private apiUrl = environment.apiUrl;

  constructor(
    private http: HttpClient,
    private snackBar: MatSnackBar,
    private vendorCategoryService: VendorCategoryService
  ) {}

  ngOnInit(): void {
    this.loadConfigs();
    this.loadVendorCategories();
  }

  loadVendorCategories(): void {
    this.vendorCategoryService.getActiveCategories().subscribe(res => {
      if (res.success) {
        this.vendorCategories = res.data;
        this.updateUnconfiguredCategories();
      }
    });
  }

  updateUnconfiguredCategories(): void {
    const configuredCodes = this.configs.map(c => c.vendorCategory);
    this.unconfiguredCategories = this.vendorCategories.filter(
      cat => !configuredCodes.includes(cat.code)
    );
  }

  loadConfigs(): void {
    this.http.get<any>(`${this.apiUrl}/tds-config`).subscribe(res => {
      if (res.success) {
        this.configs = res.data.map((c: any) => ({ ...c, editing: false }));
        // Store originals for cancel
        this.configs.forEach(c => this.originalConfigs.set(c.tdsConfigId, { ...c }));
        this.updateUnconfiguredCategories();
      }
    });
  }

  addTdsConfig(): void {
    if (!this.newConfig.vendorCategory || !this.newConfig.tdsRate) return;

    this.http.post<any>(`${this.apiUrl}/tds-config`, {
      vendorCategory: this.newConfig.vendorCategory,
      tdsSection: this.newConfig.tdsSection,
      tdsRate: this.newConfig.tdsRate,
      thresholdAmount: this.newConfig.thresholdAmount,
      description: this.newConfig.description,
      isActive: true
    }).subscribe({
      next: (res) => {
        if (res.success) {
          this.snackBar.open(`TDS config added for ${this.formatCategory(this.newConfig.vendorCategory)}`, 'Close', { duration: 3000 });
          this.showAddForm = false;
          this.newConfig = { vendorCategory: '', tdsSection: '194C', tdsRate: 2.0, thresholdAmount: 30000, description: '' };
          this.loadConfigs();
        }
      },
      error: (err) => {
        this.snackBar.open(err.error?.message || 'Failed to add TDS config', 'Close', { duration: 5000 });
      }
    });
  }

  saveConfig(config: TdsConfig): void {
    this.http.put<any>(`${this.apiUrl}/tds-config/${config.tdsConfigId}`, {
      tdsSection: config.tdsSection,
      tdsRate: config.tdsRate,
      thresholdAmount: config.thresholdAmount,
      description: config.description,
      isActive: config.isActive
    }).subscribe({
      next: (res) => {
        if (res.success) {
          config.editing = false;
          this.originalConfigs.set(config.tdsConfigId, { ...config });
          this.snackBar.open(`TDS config updated for ${this.formatCategory(config.vendorCategory)}`, 'Close', { duration: 3000 });
        }
      },
      error: (err) => {
        this.snackBar.open(err.error?.message || 'Failed to update', 'Close', { duration: 5000 });
      }
    });
  }

  cancelEdit(config: TdsConfig): void {
    const original = this.originalConfigs.get(config.tdsConfigId);
    if (original) {
      Object.assign(config, original);
    }
    config.editing = false;
  }

  formatCategory(category: string): string {
    return category.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
  }
}
