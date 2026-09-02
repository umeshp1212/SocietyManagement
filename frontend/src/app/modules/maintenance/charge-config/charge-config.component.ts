import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatDialogModule } from '@angular/material/dialog';
import { MaintenanceService } from '@core/services/maintenance.service';
import { AuthService } from '@core/services/auth.service';

@Component({
  selector: 'app-charge-config',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, MatCardModule, MatButtonModule,
    MatIconModule, MatTableModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatSlideToggleModule, MatDialogModule],
  template: `
    <div class="container">
      <div class="page-header">
        <h2>Maintenance Charge Configuration</h2>
        <div style="display: flex; gap: 8px;">
          <a mat-raised-button color="accent" routerLink="/maintenance/water-charge-config">
            <mat-icon>water_drop</mat-icon> Water Charges
          </a>
          <button mat-raised-button color="primary" (click)="showAddForm = !showAddForm" *ngIf="canManage()">
            <mat-icon>{{ showAddForm ? 'close' : 'add' }}</mat-icon>
            {{ showAddForm ? 'Cancel' : 'Add Charge Type' }}
          </button>
        </div>
      </div>

      <!-- Add/Edit Form -->
      <mat-card class="form-card" *ngIf="showAddForm">
        <mat-card-header>
          <mat-card-title>{{ editingId ? 'Edit' : 'Add New' }} Charge Type</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form class="charge-form" (ngSubmit)="saveChargeConfig()">
            <mat-form-field appearance="outline">
              <mat-label>Charge Code</mat-label>
              <input matInput [(ngModel)]="form.chargeCode" name="chargeCode" required
                     [disabled]="!!editingId" placeholder="e.g., SINKING_FUND">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Charge Name</mat-label>
              <input matInput [(ngModel)]="form.chargeName" name="chargeName" required
                     placeholder="e.g., Sinking Fund">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Description</mat-label>
              <input matInput [(ngModel)]="form.description" name="description">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Calculation Type</mat-label>
              <mat-select [(ngModel)]="form.calculationType" name="calculationType" required>
                <mat-option value="FLAT">Flat Amount</mat-option>
                <mat-option value="AREA_BASED">Area Based (per sq.ft)</mat-option>
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline" *ngIf="form.calculationType === 'AREA_BASED'">
              <mat-label>Rate Per Sq.Ft (Rs.)</mat-label>
              <input matInput type="number" step="0.01" [(ngModel)]="form.ratePerSqft" name="ratePerSqft">
            </mat-form-field>
            <mat-form-field appearance="outline" *ngIf="form.calculationType === 'FLAT'">
              <mat-label>Flat Amount (Rs.)</mat-label>
              <input matInput type="number" step="0.01" [(ngModel)]="form.flatAmount" name="flatAmount">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Applicable To</mat-label>
              <mat-select [(ngModel)]="form.applicableTo" name="applicableTo">
                <mat-option value="ALL">All Units</mat-option>
                <mat-option value="PARKING">Units with Parking</mat-option>
                <mat-option value="TWO_WHEELER">Two Wheeler Parking</mat-option>
                <mat-option value="FOUR_WHEELER">Four Wheeler Parking</mat-option>
                <mat-option value="RENTED">Rented Units Only</mat-option>
                <mat-option value="OWNER_OCCUPIED">Owner Occupied Only</mat-option>
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Display Order</mat-label>
              <input matInput type="number" [(ngModel)]="form.displayOrder" name="displayOrder">
            </mat-form-field>
            <div class="form-actions">
              <button mat-raised-button color="primary" type="submit">
                <mat-icon>save</mat-icon> {{ editingId ? 'Update' : 'Save' }}
              </button>
              <button mat-button type="button" (click)="cancelEdit()">Cancel</button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>

      <!-- Charges List -->
      <mat-card>
        <mat-card-content>
          <table mat-table [dataSource]="chargeConfigs" class="mat-elevation-z1 full-width">
            <ng-container matColumnDef="displayOrder">
              <th mat-header-cell *matHeaderCellDef>#</th>
              <td mat-cell *matCellDef="let c">{{ c.displayOrder }}</td>
            </ng-container>
            <ng-container matColumnDef="chargeName">
              <th mat-header-cell *matHeaderCellDef>Charge Name</th>
              <td mat-cell *matCellDef="let c">{{ c.chargeName }}</td>
            </ng-container>
            <ng-container matColumnDef="chargeCode">
              <th mat-header-cell *matHeaderCellDef>Code</th>
              <td mat-cell *matCellDef="let c">{{ c.chargeCode }}</td>
            </ng-container>
            <ng-container matColumnDef="calculationType">
              <th mat-header-cell *matHeaderCellDef>Type</th>
              <td mat-cell *matCellDef="let c">{{ c.calculationType === 'AREA_BASED' ? 'Area Based' : 'Flat' }}</td>
            </ng-container>
            <ng-container matColumnDef="rate">
              <th mat-header-cell *matHeaderCellDef>Rate/Amount</th>
              <td mat-cell *matCellDef="let c">
                <span *ngIf="c.calculationType === 'AREA_BASED'">Rs. {{ c.ratePerSqft }} /sq.ft</span>
                <span *ngIf="c.calculationType === 'FLAT'">Rs. {{ c.flatAmount }}</span>
              </td>
            </ng-container>
            <ng-container matColumnDef="applicableTo">
              <th mat-header-cell *matHeaderCellDef>Applicable To</th>
              <td mat-cell *matCellDef="let c">{{ formatApplicableTo(c.applicableTo) }}</td>
            </ng-container>
            <ng-container matColumnDef="isActive">
              <th mat-header-cell *matHeaderCellDef>Active</th>
              <td mat-cell *matCellDef="let c">
                <span class="status-badge" [ngClass]="c.isActive ? 'active' : 'inactive'">
                  {{ c.isActive ? 'Active' : 'Inactive' }}
                </span>
              </td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef>Actions</th>
              <td mat-cell *matCellDef="let c">
                <button mat-icon-button color="primary" (click)="editChargeConfig(c)" *ngIf="canManage()">
                  <mat-icon>edit</mat-icon>
                </button>
                <button mat-icon-button color="warn" (click)="deleteChargeConfig(c.chargeConfigId)"
                        *ngIf="c.isActive && canManage()">
                  <mat-icon>toggle_off</mat-icon>
                </button>
                <span *ngIf="!canManage()" class="view-only">—</span>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
          </table>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .container { padding: 24px; }
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .form-card { margin-bottom: 24px; }
    .charge-form { display: grid; grid-template-columns: 1fr 1fr; gap: 8px 16px; }
    .charge-form mat-form-field { width: 100%; }
    .form-actions { grid-column: 1 / -1; display: flex; gap: 12px; }
    .full-width { width: 100%; }
    .status-badge { padding: 4px 8px; border-radius: 12px; font-size: 12px; font-weight: 500; }
    .status-badge.active { background: #e8f5e9; color: #2e7d32; }
    .status-badge.inactive { background: #fbe9e7; color: #c62828; }
  `]
})
export class ChargeConfigComponent implements OnInit {
  chargeConfigs: any[] = [];
  showAddForm = false;
  editingId: number | null = null;
  displayedColumns = ['displayOrder', 'chargeName', 'chargeCode', 'calculationType', 'rate', 'applicableTo', 'isActive', 'actions'];

  form = {
    chargeCode: '',
    chargeName: '',
    description: '',
    calculationType: 'FLAT',
    ratePerSqft: null as number | null,
    flatAmount: null as number | null,
    applicableTo: 'ALL',
    displayOrder: 0,
    isActive: true
  };

  constructor(private maintenanceService: MaintenanceService, private authService: AuthService) {}

  canManage(): boolean {
    return this.authService.hasAnyRole(['SUPER_ADMIN', 'SECRETARY', 'TREASURER']);
  }

  ngOnInit(): void {
    this.loadConfigs();
  }

  loadConfigs(): void {
    this.maintenanceService.getAllChargeConfigs().subscribe(res => {
      if (res.success) {
        this.chargeConfigs = res.data || [];
      }
    });
  }

  saveChargeConfig(): void {
    const payload = { ...this.form };
    if (this.editingId) {
      this.maintenanceService.updateChargeConfig(this.editingId, payload).subscribe(res => {
        if (res.success) {
          this.loadConfigs();
          this.cancelEdit();
        }
      });
    } else {
      this.maintenanceService.createChargeConfig(payload).subscribe(res => {
        if (res.success) {
          this.loadConfigs();
          this.cancelEdit();
        }
      });
    }
  }

  editChargeConfig(config: any): void {
    this.editingId = config.chargeConfigId;
    this.form = {
      chargeCode: config.chargeCode,
      chargeName: config.chargeName,
      description: config.description || '',
      calculationType: config.calculationType,
      ratePerSqft: config.ratePerSqft,
      flatAmount: config.flatAmount,
      applicableTo: config.applicableTo || 'ALL',
      displayOrder: config.displayOrder || 0,
      isActive: config.isActive
    };
    this.showAddForm = true;
  }

  deleteChargeConfig(id: number): void {
    if (confirm('Are you sure you want to deactivate this charge type?')) {
      this.maintenanceService.deleteChargeConfig(id).subscribe(res => {
        if (res.success) {
          this.loadConfigs();
        }
      });
    }
  }

  cancelEdit(): void {
    this.showAddForm = false;
    this.editingId = null;
    this.form = {
      chargeCode: '', chargeName: '', description: '', calculationType: 'FLAT',
      ratePerSqft: null, flatAmount: null, applicableTo: 'ALL', displayOrder: 0, isActive: true
    };
  }

  formatApplicableTo(value: string): string {
    const map: Record<string, string> = {
      'ALL': 'All Units',
      'PARKING': 'Parking',
      'TWO_WHEELER': 'Two Wheeler',
      'FOUR_WHEELER': 'Four Wheeler',
      'RENTED': 'Rented Only',
      'OWNER_OCCUPIED': 'Owner Occupied'
    };
    return map[value] || value;
  }
}
