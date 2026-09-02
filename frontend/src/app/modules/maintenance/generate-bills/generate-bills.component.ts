import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MaintenanceService } from '@core/services/maintenance.service';
import { AuthService } from '@core/services/auth.service';

@Component({
  selector: 'app-generate-bills',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, MatCardModule, MatButtonModule,
    MatIconModule, MatFormFieldModule, MatSelectModule, MatInputModule, MatCheckboxModule],
  template: `
    <div class="container">
      <div class="page-header">
        <h2>Generate Maintenance Bills</h2>
        <a mat-button routerLink="/maintenance">
          <mat-icon>arrow_back</mat-icon> Back to Bills
        </a>
      </div>

      <mat-card *ngIf="!canManage()" class="no-access-card">
        <mat-card-content>
          <mat-icon color="warn">lock</mat-icon>
          <p>You don't have permission to generate maintenance bills.</p>
        </mat-card-content>
      </mat-card>

      <mat-card *ngIf="canManage()">
        <mat-card-content>
          <form class="generate-form" (ngSubmit)="generate()">
            <mat-form-field appearance="outline">
              <mat-label>Month</mat-label>
              <mat-select [(ngModel)]="selectedMonth" name="month" required>
                <mat-option *ngFor="let m of months; let i = index" [value]="i + 1">{{ m }}</mat-option>
              </mat-select>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Year</mat-label>
              <input matInput type="number" [(ngModel)]="selectedYear" name="year" required>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Due Day of Month</mat-label>
              <input matInput type="number" [(ngModel)]="dueDay" name="dueDay" min="1" max="28">
            </mat-form-field>

            <mat-checkbox [(ngModel)]="regenerate" name="regenerate" color="warn">
              Regenerate existing unpaid bills (recalculate with current charges)
            </mat-checkbox>
            <p class="hint-text" *ngIf="regenerate">
              This will delete all UNPAID bills for the selected month and regenerate them
              using the latest unit charges and charge configuration. Bills that are already
              PAID or PARTIALLY PAID will not be affected.
            </p>

            <button mat-raised-button color="primary" type="submit" [disabled]="loading">
              <mat-icon>receipt_long</mat-icon>
              {{ regenerate ? 'Regenerate Bills' : 'Generate Bills' }}
            </button>
          </form>
        </mat-card-content>
      </mat-card>

      <mat-card *ngIf="result" class="result-card">
        <mat-card-content>
          <div class="result-icon">
            <mat-icon color="primary">check_circle</mat-icon>
          </div>
          <h3>Bills Generated Successfully</h3>
          <p *ngIf="result.billsGenerated > 0">New Bills Generated: <strong>{{ result.billsGenerated }}</strong></p>
          <p *ngIf="result.billsRegenerated > 0">Bills Regenerated: <strong>{{ result.billsRegenerated }}</strong></p>
          <p *ngIf="result.message">{{ result.message }}</p>
          <a mat-raised-button color="accent" routerLink="/maintenance">View Bills</a>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .container { padding: 24px; max-width: 600px; }
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .generate-form { display: flex; flex-direction: column; gap: 12px; }
    .generate-form mat-form-field { width: 100%; }
    .result-card { margin-top: 24px; text-align: center; }
    .result-icon mat-icon { font-size: 48px; width: 48px; height: 48px; }
    .hint-text { font-size: 12px; color: #e65100; background: #fff3e0; padding: 8px 12px; border-radius: 4px; margin: 0; }
  `]
})
export class GenerateBillsComponent {
  selectedMonth: number;
  selectedYear: number;
  dueDay = 10;
  regenerate = false;
  loading = false;
  result: any = null;
  months = ['January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December'];

  constructor(private maintenanceService: MaintenanceService, private authService: AuthService) {
    const now = new Date();
    this.selectedMonth = now.getMonth() + 1;
    this.selectedYear = now.getFullYear();
  }

  canManage(): boolean {
    return this.authService.hasPermission('MAINTENANCE_CREATE');
  }

  generate(): void {
    if (!this.canManage()) { return; }
    this.loading = true;
    this.result = null;
    this.maintenanceService.generateBills(this.selectedMonth, this.selectedYear, this.dueDay, this.regenerate)
      .subscribe({
        next: res => {
          if (res.success) {
            this.result = res.data;
          }
          this.loading = false;
        },
        error: () => { this.loading = false; }
      });
  }
}
