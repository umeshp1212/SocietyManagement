import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDividerModule } from '@angular/material/divider';
import { OwnerService } from '@core/services/owner.service';
import { Owner, Unit, UnitOwner } from '@core/models/owner.model';

@Component({
  selector: 'app-unit-owners',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterModule, MatCardModule, MatButtonModule,
    MatIconModule, MatTableModule, MatFormFieldModule, MatSelectModule,
    MatInputModule, MatCheckboxModule, MatSnackBarModule, MatDividerModule
  ],
  template: `
    <div class="container" *ngIf="unit">
      <div class="page-header">
        <h2>Manage Owners - Unit {{ unit.unitNumber }}</h2>
        <a mat-button routerLink="/units">Back to Units</a>
      </div>

      <!-- Unit Info -->
      <mat-card>
        <mat-card-content>
          <div class="unit-info">
            <span><strong>Unit:</strong> {{ unit.unitNumber }}</span>
            <span><strong>Type:</strong> {{ unit.unitType }}</span>
            <span><strong>Wing:</strong> {{ unit.wing || '-' }}</span>
            <span><strong>Floor:</strong> {{ unit.floor || '-' }}</span>
            <span><strong>Occupancy:</strong> {{ unit.occupancyStatus }}</span>
          </div>
        </mat-card-content>
      </mat-card>

      <!-- Current Owners -->
      <mat-card style="margin-top: 16px">
        <mat-card-header>
          <mat-card-title>Current Owners ({{ owners.length }}/4)</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div *ngIf="owners.length === 0" class="no-owners">
            <mat-icon>person_off</mat-icon>
            <p>No owners assigned to this unit</p>
          </div>

          <table mat-table [dataSource]="owners" *ngIf="owners.length > 0" class="mat-elevation-z1">
            <ng-container matColumnDef="ownerName">
              <th mat-header-cell *matHeaderCellDef>Owner Name</th>
              <td mat-cell *matCellDef="let o">{{ o.ownerName }}</td>
            </ng-container>
            <ng-container matColumnDef="ownerContact">
              <th mat-header-cell *matHeaderCellDef>Contact</th>
              <td mat-cell *matCellDef="let o">{{ o.ownerContact }}</td>
            </ng-container>
            <ng-container matColumnDef="isPrimary">
              <th mat-header-cell *matHeaderCellDef>Primary</th>
              <td mat-cell *matCellDef="let o">
                <mat-icon *ngIf="o.isPrimary" color="primary">star</mat-icon>
                <span *ngIf="!o.isPrimary">-</span>
              </td>
            </ng-container>
            <ng-container matColumnDef="ownershipPercentage">
              <th mat-header-cell *matHeaderCellDef>Ownership %</th>
              <td mat-cell *matCellDef="let o">{{ o.ownershipPercentage }}%</td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef>Actions</th>
              <td mat-cell *matCellDef="let o">
                <button mat-icon-button color="warn" (click)="removeOwner(o.ownerId)"
                        matTooltip="Remove owner from unit">
                  <mat-icon>person_remove</mat-icon>
                </button>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
          </table>
        </mat-card-content>
      </mat-card>

      <!-- Add Co-Owner Form -->
      <mat-card style="margin-top: 16px" *ngIf="owners.length < 4">
        <mat-card-header>
          <mat-card-title>Add Owner / Co-Owner</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div class="add-owner-form">
            <mat-form-field appearance="outline" class="owner-select">
              <mat-label>Select Owner *</mat-label>
              <mat-select [(ngModel)]="newOwnerId">
                <mat-option *ngFor="let owner of availableOwners" [value]="owner.ownerId">
                  {{ owner.fullName }} ({{ owner.contactNumber }})
                </mat-option>
              </mat-select>
            </mat-form-field>

            <mat-form-field appearance="outline" style="width: 150px;">
              <mat-label>Ownership %</mat-label>
              <input matInput type="number" [(ngModel)]="newPercentage" min="1" max="100">
            </mat-form-field>

            <mat-checkbox [(ngModel)]="newIsPrimary">Primary Owner</mat-checkbox>

            <button mat-raised-button color="primary" (click)="addOwner()"
                    [disabled]="!newOwnerId || !newPercentage">
              <mat-icon>person_add</mat-icon> Add
            </button>
          </div>

          <mat-divider style="margin: 16px 0;"></mat-divider>
          <p class="hint">
            Don't see the owner in the list?
            <a routerLink="/owners/add">Add a new owner first</a>, then come back here to link them.
          </p>
        </mat-card-content>
      </mat-card>

      <mat-card style="margin-top: 16px" *ngIf="owners.length >= 4">
        <mat-card-content>
          <p style="color: #c62828; text-align: center;">
            <mat-icon>warning</mat-icon> Maximum 4 owners reached for this unit.
            Remove an owner to add a new one.
          </p>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .unit-info { display: flex; gap: 24px; flex-wrap: wrap; }
    .no-owners { text-align: center; padding: 24px; color: #999; }
    .no-owners mat-icon { font-size: 48px; height: 48px; width: 48px; }
    .add-owner-form { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
    .owner-select { min-width: 300px; }
    .hint { font-size: 13px; color: #666; }
    .hint a { color: #1976d2; }
  `]
})
export class UnitOwnersComponent implements OnInit {
  unitId!: number;
  unit?: Unit;
  owners: UnitOwner[] = [];
  availableOwners: Owner[] = [];
  displayedColumns = ['ownerName', 'ownerContact', 'isPrimary', 'ownershipPercentage', 'actions'];

  // Add form
  newOwnerId: number | null = null;
  newPercentage = 100;
  newIsPrimary = false;

  constructor(
    private route: ActivatedRoute,
    private ownerService: OwnerService,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.unitId = +this.route.snapshot.paramMap.get('id')!;
    this.loadUnit();
    this.loadOwners();
    this.loadAvailableOwners();
  }

  loadUnit(): void {
    this.ownerService.getUnitById(this.unitId).subscribe(res => {
      if (res.success) this.unit = res.data;
    });
  }

  loadOwners(): void {
    this.ownerService.getUnitOwners(this.unitId).subscribe(res => {
      if (res.success) this.owners = res.data;
    });
  }

  loadAvailableOwners(): void {
    this.ownerService.getActiveOwnersList().subscribe(res => {
      if (res.success) this.availableOwners = res.data;
    });
  }

  addOwner(): void {
    if (!this.newOwnerId || !this.newPercentage) return;

    this.ownerService.addOwnerToUnit({
      unitId: this.unitId,
      ownerId: this.newOwnerId,
      isPrimary: this.newIsPrimary,
      ownershipPercentage: this.newPercentage
    }).subscribe({
      next: (res) => {
        if (res.success) {
          this.snackBar.open('Owner added to unit successfully', 'Close', { duration: 3000 });
          this.loadOwners();
          this.loadUnit();
          this.newOwnerId = null;
          this.newPercentage = 100;
          this.newIsPrimary = false;
        }
      },
      error: (err) => {
        this.snackBar.open(err.error?.message || 'Failed to add owner', 'Close', { duration: 5000 });
      }
    });
  }

  removeOwner(ownerId: number): void {
    if (!confirm('Are you sure you want to remove this owner from the unit?')) return;

    this.ownerService.removeOwnerFromUnit(this.unitId, ownerId).subscribe({
      next: (res) => {
        if (res.success) {
          this.snackBar.open('Owner removed from unit', 'Close', { duration: 3000 });
          this.loadOwners();
          this.loadUnit();
        }
      },
      error: (err) => {
        this.snackBar.open(err.error?.message || 'Failed to remove owner', 'Close', { duration: 5000 });
      }
    });
  }
}
