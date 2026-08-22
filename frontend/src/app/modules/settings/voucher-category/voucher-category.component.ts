import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
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
import { MatChipsModule } from '@angular/material/chips';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { VoucherCategoryService } from '@core/services/voucher-category.service';
import { VoucherCategory } from '@core/models/voucher-category.model';

@Component({
  selector: 'app-voucher-category',
  standalone: true,
  imports: [
    CommonModule, FormsModule, ReactiveFormsModule, MatCardModule, MatTableModule,
    MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatSlideToggleModule, MatSnackBarModule, MatTooltipModule,
    MatChipsModule, MatDialogModule
  ],
  template: `
    <div class="container">
      <div class="page-header">
        <h2>Voucher Categories</h2>
        <button mat-raised-button color="primary" (click)="showAddForm = !showAddForm">
          <mat-icon>{{ showAddForm ? 'close' : 'add' }}</mat-icon>
          {{ showAddForm ? 'Cancel' : 'Add Category' }}
        </button>
      </div>

      <p class="subtitle">
        Manage expense and income categories used in voucher creation.
        Categories can be added, edited, or deactivated as needed.
      </p>

      <!-- Add New Category Form -->
      <mat-card *ngIf="showAddForm" class="add-form-card">
        <mat-card-header>
          <mat-card-title>Add New Category</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="addForm" (ngSubmit)="onAddCategory()">
            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Code</mat-label>
                <input matInput formControlName="code" placeholder="ELECTRICITY_COMMON"
                       (input)="onCodeInput($event)">
                <mat-hint>Uppercase letters, digits, underscores</mat-hint>
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>Name</mat-label>
                <input matInput formControlName="name" placeholder="Electricity (Common)">
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>Type</mat-label>
                <mat-select formControlName="type">
                  <mat-option value="EXPENSE">Expense</mat-option>
                  <mat-option value="INCOME">Income</mat-option>
                </mat-select>
              </mat-form-field>
            </div>

            <div class="form-row">
              <mat-form-field appearance="outline" class="wide-field">
                <mat-label>Description</mat-label>
                <input matInput formControlName="description" placeholder="Brief description">
              </mat-form-field>

              <mat-form-field appearance="outline" class="narrow-field">
                <mat-label>Display Order</mat-label>
                <input matInput type="number" formControlName="displayOrder" min="0">
              </mat-form-field>
            </div>

            <div class="form-actions">
              <button mat-button type="button" (click)="showAddForm = false">Cancel</button>
              <button mat-raised-button color="primary" type="submit"
                      [disabled]="addForm.invalid">
                <mat-icon>save</mat-icon> Save Category
              </button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>

      <!-- Categories Table -->
      <mat-card>
        <mat-card-content>
          <div class="filter-row">
            <mat-form-field appearance="outline" class="filter-field">
              <mat-label>Filter by Type</mat-label>
              <mat-select [(value)]="filterType" (selectionChange)="applyFilter()">
                <mat-option value="ALL">All</mat-option>
                <mat-option value="EXPENSE">Expense</mat-option>
                <mat-option value="INCOME">Income</mat-option>
              </mat-select>
            </mat-form-field>
            <span class="category-count">{{ filteredCategories.length }} categories</span>
          </div>

          <div class="table-responsive">
            <table mat-table [dataSource]="filteredCategories" class="mat-elevation-z0">

              <ng-container matColumnDef="displayOrder">
                <th mat-header-cell *matHeaderCellDef>#</th>
                <td mat-cell *matCellDef="let c">
                  <span *ngIf="!c.editing">{{ c.displayOrder }}</span>
                  <input *ngIf="c.editing" [(ngModel)]="c.displayOrder" type="number"
                         class="inline-input narrow" min="0">
                </td>
              </ng-container>

              <ng-container matColumnDef="code">
                <th mat-header-cell *matHeaderCellDef>Code</th>
                <td mat-cell *matCellDef="let c">
                  <span *ngIf="!c.editing" class="code-text">{{ c.code }}</span>
                  <input *ngIf="c.editing" [(ngModel)]="c.code"
                         class="inline-input" placeholder="CODE">
                </td>
              </ng-container>

              <ng-container matColumnDef="name">
                <th mat-header-cell *matHeaderCellDef>Name</th>
                <td mat-cell *matCellDef="let c">
                  <span *ngIf="!c.editing">{{ c.name }}</span>
                  <input *ngIf="c.editing" [(ngModel)]="c.name"
                         class="inline-input wide" placeholder="Category Name">
                </td>
              </ng-container>

              <ng-container matColumnDef="type">
                <th mat-header-cell *matHeaderCellDef>Type</th>
                <td mat-cell *matCellDef="let c">
                  <span *ngIf="!c.editing" class="type-chip"
                        [class.expense]="c.type === 'EXPENSE'"
                        [class.income]="c.type === 'INCOME'">
                    {{ c.type }}
                  </span>
                  <select *ngIf="c.editing" [(ngModel)]="c.type" class="inline-select">
                    <option value="EXPENSE">EXPENSE</option>
                    <option value="INCOME">INCOME</option>
                  </select>
                </td>
              </ng-container>

              <ng-container matColumnDef="description">
                <th mat-header-cell *matHeaderCellDef>Description</th>
                <td mat-cell *matCellDef="let c">
                  <span *ngIf="!c.editing" class="desc-text">{{ c.description || '-' }}</span>
                  <input *ngIf="c.editing" [(ngModel)]="c.description"
                         class="inline-input wide" placeholder="Description">
                </td>
              </ng-container>

              <ng-container matColumnDef="isActive">
                <th mat-header-cell *matHeaderCellDef>Active</th>
                <td mat-cell *matCellDef="let c">
                  <mat-slide-toggle [(ngModel)]="c.isActive"
                                    [disabled]="!c.editing" color="primary">
                  </mat-slide-toggle>
                </td>
              </ng-container>

              <ng-container matColumnDef="actions">
                <th mat-header-cell *matHeaderCellDef>Actions</th>
                <td mat-cell *matCellDef="let c">
                  <button mat-icon-button *ngIf="!c.editing" (click)="startEdit(c)"
                          matTooltip="Edit" color="primary">
                    <mat-icon>edit</mat-icon>
                  </button>
                  <button mat-icon-button *ngIf="c.editing" (click)="saveCategory(c)"
                          matTooltip="Save" color="primary">
                    <mat-icon>save</mat-icon>
                  </button>
                  <button mat-icon-button *ngIf="c.editing" (click)="cancelEdit(c)"
                          matTooltip="Cancel">
                    <mat-icon>close</mat-icon>
                  </button>
                  <button mat-icon-button *ngIf="!c.editing" (click)="deleteCategory(c)"
                          matTooltip="Delete" color="warn">
                    <mat-icon>delete</mat-icon>
                  </button>
                </td>
              </ng-container>

              <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
              <tr mat-row *matRowDef="let row; columns: displayedColumns;"
                  [class.inactive-row]="!row.isActive"></tr>
            </table>
          </div>

          <div *ngIf="filteredCategories.length === 0" class="empty-state">
            <mat-icon>category</mat-icon>
            <p>No categories found. Add one to get started.</p>
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
    .form-row { display: flex; gap: 16px; flex-wrap: wrap; margin-bottom: 8px; }
    .form-row mat-form-field { flex: 1; min-width: 180px; }
    .wide-field { flex: 2 !important; }
    .narrow-field { flex: 0.5 !important; min-width: 100px !important; }
    .form-actions { display: flex; gap: 12px; justify-content: flex-end; }
    .filter-row { display: flex; align-items: center; gap: 16px; margin-bottom: 12px; }
    .filter-field { width: 180px; }
    .category-count { color: #666; font-size: 13px; }
    .table-responsive { overflow-x: auto; }
    .inline-input { border: 1px solid #ccc; border-radius: 4px; padding: 6px 8px; font-size: 13px; width: 100px; }
    .inline-input.narrow { width: 50px; }
    .inline-input.wide { width: 160px; }
    .inline-select { border: 1px solid #ccc; border-radius: 4px; padding: 6px 8px; font-size: 13px; }
    .code-text { font-family: monospace; font-size: 12px; color: #555; background: #f5f5f5; padding: 2px 6px; border-radius: 3px; }
    .type-chip { font-size: 11px; font-weight: 500; padding: 2px 8px; border-radius: 12px; text-transform: uppercase; }
    .type-chip.expense { background: #ffebee; color: #c62828; }
    .type-chip.income { background: #e8f5e9; color: #2e7d32; }
    .desc-text { font-size: 12px; color: #666; }
    .inactive-row { opacity: 0.5; background: #fafafa; }
    .empty-state { text-align: center; padding: 40px 20px; color: #999; }
    .empty-state mat-icon { font-size: 48px; height: 48px; width: 48px; margin-bottom: 12px; }

    @media (max-width: 768px) {
      .page-header { flex-direction: column; align-items: flex-start; gap: 12px; }
      .form-row { flex-direction: column; }
      .form-row mat-form-field { min-width: 100%; }
      .inline-input.wide { width: 120px; }
    }
  `]
})
export class VoucherCategoryComponent implements OnInit {
  categories: (VoucherCategory & { editing?: boolean })[] = [];
  filteredCategories: (VoucherCategory & { editing?: boolean })[] = [];
  originalCategories: Map<number, VoucherCategory> = new Map();
  displayedColumns = ['displayOrder', 'code', 'name', 'type', 'description', 'isActive', 'actions'];
  filterType = 'ALL';
  showAddForm = false;
  addForm!: FormGroup;

  constructor(
    private categoryService: VoucherCategoryService,
    private snackBar: MatSnackBar,
    private fb: FormBuilder
  ) {}

  ngOnInit(): void {
    this.initAddForm();
    this.loadCategories();
  }

  initAddForm(): void {
    this.addForm = this.fb.group({
      code: ['', [Validators.required, Validators.pattern(/^[A-Z][A-Z0-9_]*$/)]],
      name: ['', [Validators.required, Validators.maxLength(100)]],
      type: ['EXPENSE', Validators.required],
      description: [''],
      displayOrder: [0]
    });
  }

  loadCategories(): void {
    this.categoryService.getAllCategories().subscribe({
      next: (res) => {
        if (res.success) {
          this.categories = res.data.map(c => ({ ...c, editing: false }));
          this.categories.forEach(c => this.originalCategories.set(c.categoryId, { ...c }));
          this.applyFilter();
        }
      },
      error: () => {
        this.snackBar.open('Failed to load categories', 'Close', { duration: 5000 });
      }
    });
  }

  applyFilter(): void {
    if (this.filterType === 'ALL') {
      this.filteredCategories = [...this.categories];
    } else {
      this.filteredCategories = this.categories.filter(c => c.type === this.filterType);
    }
  }

  onCodeInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    input.value = input.value.toUpperCase().replace(/[^A-Z0-9_]/g, '');
    this.addForm.get('code')?.setValue(input.value);
  }

  onAddCategory(): void {
    if (this.addForm.invalid) return;

    this.categoryService.createCategory(this.addForm.value).subscribe({
      next: (res) => {
        if (res.success) {
          this.snackBar.open('Category created successfully', 'Close', { duration: 3000 });
          this.showAddForm = false;
          this.addForm.reset({ type: 'EXPENSE', displayOrder: 0 });
          this.loadCategories();
        }
      },
      error: (err) => {
        this.snackBar.open(err.error?.message || 'Failed to create category', 'Close', { duration: 5000 });
      }
    });
  }

  startEdit(category: any): void {
    this.originalCategories.set(category.categoryId, { ...category });
    category.editing = true;
  }

  saveCategory(category: any): void {
    this.categoryService.updateCategory(category.categoryId, {
      code: category.code,
      name: category.name,
      type: category.type,
      description: category.description,
      displayOrder: category.displayOrder,
      isActive: category.isActive
    }).subscribe({
      next: (res) => {
        if (res.success) {
          category.editing = false;
          this.originalCategories.set(category.categoryId, { ...category });
          this.snackBar.open(`Category "${category.name}" updated`, 'Close', { duration: 3000 });
        }
      },
      error: (err) => {
        this.snackBar.open(err.error?.message || 'Failed to update', 'Close', { duration: 5000 });
      }
    });
  }

  cancelEdit(category: any): void {
    const original = this.originalCategories.get(category.categoryId);
    if (original) {
      Object.assign(category, original);
    }
    category.editing = false;
  }

  deleteCategory(category: VoucherCategory & { editing?: boolean }): void {
    if (!confirm(`Are you sure you want to delete "${category.name}"? This cannot be undone.`)) {
      return;
    }

    this.categoryService.deleteCategory(category.categoryId).subscribe({
      next: (res) => {
        if (res.success) {
          this.snackBar.open(`Category "${category.name}" deleted`, 'Close', { duration: 3000 });
          this.loadCategories();
        }
      },
      error: (err) => {
        this.snackBar.open(err.error?.message || 'Failed to delete category', 'Close', { duration: 5000 });
      }
    });
  }
}
