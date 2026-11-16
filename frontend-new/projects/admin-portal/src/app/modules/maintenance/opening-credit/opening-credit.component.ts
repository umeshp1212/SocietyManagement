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
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { environment } from '@env/environment';
import { AuthService } from '@core/services/auth.service';
import { SearchableSelectComponent } from '@shared/components/searchable-select';
import { BackButtonComponent } from '@shared/components/back-button';

interface UnitOption {
  unitId: number;
  unitNumber: string;
  primaryOwnerName?: string;
  allOwnerNames?: string;
}

/**
 * Seed OPENING advance credits — money owners prepaid BEFORE go-live. Each entry creates an
 * available credit that the next generated bill auto-applies (carrying the remainder forward).
 * In-system overpayments create credits automatically; this screen is only for pre-go-live prepaid.
 */
@Component({
  selector: 'app-opening-credit',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatCardModule, MatTableModule, MatButtonModule,
    MatIconModule, MatFormFieldModule, MatInputModule, MatSnackBarModule,
    MatChipsModule, MatTooltipModule, SearchableSelectComponent, BackButtonComponent
  ],
  template: `
    <div class="container">
      <app-back-button link="/maintenance" label="Back to Bills"></app-back-button>
      <div class="page-header">
        <h2>Opening Credits (Prepaid Before Go-Live)</h2>
        <button mat-raised-button color="primary" (click)="showAddForm = !showAddForm" *ngIf="canManage()">
          <mat-icon>{{ showAddForm ? 'close' : 'add' }}</mat-icon>
          {{ showAddForm ? 'Cancel' : 'Add Opening Credit' }}
        </button>
      </div>

      <p class="subtitle">
        Enter advance/prepaid amounts owners paid before the system went live. Each credit is
        applied automatically to that unit's next generated bill ("Less: Advance Credit Applied"),
        and any remaining balance carries forward to following bills.
      </p>

      <!-- Add Form -->
      <mat-card *ngIf="showAddForm && canManage()" class="add-card">
        <mat-card-content>
          <div class="form-grid">
            <app-searchable-select
              label="Select Unit *"
              [options]="units" valueKey="unitId" [labelWith]="unitLabel"
              [(ngModel)]="form.unitId" [required]="true">
            </app-searchable-select>

            <mat-form-field appearance="outline">
              <mat-label>Credit Amount (Rs) *</mat-label>
              <input matInput type="number" min="0" step="0.01" [(ngModel)]="form.amount"
                     placeholder="e.g. 3217">
              <mat-hint>Prepaid amount available to this unit</mat-hint>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Received Date</mat-label>
              <input matInput type="date" [(ngModel)]="form.receivedDate">
              <mat-hint>When the owner paid it (optional)</mat-hint>
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Remarks</mat-label>
              <input matInput [(ngModel)]="form.remarks" placeholder="e.g. Advance paid in Oct 2026">
            </mat-form-field>
          </div>
          <div class="form-actions">
            <button mat-raised-button color="primary" (click)="save()"
                    [disabled]="!form.unitId || !form.amount || form.amount <= 0 || saving">
              <mat-icon>save</mat-icon> {{ saving ? 'Saving...' : 'Save Opening Credit' }}
            </button>
          </div>
        </mat-card-content>
      </mat-card>

      <!-- Existing credits for a selected unit -->
      <mat-card class="lookup-card">
        <mat-card-content>
          <div class="lookup-row">
            <app-searchable-select
              label="View a unit's credits"
              [options]="units" valueKey="unitId" [labelWith]="unitLabel"
              [(ngModel)]="lookupUnitId">
            </app-searchable-select>
            <button mat-stroked-button color="primary" (click)="loadCredits()" [disabled]="!lookupUnitId">
              <mat-icon>search</mat-icon> Show Credits
            </button>
            <span class="available" *ngIf="lookupUnitId && loaded">
              Available: <strong>{{ availableTotal | currency:'INR' }}</strong>
            </span>
          </div>

          <table mat-table [dataSource]="credits" class="mat-elevation-z0" *ngIf="credits.length > 0">
            <ng-container matColumnDef="receivedDate">
              <th mat-header-cell *matHeaderCellDef>Date</th>
              <td mat-cell *matCellDef="let c">{{ c.receivedDate }}</td>
            </ng-container>
            <ng-container matColumnDef="amount">
              <th mat-header-cell *matHeaderCellDef>Amount</th>
              <td mat-cell *matCellDef="let c">{{ c.amount | currency:'INR' }}</td>
            </ng-container>
            <ng-container matColumnDef="appliedAmount">
              <th mat-header-cell *matHeaderCellDef>Applied</th>
              <td mat-cell *matCellDef="let c">{{ c.appliedAmount | currency:'INR' }}</td>
            </ng-container>
            <ng-container matColumnDef="balanceAmount">
              <th mat-header-cell *matHeaderCellDef>Remaining</th>
              <td mat-cell *matCellDef="let c">{{ c.balanceAmount | currency:'INR' }}</td>
            </ng-container>
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>Status</th>
              <td mat-cell *matCellDef="let c">{{ c.status }}</td>
            </ng-container>
            <ng-container matColumnDef="remarks">
              <th mat-header-cell *matHeaderCellDef>Remarks</th>
              <td mat-cell *matCellDef="let c">{{ c.remarks }}</td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="cols"></tr>
            <tr mat-row *matRowDef="let row; columns: cols;"></tr>
          </table>

          <div *ngIf="loaded && credits.length === 0" class="no-data">
            No credits for this unit.
          </div>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .container { padding: 24px; max-width: 1100px; margin: 0 auto; }
    .page-header { display: flex; justify-content: space-between; align-items: center; }
    .subtitle { color: #666; font-size: 14px; margin: 8px 0 16px; }
    .add-card, .lookup-card { margin-bottom: 20px; }
    .form-grid { display: flex; flex-wrap: wrap; gap: 16px; }
    .form-grid mat-form-field, .form-grid app-searchable-select { flex: 1; min-width: 220px; }
    .full-width { flex-basis: 100% !important; }
    .form-actions { display: flex; justify-content: flex-end; margin-top: 8px; }
    .lookup-row { display: flex; gap: 16px; align-items: center; flex-wrap: wrap; margin-bottom: 12px; }
    .lookup-row app-searchable-select { min-width: 260px; }
    .available { margin-left: auto; font-size: 14px; color: #333; }
    table { width: 100%; }
    .no-data { color: #888; padding: 16px; text-align: center; }
  `]
})
export class OpeningCreditComponent implements OnInit {
  private apiUrl = environment.apiUrl;

  units: UnitOption[] = [];
  showAddForm = false;
  saving = false;

  form: { unitId: number | null; amount: number | null; receivedDate: string; remarks: string } = {
    unitId: null, amount: null, receivedDate: '', remarks: ''
  };

  lookupUnitId: number | null = null;
  credits: any[] = [];
  availableTotal = 0;
  loaded = false;
  cols = ['receivedDate', 'amount', 'appliedAmount', 'balanceAmount', 'status', 'remarks'];

  constructor(private http: HttpClient, private snackBar: MatSnackBar, private authService: AuthService) {}

  canManage(): boolean {
    return this.authService.hasPermission('MAINTENANCE_OPENING_BALANCE')
        || this.authService.hasRole('SUPER_ADMIN');
  }

  unitLabel = (u: UnitOption) =>
    `${u.unitNumber}${u.primaryOwnerName ? ' - ' + u.primaryOwnerName : (u.allOwnerNames ? ' - ' + u.allOwnerNames : '')}`;

  ngOnInit(): void {
    this.http.get<any>(`${this.apiUrl}/units?page=0&size=500`).subscribe(res => {
      if (res.success) this.units = res.data.content;
    });
  }

  save(): void {
    if (!this.form.unitId || !this.form.amount || this.form.amount <= 0) return;
    this.saving = true;
    this.http.post<any>(`${this.apiUrl}/maintenance/advance-credit/opening`, {
      unitId: this.form.unitId,
      amount: this.form.amount,
      receivedDate: this.form.receivedDate || null,
      remarks: this.form.remarks || null
    }).subscribe({
      next: (res) => {
        this.saving = false;
        if (res.success) {
          this.snackBar.open('Opening credit saved', 'Close', { duration: 3000 });
          const savedUnit = this.form.unitId;
          this.form = { unitId: null, amount: null, receivedDate: '', remarks: '' };
          this.showAddForm = false;
          // Show the just-saved unit's credits for confirmation.
          this.lookupUnitId = savedUnit;
          this.loadCredits();
        }
      },
      error: (err) => {
        this.saving = false;
        this.snackBar.open(err.error?.message || 'Failed to save opening credit', 'Close', { duration: 5000 });
      }
    });
  }

  loadCredits(): void {
    if (!this.lookupUnitId) return;
    this.http.get<any>(`${this.apiUrl}/maintenance/advance-credit/unit/${this.lookupUnitId}`).subscribe({
      next: (res) => {
        if (res.success) {
          this.credits = res.data?.entries || [];
          this.availableTotal = Number(res.data?.availableTotal || 0);
          this.loaded = true;
        }
      },
      error: (err) => this.snackBar.open(err.error?.message || 'Failed to load credits', 'Close', { duration: 4000 })
    });
  }
}
