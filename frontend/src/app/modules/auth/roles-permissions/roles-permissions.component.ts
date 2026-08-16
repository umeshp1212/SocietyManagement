import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { environment } from '@env/environment';

interface Permission {
  permissionId: number;
  permissionName: string;
  module: string;
  description: string;
}

interface RoleDetail {
  roleId: number;
  roleName: string;
  displayName: string;
  description: string;
  permissions: string[];
}

@Component({
  selector: 'app-roles-permissions',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterModule, MatCardModule, MatButtonModule,
    MatIconModule, MatExpansionModule, MatCheckboxModule, MatChipsModule,
    MatDividerModule, MatSnackBarModule, MatProgressSpinnerModule, MatTooltipModule
  ],
  template: `
    <div class="container">
      <div class="page-header">
        <h2>Roles & Permissions</h2>
        <a mat-button routerLink="/users">Back to Users</a>
      </div>

      <p class="subtitle">
        Manage which permissions are assigned to each role. Users inherit permissions from their assigned roles.
      </p>

      <!-- Loading State -->
      <div *ngIf="loading" class="loading-container">
        <mat-spinner diameter="40"></mat-spinner>
        <p>Loading roles and permissions...</p>
      </div>

      <!-- Error State -->
      <mat-card *ngIf="permissionsError && !loading" class="error-card">
        <mat-card-content>
          <div class="error-message">
            <mat-icon>error_outline</mat-icon>
            <span>{{ permissionsError }}</span>
          </div>
        </mat-card-content>
      </mat-card>

      <!-- Roles Accordion -->
      <mat-accordion multi *ngIf="!loading && !permissionsError">
        <mat-expansion-panel *ngFor="let role of roles">
          <mat-expansion-panel-header>
            <mat-panel-title>
              <mat-icon class="role-icon">security</mat-icon>
              {{ role.displayName }}
            </mat-panel-title>
            <mat-panel-description>
              {{ getPermissionCount(role) }} permissions | {{ role.description }}
            </mat-panel-description>
          </mat-expansion-panel-header>

          <!-- Permissions grouped by module -->
          <div class="permissions-container" *ngIf="allPermissions.length > 0">
            <div class="select-all-actions">
              <button mat-button color="primary" (click)="selectAllPermissions(role)"
                      [disabled]="role.roleName === 'SUPER_ADMIN'">
                <mat-icon>check_box</mat-icon> Select All
              </button>
              <button mat-button color="warn" (click)="deselectAllPermissions(role)"
                      [disabled]="role.roleName === 'SUPER_ADMIN'">
                <mat-icon>check_box_outline_blank</mat-icon> Deselect All
              </button>
            </div>

            <div *ngFor="let module of getModules()" class="module-group">
              <div class="module-header">
                <h4 class="module-title">{{ module }}</h4>
                <div class="module-actions">
                  <button mat-icon-button matTooltip="Select all in module"
                          (click)="selectModulePermissions(role, module)"
                          [disabled]="role.roleName === 'SUPER_ADMIN'">
                    <mat-icon>playlist_add_check</mat-icon>
                  </button>
                  <button mat-icon-button matTooltip="Deselect all in module"
                          (click)="deselectModulePermissions(role, module)"
                          [disabled]="role.roleName === 'SUPER_ADMIN'">
                    <mat-icon>playlist_remove</mat-icon>
                  </button>
                </div>
              </div>
              <div class="permission-list">
                <mat-checkbox *ngFor="let perm of getPermissionsByModule(module)"
                              [checked]="hasPermission(role, perm.permissionName)"
                              (change)="togglePermission(role, perm, $event.checked)"
                              [disabled]="role.roleName === 'SUPER_ADMIN'">
                  <span class="perm-name">{{ perm.permissionName }}</span>
                  <span class="perm-desc">{{ perm.description }}</span>
                </mat-checkbox>
              </div>
            </div>
          </div>

          <div *ngIf="allPermissions.length === 0" class="no-permissions">
            <mat-icon>info</mat-icon>
            <span>No permissions found in the system.</span>
          </div>

          <mat-divider></mat-divider>
          <div class="panel-actions">
            <button mat-raised-button color="primary"
                    (click)="saveRolePermissions(role)"
                    [disabled]="role.roleName === 'SUPER_ADMIN'">
              <mat-icon>save</mat-icon> Save Permissions
            </button>
            <span *ngIf="role.roleName === 'SUPER_ADMIN'" class="admin-note">
              Super Admin always has all permissions
            </span>
          </div>
        </mat-expansion-panel>
      </mat-accordion>

      <!-- Permissions Reference -->
      <mat-card style="margin-top: 24px" *ngIf="!loading && !permissionsError && allPermissions.length > 0">
        <mat-card-header>
          <mat-card-title>All Permissions Reference</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div *ngFor="let module of getModules()" class="ref-module">
            <h4>{{ module }}</h4>
            <div class="ref-list">
              <div *ngFor="let perm of getPermissionsByModule(module)" class="ref-item">
                <mat-icon class="ref-icon">lock</mat-icon>
                <div>
                  <strong>{{ perm.permissionName }}</strong>
                  <span>{{ perm.description }}</span>
                </div>
              </div>
            </div>
          </div>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .subtitle { color: #666; margin-bottom: 20px; }
    .role-icon { margin-right: 8px; color: #1976d2; }
    .permissions-container { padding: 12px 0; }
    .module-group { margin-bottom: 16px; }
    .module-header { display: flex; align-items: center; justify-content: space-between; }
    .module-title { color: #1976d2; font-size: 13px; font-weight: 600; text-transform: uppercase;
                    margin: 0 0 8px; letter-spacing: 0.5px; }
    .module-actions { display: flex; gap: 0; }
    .module-actions button { transform: scale(0.85); }
    .permission-list { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 4px; }
    .perm-name { font-weight: 500; font-size: 13px; margin-right: 8px; }
    .perm-desc { font-size: 11px; color: #666; }
    .panel-actions { padding: 12px 0; display: flex; align-items: center; gap: 12px; }
    .admin-note { font-size: 12px; color: #999; font-style: italic; }
    .select-all-actions { display: flex; gap: 8px; margin-bottom: 12px; padding-bottom: 8px; border-bottom: 1px solid #eee; }
    .loading-container { display: flex; flex-direction: column; align-items: center; gap: 16px; padding: 48px 0; }
    .loading-container p { color: #666; }
    .error-card { margin-bottom: 20px; }
    .error-message { display: flex; align-items: center; gap: 12px; color: #d32f2f; padding: 8px 0; }
    .error-message mat-icon { color: #d32f2f; }
    .no-permissions { display: flex; align-items: center; gap: 8px; color: #999; padding: 16px 0; }
    .ref-module { margin-bottom: 16px; }
    .ref-module h4 { color: #333; margin: 0 0 8px; border-bottom: 1px solid #eee; padding-bottom: 4px; }
    .ref-list { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 8px; }
    .ref-item { display: flex; align-items: flex-start; gap: 8px; font-size: 13px; }
    .ref-item strong { display: block; }
    .ref-item span { color: #666; font-size: 12px; }
    .ref-icon { font-size: 16px; height: 16px; width: 16px; color: #999; margin-top: 2px; }
  `]
})
export class RolesPermissionsComponent implements OnInit {
  roles: RoleDetail[] = [];
  allPermissions: Permission[] = [];
  // Track modified permissions per role
  rolePermissionMap: Map<number, Set<string>> = new Map();
  loading = true;
  permissionsError = '';

  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient, private snackBar: MatSnackBar) {}

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.loading = true;
    this.permissionsError = '';

    // Load permissions first, then roles
    this.http.get<any>(`${this.apiUrl}/users/permissions`).subscribe({
      next: (res) => {
        if (res.success) {
          this.allPermissions = res.data;
        }
        this.loadRoles();
      },
      error: (err) => {
        console.error('Failed to load permissions:', err);
        this.permissionsError = err.status === 403
          ? 'You do not have permission to manage roles. Only Super Admin can access this page.'
          : 'Failed to load permissions. Please try again.';
        this.loading = false;
      }
    });
  }

  loadRoles(): void {
    this.http.get<any>(`${this.apiUrl}/users/roles`).subscribe({
      next: (res) => {
        if (res.success) {
          this.roles = res.data;
          // Initialize permission map for each role
          this.roles.forEach(role => {
            this.rolePermissionMap.set(role.roleId, new Set(role.permissions));
          });
        }
        this.loading = false;
      },
      error: (err) => {
        console.error('Failed to load roles:', err);
        this.loading = false;
      }
    });
  }

  getModules(): string[] {
    const modules = [...new Set(this.allPermissions.map(p => p.module))];
    return modules.sort();
  }

  getPermissionsByModule(module: string): Permission[] {
    return this.allPermissions.filter(p => p.module === module);
  }

  getPermissionCount(role: RoleDetail): number {
    const perms = this.rolePermissionMap.get(role.roleId);
    return perms ? perms.size : 0;
  }

  hasPermission(role: RoleDetail, permissionName: string): boolean {
    const perms = this.rolePermissionMap.get(role.roleId);
    return perms ? perms.has(permissionName) : false;
  }

  togglePermission(role: RoleDetail, perm: Permission, checked: boolean): void {
    const perms = this.rolePermissionMap.get(role.roleId);
    if (!perms) return;

    if (checked) {
      perms.add(perm.permissionName);
    } else {
      perms.delete(perm.permissionName);
    }
  }

  selectAllPermissions(role: RoleDetail): void {
    const perms = this.rolePermissionMap.get(role.roleId);
    if (!perms) return;
    this.allPermissions.forEach(p => perms.add(p.permissionName));
  }

  deselectAllPermissions(role: RoleDetail): void {
    const perms = this.rolePermissionMap.get(role.roleId);
    if (!perms) return;
    perms.clear();
  }

  selectModulePermissions(role: RoleDetail, module: string): void {
    const perms = this.rolePermissionMap.get(role.roleId);
    if (!perms) return;
    this.getPermissionsByModule(module).forEach(p => perms.add(p.permissionName));
  }

  deselectModulePermissions(role: RoleDetail, module: string): void {
    const perms = this.rolePermissionMap.get(role.roleId);
    if (!perms) return;
    this.getPermissionsByModule(module).forEach(p => perms.delete(p.permissionName));
  }

  saveRolePermissions(role: RoleDetail): void {
    const perms = this.rolePermissionMap.get(role.roleId);
    if (!perms) return;

    // Find permission IDs from names
    const permissionIds = this.allPermissions
      .filter(p => perms.has(p.permissionName))
      .map(p => p.permissionId);

    this.http.put<any>(`${this.apiUrl}/users/roles/${role.roleId}/permissions`, {
      permissionIds
    }).subscribe({
      next: (res) => {
        if (res.success) {
          this.snackBar.open(`Permissions saved for ${role.displayName}`, 'Close', { duration: 3000 });
          // Update local data
          role.permissions = res.data.permissions;
        }
      },
      error: (err) => {
        this.snackBar.open(err.error?.message || 'Failed to save permissions', 'Close', { duration: 5000 });
      }
    });
  }
}
