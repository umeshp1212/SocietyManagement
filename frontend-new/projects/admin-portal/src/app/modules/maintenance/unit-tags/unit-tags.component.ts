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
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MaintenanceService } from '@core/services/maintenance.service';
import { OwnerService } from '@core/services/owner.service';
import { AuthService } from '@core/services/auth.service';
import { Unit } from '@core/models/owner.model';
import { BackButtonComponent } from '@shared/components/back-button';

/**
 * Admin screen to manage "unit tags" — arbitrary, runtime-created groups of units
 * (e.g. solar-panel owners). A maintenance charge can then be restricted to a tag so
 * only the tagged units are billed, with no code change required.
 */
@Component({
  selector: 'app-unit-tags',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, MatCardModule, MatButtonModule,
    MatIconModule, MatTableModule, MatFormFieldModule, MatInputModule, MatCheckboxModule,
    MatSlideToggleModule, MatTooltipModule, BackButtonComponent],
  template: `
    <div class="container">
      <app-back-button link="/maintenance/charge-config" label="Back to Charges"></app-back-button>
      <div class="page-header">
        <h2>Unit Tags</h2>
        <button mat-raised-button color="primary" (click)="startCreate()" *ngIf="canManage()">
          <mat-icon>add</mat-icon> Add Tag
        </button>
      </div>

      <p class="hint">
        Tags let you bill an arbitrary group of units (e.g. owners who installed a solar panel).
        Create a tag, assign the units that belong to it, then pick the tag under
        "Restrict to Unit Tag" when configuring a charge.
      </p>

      <!-- Add/Edit Tag Form -->
      <mat-card class="form-card" *ngIf="showForm">
        <mat-card-header>
          <mat-card-title>{{ editingId ? 'Edit' : 'Add New' }} Tag</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form class="tag-form" (ngSubmit)="saveTag()">
            <mat-form-field appearance="outline">
              <mat-label>Tag Code</mat-label>
              <input matInput [(ngModel)]="form.tagCode" name="tagCode" required
                     [disabled]="!!editingId" placeholder="e.g., SOLAR_PANEL">
              <mat-hint>Stable code, uppercase. Cannot be changed later.</mat-hint>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Tag Name</mat-label>
              <input matInput [(ngModel)]="form.tagName" name="tagName" required
                     placeholder="e.g., Solar Panel Users">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Description</mat-label>
              <input matInput [(ngModel)]="form.description" name="description">
            </mat-form-field>
            <mat-slide-toggle [(ngModel)]="form.isActive" name="isActive">Active</mat-slide-toggle>
            <div class="form-actions">
              <button mat-raised-button color="primary" type="submit">
                <mat-icon>save</mat-icon> {{ editingId ? 'Update' : 'Save' }}
              </button>
              <button mat-button type="button" (click)="cancelForm()">Cancel</button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>

      <!-- Tags List -->
      <mat-card>
        <mat-card-content>
          <table mat-table [dataSource]="tags" class="mat-elevation-z1 full-width">
            <ng-container matColumnDef="tagName">
              <th mat-header-cell *matHeaderCellDef>Name</th>
              <td mat-cell *matCellDef="let t">{{ t.tagName }}</td>
            </ng-container>
            <ng-container matColumnDef="tagCode">
              <th mat-header-cell *matHeaderCellDef>Code</th>
              <td mat-cell *matCellDef="let t">{{ t.tagCode }}</td>
            </ng-container>
            <ng-container matColumnDef="assignedUnitCount">
              <th mat-header-cell *matHeaderCellDef>Units</th>
              <td mat-cell *matCellDef="let t">{{ t.assignedUnitCount }}</td>
            </ng-container>
            <ng-container matColumnDef="isActive">
              <th mat-header-cell *matHeaderCellDef>Active</th>
              <td mat-cell *matCellDef="let t">
                <span class="status-badge" [ngClass]="t.isActive ? 'active' : 'inactive'">
                  {{ t.isActive ? 'Active' : 'Inactive' }}
                </span>
              </td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef>Actions</th>
              <td mat-cell *matCellDef="let t">
                <button mat-icon-button color="accent" (click)="openAssign(t)" *ngIf="canManage()"
                        matTooltip="Assign units">
                  <mat-icon>group_add</mat-icon>
                </button>
                <button mat-icon-button color="primary" (click)="startEdit(t)" *ngIf="canManage()">
                  <mat-icon>edit</mat-icon>
                </button>
                <button mat-icon-button color="warn" (click)="deleteTag(t)" *ngIf="canManage()">
                  <mat-icon>delete</mat-icon>
                </button>
                <span *ngIf="!canManage()" class="view-only">—</span>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns;"></tr>
          </table>
        </mat-card-content>
      </mat-card>

      <!-- Assign Units Panel -->
      <mat-card class="assign-card" *ngIf="assignTag">
        <mat-card-header>
          <mat-card-title>Assign Units to "{{ assignTag.tagName }}"</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Filter units</mat-label>
            <input matInput [(ngModel)]="unitFilter" placeholder="unit number / wing">
          </mat-form-field>
          <div class="unit-grid">
            <mat-checkbox *ngFor="let u of filteredUnits()"
                          [checked]="selectedUnitIds.has(u.unitId)"
                          (change)="toggleUnit(u.unitId, $event.checked)">
              {{ u.unitNumber }}<span class="muted"> ({{ u.unitType }}<span *ngIf="u.wing">, {{ u.wing }}</span>)</span>
            </mat-checkbox>
          </div>
          <div class="form-actions">
            <span class="selected-count">{{ selectedUnitIds.size }} selected</span>
            <button mat-raised-button color="primary" (click)="saveAssignments()">
              <mat-icon>save</mat-icon> Save Assignments
            </button>
            <button mat-button (click)="closeAssign()">Cancel</button>
          </div>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .container { padding: 24px; }
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
    .hint { color: #666; margin: 0 0 16px; max-width: 760px; }
    .form-card, .assign-card { margin-bottom: 24px; }
    .tag-form { display: grid; grid-template-columns: 1fr 1fr; gap: 8px 16px; align-items: center; }
    .tag-form mat-form-field { width: 100%; }
    .form-actions { grid-column: 1 / -1; display: flex; gap: 12px; align-items: center; }
    .full-width { width: 100%; }
    .unit-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 8px; max-height: 340px; overflow: auto; padding: 8px 0; }
    .muted { color: #888; font-size: 12px; }
    .selected-count { color: #555; margin-right: auto; }
    .status-badge { padding: 4px 8px; border-radius: 12px; font-size: 12px; font-weight: 500; }
    .status-badge.active { background: #e8f5e9; color: #2e7d32; }
    .status-badge.inactive { background: #fbe9e7; color: #c62828; }
  `]
})
export class UnitTagsComponent implements OnInit {
  tags: any[] = [];
  units: Unit[] = [];
  columns = ['tagName', 'tagCode', 'assignedUnitCount', 'isActive', 'actions'];

  showForm = false;
  editingId: number | null = null;
  form = { tagCode: '', tagName: '', description: '', isActive: true };

  assignTag: any | null = null;
  selectedUnitIds = new Set<number>();
  unitFilter = '';

  constructor(
    private maintenanceService: MaintenanceService,
    private ownerService: OwnerService,
    private authService: AuthService) {}

  canManage(): boolean {
    return this.authService.hasPermission('MAINTENANCE_CONFIG');
  }

  ngOnInit(): void {
    this.loadTags();
    this.loadUnits();
  }

  loadTags(): void {
    this.maintenanceService.getAllUnitTags().subscribe(res => {
      if (res.success) { this.tags = res.data || []; }
    });
  }

  loadUnits(): void {
    // One large page is sufficient for a society's unit count.
    this.ownerService.getAllUnits(0, 1000).subscribe(res => {
      if (res.success) { this.units = res.data?.content || []; }
    });
  }

  startCreate(): void {
    this.editingId = null;
    this.form = { tagCode: '', tagName: '', description: '', isActive: true };
    this.showForm = true;
  }

  startEdit(tag: any): void {
    this.editingId = tag.unitTagId;
    this.form = {
      tagCode: tag.tagCode,
      tagName: tag.tagName,
      description: tag.description || '',
      isActive: tag.isActive
    };
    this.showForm = true;
  }

  saveTag(): void {
    const payload = { ...this.form };
    const done = (ok: boolean) => { if (ok) { this.loadTags(); this.cancelForm(); } };
    if (this.editingId) {
      this.maintenanceService.updateUnitTag(this.editingId, payload).subscribe(r => done(r.success));
    } else {
      this.maintenanceService.createUnitTag(payload).subscribe(r => done(r.success));
    }
  }

  cancelForm(): void {
    this.showForm = false;
    this.editingId = null;
    this.form = { tagCode: '', tagName: '', description: '', isActive: true };
  }

  deleteTag(tag: any): void {
    if (!confirm(`Delete tag "${tag.tagName}"? Units will lose this tag.`)) { return; }
    this.maintenanceService.deleteUnitTag(tag.unitTagId).subscribe(res => {
      if (res.success) { this.loadTags(); }
    });
  }

  openAssign(tag: any): void {
    this.assignTag = tag;
    this.selectedUnitIds = new Set<number>();
    this.unitFilter = '';
    this.maintenanceService.getUnitsForTag(tag.unitTagId).subscribe(res => {
      if (res.success) { this.selectedUnitIds = new Set<number>(res.data || []); }
    });
  }

  closeAssign(): void {
    this.assignTag = null;
    this.selectedUnitIds = new Set<number>();
  }

  toggleUnit(unitId: number, checked: boolean): void {
    if (checked) { this.selectedUnitIds.add(unitId); }
    else { this.selectedUnitIds.delete(unitId); }
  }

  filteredUnits(): Unit[] {
    const f = this.unitFilter.trim().toLowerCase();
    if (!f) { return this.units; }
    return this.units.filter(u =>
      (u.unitNumber || '').toLowerCase().includes(f) ||
      (u.wing || '').toLowerCase().includes(f));
  }

  saveAssignments(): void {
    if (!this.assignTag) { return; }
    const ids = Array.from(this.selectedUnitIds);
    this.maintenanceService.setUnitsForTag(this.assignTag.unitTagId, ids).subscribe(res => {
      if (res.success) {
        this.loadTags();
        this.closeAssign();
      }
    });
  }
}
