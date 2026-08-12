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
    MatDividerModule, MatSnackBarModule
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

      <!-- Roles Accordion -->
      <mat-accordion multi>
        <mat-expansion-panel *ngFor="let role of roles">
          <mat-expansion-panel-header>
            <mat-panel-title>
              <mat-icon class="role-icon">security</mat-icon>
              {{ role.displayName }}
            </mat-panel-title>
            <mat-panel-description>
              {{ role.permissions.length }} permissions | {{ role.description }}
            </mat-panel-description>
          </mat-expansion-panel-header>

          <!-- Permissions grouped by module -->
          <div class="permissions-container">
            <div *ngFor="let module of getModules()" class="module-group">
              <h4 class="module-title">{{ module }}</h4>
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
      <mat-card style="margin-top: 24px">
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
    .module-title { color: #1976d2; font-size: 13px; font-weight: 600; text-transform: uppercase;
                    margin: 0 0 8px; letter-spacing: 0.5px; }
    .permission-list { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 4px; }
    .perm-name { font-weight: 500; font-size: 13px; margin-right: 8px; }
    .perm-desc { font-size: 11px; color: #666; }
    .panel-actions { padding: 12px 0; display: flex; align-items: center; gap: 12px; }
    .admin-note { font-size: 12px; color: #999; font-style: italic; }
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

  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient, private snackBar: MatSnackBar) {}

  ngOnInit(): void {
    this.loadRoles();
    this.loadPermissions();
  }

  loadRoles(): void {
    this.http.get<any>(`${this.apiUrl}/users/roles`).subscribe(res => {
      if (res.success) {
        this.roles = res.data;
        // Initialize permission map for each role
        this.roles.forEach(role => {
          this.rolePermissionMap.set(role.roleId, new Set(role.permissions));
        });
      }
    });
  }

  loadPermissions(): void {
    this.http.get<any>(`${this.apiUrl}/users/permissions`).subscribe(res => {
      if (res.success) this.allPermissions = res.data;
    });
  }

  getModules(): string[] {
    const modules = [...new Set(this.allPermissions.map(p => p.module))];
    return modules.sort();
  }

  getPermissionsByModule(module: string): Permission[] {
    return this.allPermissions.filter(p => p.module === module);
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
