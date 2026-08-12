import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { environment } from '@env/environment';

@Component({
  selector: 'app-user-list',
  standalone: true,
  imports: [
    CommonModule, RouterModule, FormsModule, MatTableModule, MatPaginatorModule,
    MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule,
    MatChipsModule, MatTooltipModule, MatSnackBarModule
  ],
  template: `
    <div class="container">
      <div class="page-header">
        <h2>User Management</h2>
        <div style="display: flex; gap: 8px;">
          <a mat-raised-button routerLink="/users/roles-permissions">
            <mat-icon>security</mat-icon> Roles & Permissions
          </a>
          <a mat-raised-button color="primary" routerLink="/users/add">
            <mat-icon>person_add</mat-icon> Add User
          </a>
        </div>
      </div>

      <div class="search-bar">
        <mat-form-field appearance="outline">
          <mat-label>Search</mat-label>
          <input matInput [(ngModel)]="searchTerm" (keyup.enter)="loadUsers()"
                 placeholder="Name, Username, Email, Phone">
          <mat-icon matSuffix>search</mat-icon>
        </mat-form-field>
      </div>

      <table mat-table [dataSource]="users" class="mat-elevation-z2">
        <ng-container matColumnDef="username">
          <th mat-header-cell *matHeaderCellDef>Username</th>
          <td mat-cell *matCellDef="let u">{{ u.username }}</td>
        </ng-container>
        <ng-container matColumnDef="fullName">
          <th mat-header-cell *matHeaderCellDef>Full Name</th>
          <td mat-cell *matCellDef="let u">{{ u.fullName }}</td>
        </ng-container>
        <ng-container matColumnDef="email">
          <th mat-header-cell *matHeaderCellDef>Email</th>
          <td mat-cell *matCellDef="let u">{{ u.email || '-' }}</td>
        </ng-container>
        <ng-container matColumnDef="roles">
          <th mat-header-cell *matHeaderCellDef>Roles</th>
          <td mat-cell *matCellDef="let u">
            <mat-chip-set>
              <mat-chip *ngFor="let role of u.roles" class="role-chip">{{ role }}</mat-chip>
            </mat-chip-set>
          </td>
        </ng-container>
        <ng-container matColumnDef="isActive">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let u">
            <span class="status-badge" [ngClass]="u.isActive ? 'active' : 'inactive'">
              {{ u.isActive ? 'Active' : 'Inactive' }}
            </span>
          </td>
        </ng-container>
        <ng-container matColumnDef="lastLogin">
          <th mat-header-cell *matHeaderCellDef>Last Login</th>
          <td mat-cell *matCellDef="let u">{{ u.lastLogin | date:'dd-MM-yyyy HH:mm' }}</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef>Actions</th>
          <td mat-cell *matCellDef="let u">
            <a mat-icon-button [routerLink]="['/users/edit', u.userId]" matTooltip="Edit">
              <mat-icon>edit</mat-icon>
            </a>
            <button mat-icon-button (click)="toggleStatus(u)" [color]="u.isActive ? 'warn' : 'primary'"
                    [matTooltip]="u.isActive ? 'Deactivate' : 'Activate'">
              <mat-icon>{{ u.isActive ? 'block' : 'check_circle' }}</mat-icon>
            </button>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
      </table>

      <mat-paginator [length]="totalElements" [pageSize]="pageSize"
        [pageSizeOptions]="[10, 20, 50]" (page)="onPageChange($event)">
      </mat-paginator>
    </div>
  `,
  styles: [`
    .role-chip { font-size: 11px !important; min-height: 24px !important; }
  `]
})
export class UserListComponent implements OnInit {
  users: any[] = [];
  displayedColumns = ['username', 'fullName', 'email', 'roles', 'isActive', 'lastLogin', 'actions'];
  totalElements = 0;
  pageSize = 20;
  currentPage = 0;
  searchTerm = '';

  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient, private snackBar: MatSnackBar) {}

  ngOnInit(): void { this.loadUsers(); }

  loadUsers(): void {
    let params = new HttpParams()
      .set('page', this.currentPage)
      .set('size', this.pageSize);
    if (this.searchTerm) params = params.set('search', this.searchTerm);

    this.http.get<any>(`${this.apiUrl}/users`, { params }).subscribe(res => {
      if (res.success) {
        this.users = res.data.content;
        this.totalElements = res.data.totalElements;
      }
    });
  }

  toggleStatus(user: any): void {
    this.http.patch<any>(`${this.apiUrl}/users/${user.userId}/toggle-status`, {}).subscribe(res => {
      if (res.success) {
        this.snackBar.open(
          `User ${res.data.isActive ? 'activated' : 'deactivated'}`, 'Close', { duration: 3000 });
        this.loadUsers();
      }
    });
  }

  onPageChange(event: PageEvent): void {
    this.currentPage = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadUsers();
  }
}
