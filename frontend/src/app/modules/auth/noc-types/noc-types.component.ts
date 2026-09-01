import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { environment } from '@env/environment';

@Component({
  selector: 'app-noc-types',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterModule,
    MatCardModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatSlideToggleModule, MatSnackBarModule
  ],
  template: `
    <div class="container">
      <div class="page-header">
        <h2>NOC Types</h2>
        <button mat-raised-button color="primary" (click)="showAddForm = !showAddForm">
          <mat-icon>{{ showAddForm ? 'close' : 'add' }}</mat-icon>
          {{ showAddForm ? 'Cancel' : 'Add NOC Type' }}
        </button>
      </div>

      <p class="subtitle">
        Configure the types of No Objection Certificate owners can request. The default template
        pre-fills the certificate body at approval (admin can still edit per request).
        Placeholders: <code>{{ '{' }}ownerName{{ '}' }} {{ '{' }}unitNumber{{ '}' }} {{ '{' }}societyName{{ '}' }} {{ '{' }}addressee{{ '}' }} {{ '{' }}details{{ '}' }} {{ '{' }}date{{ '}' }}</code>
      </p>

      <!-- Add Form -->
      <mat-card *ngIf="showAddForm" class="add-card">
        <mat-card-content>
          <div class="row">
            <mat-form-field appearance="outline">
              <mat-label>Code</mat-label>
              <input matInput [(ngModel)]="newType.code" placeholder="LOAN_TRANSFER">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Name</mat-label>
              <input matInput [(ngModel)]="newType.name" placeholder="Loan / Loan Transfer">
            </mat-form-field>
            <mat-form-field appearance="outline" class="narrow">
              <mat-label>Order</mat-label>
              <input matInput type="number" [(ngModel)]="newType.displayOrder">
            </mat-form-field>
          </div>
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Description</mat-label>
            <input matInput [(ngModel)]="newType.description">
          </mat-form-field>
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Default Template</mat-label>
            <textarea matInput [(ngModel)]="newType.defaultTemplate" rows="4"></textarea>
          </mat-form-field>
          <div class="actions">
            <button mat-raised-button color="primary" (click)="addType()"
                    [disabled]="!newType.code || !newType.name">
              <mat-icon>save</mat-icon> Save
            </button>
          </div>
        </mat-card-content>
      </mat-card>

      <!-- List -->
      <mat-card *ngFor="let t of types" class="type-card">
        <mat-card-content>
          <div class="type-head">
            <div>
              <strong>{{ t.name }}</strong> <span class="code">({{ t.code }})</span>
              <span class="chip" [class.inactive]="!t.isActive">{{ t.isActive ? 'Active' : 'Inactive' }}</span>
            </div>
            <mat-slide-toggle [(ngModel)]="t.isActive" (change)="saveType(t)" color="primary"></mat-slide-toggle>
          </div>
          <p class="desc" *ngIf="t.description">{{ t.description }}</p>
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Default Template</mat-label>
            <textarea matInput [(ngModel)]="t.defaultTemplate" rows="3"></textarea>
          </mat-form-field>
          <div class="actions">
            <button mat-button color="primary" (click)="saveType(t)"><mat-icon>save</mat-icon> Save</button>
          </div>
        </mat-card-content>
      </mat-card>

      <div *ngIf="types.length === 0" class="empty">No NOC types configured yet.</div>
    </div>
  `,
  styles: [`
    .container { padding: 20px; max-width: 800px; margin: 0 auto; }
    .page-header { display: flex; justify-content: space-between; align-items: center; }
    .page-header h2 { margin: 0; }
    .subtitle { color: #666; font-size: 13px; margin: 8px 0 16px; }
    .subtitle code { background: #f5f5f5; padding: 2px 4px; border-radius: 3px; font-size: 12px; }
    .add-card, .type-card { margin-bottom: 12px; }
    .row { display: flex; gap: 12px; }
    .row mat-form-field { flex: 1; }
    .narrow { flex: 0.4 !important; }
    .full-width { width: 100%; }
    .actions { display: flex; justify-content: flex-end; }
    .type-head { display: flex; justify-content: space-between; align-items: center; }
    .code { color: #888; font-size: 13px; }
    .chip { font-size: 11px; padding: 2px 8px; border-radius: 12px; background: #e8f5e9; color: #2e7d32; margin-left: 8px; }
    .chip.inactive { background: #ffebee; color: #c62828; }
    .desc { color: #666; font-size: 13px; margin: 6px 0; }
    .empty { text-align: center; color: #999; padding: 40px; }
  `]
})
export class NocTypesComponent implements OnInit {
  types: any[] = [];
  showAddForm = false;
  newType: any = { code: '', name: '', description: '', defaultTemplate: '', displayOrder: 0, isActive: true };

  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient, private snackBar: MatSnackBar) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.http.get<any>(`${this.apiUrl}/noc-types`).subscribe({
      next: (res) => { if (res.success) this.types = res.data || []; },
      error: (err) => this.snackBar.open(err.error?.message || 'Failed to load NOC types', 'Close', { duration: 4000 })
    });
  }

  addType(): void {
    this.http.post<any>(`${this.apiUrl}/noc-types`, this.newType).subscribe({
      next: (res) => {
        if (res.success) {
          this.snackBar.open('NOC type created', 'Close', { duration: 3000 });
          this.newType = { code: '', name: '', description: '', defaultTemplate: '', displayOrder: 0, isActive: true };
          this.showAddForm = false;
          this.load();
        }
      },
      error: (err) => this.snackBar.open(err.error?.message || 'Failed to create', 'Close', { duration: 4000 })
    });
  }

  saveType(t: any): void {
    this.http.put<any>(`${this.apiUrl}/noc-types/${t.nocTypeId}`, {
      name: t.name, description: t.description, defaultTemplate: t.defaultTemplate,
      displayOrder: t.displayOrder, isActive: t.isActive
    }).subscribe({
      next: (res) => { if (res.success) this.snackBar.open('Saved', 'Close', { duration: 2000 }); },
      error: (err) => this.snackBar.open(err.error?.message || 'Failed to save', 'Close', { duration: 4000 })
    });
  }
}
