import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { environment } from '@env/environment';
import { AuthService } from '@core/services/auth.service';

interface CommitteeMember {
  memberId: number;
  fullName: string;
  designation: string;
  photoPath: string | null;
  phone: string;
  email: string;
  displayOrder: number;
  isActive: boolean;
  editing?: boolean;
}

@Component({
  selector: 'app-committee-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, ReactiveFormsModule, MatCardModule, MatTableModule,
    MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule,
    MatSlideToggleModule, MatSnackBarModule, MatTooltipModule
  ],
  template: `
    <div class="container">
      <div class="page-header">
        <h2>Management Committee</h2>
        <button mat-raised-button color="primary" *ngIf="canManage" (click)="showAddForm = !showAddForm">
          <mat-icon>{{ showAddForm ? 'close' : 'add' }}</mat-icon>
          {{ showAddForm ? 'Cancel' : 'Add Member' }}
        </button>
      </div>

      <p class="subtitle">
        Manage committee members displayed on the public landing page.
        Set display order: Chairman (1), Secretary (2), Treasurer (3), then others in sequence.
      </p>

      <!-- Add Form -->
      <mat-card *ngIf="showAddForm" class="add-form-card">
        <mat-card-header>
          <mat-card-title>Add Committee Member</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="addForm" (ngSubmit)="onAddMember()">
            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Full Name</mat-label>
                <input matInput formControlName="fullName" placeholder="Mr. John Doe">
                <mat-error *ngIf="addForm.get('fullName')?.hasError('required')">Full name is required</mat-error>
                <mat-error *ngIf="addForm.get('fullName')?.hasError('maxlength')">Full name cannot exceed 150 characters</mat-error>
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Designation</mat-label>
                <input matInput formControlName="designation" placeholder="Chairman">
                <mat-error *ngIf="addForm.get('designation')?.hasError('required')">Designation is required</mat-error>
                <mat-error *ngIf="addForm.get('designation')?.hasError('maxlength')">Designation cannot exceed 100 characters</mat-error>
              </mat-form-field>
            </div>
            <div class="form-row">
              <mat-form-field appearance="outline">
                <mat-label>Phone</mat-label>
                <input matInput formControlName="phone" placeholder="9876543210" maxlength="10" inputmode="numeric">
                <mat-error *ngIf="addForm.get('phone')?.hasError('pattern')">Enter a valid 10-digit mobile number starting with 6-9</mat-error>
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Email</mat-label>
                <input matInput formControlName="email" placeholder="john@example.com" type="email">
                <mat-error *ngIf="addForm.get('email')?.hasError('email')">Enter a valid email address</mat-error>
              </mat-form-field>
              <mat-form-field appearance="outline" class="narrow-field">
                <mat-label>Display Order</mat-label>
                <input matInput type="number" formControlName="displayOrder" min="0">
              </mat-form-field>
            </div>
            <div class="form-actions">
              <button mat-button type="button" (click)="showAddForm = false">Cancel</button>
              <button mat-raised-button color="primary" type="submit" [disabled]="addForm.invalid">
                <mat-icon>save</mat-icon> Save
              </button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>

      <!-- Members Table -->
      <mat-card>
        <mat-card-content>
          <div class="table-responsive">
            <table mat-table [dataSource]="members" class="mat-elevation-z0">

              <ng-container matColumnDef="displayOrder">
                <th mat-header-cell *matHeaderCellDef>#</th>
                <td mat-cell *matCellDef="let m">
                  <span *ngIf="!m.editing">{{ m.displayOrder }}</span>
                  <input *ngIf="m.editing" [(ngModel)]="m.displayOrder" type="number" class="inline-input narrow" min="0">
                </td>
              </ng-container>

              <ng-container matColumnDef="photo">
                <th mat-header-cell *matHeaderCellDef>Photo</th>
                <td mat-cell *matCellDef="let m">
                  <img *ngIf="m.photoPath" [src]="getPhotoUrl(m.photoPath)" class="member-photo" alt="">
                  <mat-icon *ngIf="!m.photoPath" class="no-photo">account_circle</mat-icon>
                </td>
              </ng-container>

              <ng-container matColumnDef="fullName">
                <th mat-header-cell *matHeaderCellDef>Name</th>
                <td mat-cell *matCellDef="let m">
                  <span *ngIf="!m.editing">{{ m.fullName }}</span>
                  <input *ngIf="m.editing" [(ngModel)]="m.fullName" class="inline-input wide">
                </td>
              </ng-container>

              <ng-container matColumnDef="designation">
                <th mat-header-cell *matHeaderCellDef>Designation</th>
                <td mat-cell *matCellDef="let m">
                  <span *ngIf="!m.editing">{{ m.designation }}</span>
                  <input *ngIf="m.editing" [(ngModel)]="m.designation" class="inline-input">
                </td>
              </ng-container>

              <ng-container matColumnDef="phone">
                <th mat-header-cell *matHeaderCellDef>Phone</th>
                <td mat-cell *matCellDef="let m">
                  <span *ngIf="!m.editing">{{ m.phone || '-' }}</span>
                  <input *ngIf="m.editing" [(ngModel)]="m.phone" class="inline-input">
                </td>
              </ng-container>

              <ng-container matColumnDef="isActive">
                <th mat-header-cell *matHeaderCellDef>Active</th>
                <td mat-cell *matCellDef="let m">
                  <mat-slide-toggle [(ngModel)]="m.isActive" [disabled]="!m.editing" color="primary"></mat-slide-toggle>
                </td>
              </ng-container>

              <ng-container matColumnDef="actions">
                <th mat-header-cell *matHeaderCellDef>Actions</th>
                <td mat-cell *matCellDef="let m">
                  <span *ngIf="!canManage" class="view-only">View only</span>
                  <button mat-icon-button *ngIf="canManage && !m.editing" (click)="startEdit(m)" matTooltip="Edit" color="primary">
                    <mat-icon>edit</mat-icon>
                  </button>
                  <button mat-icon-button *ngIf="canManage && m.editing" (click)="saveMember(m)" matTooltip="Save" color="primary">
                    <mat-icon>save</mat-icon>
                  </button>
                  <button mat-icon-button *ngIf="canManage && m.editing" (click)="cancelEdit(m)" matTooltip="Cancel">
                    <mat-icon>close</mat-icon>
                  </button>
                  <button mat-icon-button *ngIf="canManage && !m.editing" (click)="uploadPhotoInput.click(); selectedMember = m"
                          matTooltip="Upload Photo" color="accent">
                    <mat-icon>photo_camera</mat-icon>
                  </button>
                  <button mat-icon-button *ngIf="canManage && !m.editing" (click)="deleteMember(m)" matTooltip="Delete" color="warn">
                    <mat-icon>delete</mat-icon>
                  </button>
                </td>
              </ng-container>

              <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
              <tr mat-row *matRowDef="let row; columns: displayedColumns;" [class.inactive-row]="!row.isActive"></tr>
            </table>
          </div>

          <div *ngIf="members.length === 0" class="empty-state">
            <mat-icon>groups</mat-icon>
            <p>No committee members added yet.</p>
          </div>
        </mat-card-content>
      </mat-card>

      <!-- Hidden file input for photo upload -->
      <input type="file" #uploadPhotoInput (change)="onPhotoSelected($event)"
             accept=".jpg,.jpeg,.png" style="display:none">
    </div>
  `,
  styles: [`
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
    .page-header h2 { margin: 0; }
    .subtitle { color: #666; margin-bottom: 20px; }
    .add-form-card { margin-bottom: 20px; }
    .form-row { display: flex; gap: 16px; flex-wrap: wrap; margin-bottom: 8px; }
    .form-row mat-form-field { flex: 1; min-width: 180px; }
    .narrow-field { flex: 0.5 !important; min-width: 100px !important; }
    .form-actions { display: flex; gap: 12px; justify-content: flex-end; }
    .table-responsive { overflow-x: auto; }
    .inline-input { border: 1px solid #ccc; border-radius: 4px; padding: 6px 8px; font-size: 13px; width: 120px; }
    .inline-input.narrow { width: 50px; }
    .inline-input.wide { width: 160px; }
    .member-photo { width: 40px; height: 40px; border-radius: 50%; object-fit: cover; }
    .no-photo { font-size: 40px; width: 40px; height: 40px; color: #ccc; }
    .inactive-row { opacity: 0.5; background: #fafafa; }
    .empty-state { text-align: center; padding: 40px 20px; color: #999; }
    .empty-state mat-icon { font-size: 48px; height: 48px; width: 48px; margin-bottom: 12px; }

    @media (max-width: 768px) {
      .page-header { flex-direction: column; align-items: flex-start; gap: 12px; }
      .form-row { flex-direction: column; }
      .form-row mat-form-field { min-width: 100%; }
    }
  `]
})
export class CommitteeListComponent implements OnInit {
  members: CommitteeMember[] = [];
  originalMembers: Map<number, CommitteeMember> = new Map();
  displayedColumns = ['displayOrder', 'photo', 'fullName', 'designation', 'phone', 'isActive', 'actions'];
  showAddForm = false;
  addForm!: FormGroup;
  selectedMember: CommitteeMember | null = null;
  canManage = false;

  private apiUrl = environment.apiUrl;

  constructor(
    private http: HttpClient,
    private snackBar: MatSnackBar,
    private fb: FormBuilder,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.canManage = this.authService.hasAnyRole(['SUPER_ADMIN', 'CHAIRMAN', 'SECRETARY'])
      || this.authService.hasPermission('COMMITTEE_MANAGE');
    this.initAddForm();
    this.loadMembers();
  }

  initAddForm(): void {
    this.addForm = this.fb.group({
      fullName: ['', [Validators.required, Validators.maxLength(150)]],
      designation: ['', [Validators.required, Validators.maxLength(100)]],
      phone: ['', [Validators.pattern(/^[6-9]\d{9}$/)]],
      email: ['', [Validators.email]],
      displayOrder: [0]
    });
  }

  loadMembers(): void {
    this.http.get<any>(`${this.apiUrl}/committee-members`).subscribe({
      next: (res) => {
        if (res.success) {
          this.members = res.data.map((m: any) => ({ ...m, editing: false }));
        }
      },
      error: () => this.snackBar.open('Failed to load committee members', 'Close', { duration: 5000 })
    });
  }

  onAddMember(): void {
    if (this.addForm.invalid) return;
    this.http.post<any>(`${this.apiUrl}/committee-members`, this.addForm.value).subscribe({
      next: (res) => {
        if (res.success) {
          this.snackBar.open('Member added successfully', 'Close', { duration: 3000 });
          this.showAddForm = false;
          this.addForm.reset({ displayOrder: 0 });
          this.loadMembers();
        }
      },
      error: (err) => this.snackBar.open(err.error?.message || 'Failed to add member', 'Close', { duration: 5000 })
    });
  }

  startEdit(member: CommitteeMember): void {
    this.originalMembers.set(member.memberId, { ...member });
    member.editing = true;
  }

  saveMember(member: CommitteeMember): void {
    this.http.put<any>(`${this.apiUrl}/committee-members/${member.memberId}`, {
      fullName: member.fullName,
      designation: member.designation,
      phone: member.phone,
      email: member.email,
      displayOrder: member.displayOrder,
      isActive: member.isActive
    }).subscribe({
      next: (res) => {
        if (res.success) {
          member.editing = false;
          this.snackBar.open('Member updated', 'Close', { duration: 3000 });
        }
      },
      error: (err) => this.snackBar.open(err.error?.message || 'Failed to update', 'Close', { duration: 5000 })
    });
  }

  cancelEdit(member: CommitteeMember): void {
    const original = this.originalMembers.get(member.memberId);
    if (original) Object.assign(member, original);
    member.editing = false;
  }

  deleteMember(member: CommitteeMember): void {
    if (!confirm(`Delete "${member.fullName}" from committee?`)) return;
    this.http.delete<any>(`${this.apiUrl}/committee-members/${member.memberId}`).subscribe({
      next: () => {
        this.snackBar.open('Member deleted', 'Close', { duration: 3000 });
        this.loadMembers();
      },
      error: () => this.snackBar.open('Failed to delete', 'Close', { duration: 5000 })
    });
  }

  onPhotoSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files || input.files.length === 0 || !this.selectedMember) return;

    const formData = new FormData();
    formData.append('file', input.files[0]);

    this.http.post<any>(`${this.apiUrl}/committee-members/${this.selectedMember.memberId}/photo`, formData).subscribe({
      next: (res) => {
        if (res.success) {
          this.snackBar.open('Photo uploaded', 'Close', { duration: 3000 });
          this.loadMembers();
        }
      },
      error: () => this.snackBar.open('Photo upload failed', 'Close', { duration: 5000 })
    });

    input.value = '';
  }

  getPhotoUrl(photoPath: string): string {
    return `${this.apiUrl}/files/view/${photoPath}`;
  }
}
