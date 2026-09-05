import { Component, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { MatSidenavModule, MatSidenav } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatDividerModule } from '@angular/material/divider';
import { AuthService, LoginResponse } from '../services/auth.service';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [
    CommonModule, RouterModule, MatSidenavModule,
    MatToolbarModule, MatListModule, MatIconModule, MatButtonModule,
    MatMenuModule, MatDividerModule
  ],
  template: `
    <mat-sidenav-container class="sidenav-container">
      <mat-sidenav #sidenav [mode]="isMobile ? 'over' : 'side'"
                   [opened]="!isMobile" class="sidenav"
                   [fixedInViewport]="isMobile" fixedTopGap="56">
        <div class="sidenav-header">
          <mat-icon class="society-icon">apartment</mat-icon>
          <h3>Society Mgmt</h3>
        </div>
        <mat-nav-list>
          <a mat-list-item routerLink="/dashboard" routerLinkActive="active"
             (click)="closeSidenavOnMobile()">
            <mat-icon matListItemIcon>dashboard</mat-icon>
            <span matListItemTitle>Dashboard</span>
          </a>

          <a mat-list-item routerLink="/owners" routerLinkActive="active"
             *ngIf="hasPermission('OWNER_VIEW')" (click)="closeSidenavOnMobile()">
            <mat-icon matListItemIcon>people</mat-icon>
            <span matListItemTitle>Owners</span>
          </a>

          <a mat-list-item routerLink="/units" routerLinkActive="active"
             *ngIf="hasPermission('UNIT_VIEW')" (click)="closeSidenavOnMobile()">
            <mat-icon matListItemIcon>apartment</mat-icon>
            <span matListItemTitle>Units</span>
          </a>

          <a mat-list-item routerLink="/vendors" routerLinkActive="active"
             *ngIf="hasPermission('VENDOR_VIEW')" (click)="closeSidenavOnMobile()">
            <mat-icon matListItemIcon>store</mat-icon>
            <span matListItemTitle>Vendors</span>
          </a>

          <a mat-list-item routerLink="/tenants" routerLinkActive="active"
             *ngIf="hasPermission('TENANT_VIEW')" (click)="closeSidenavOnMobile()">
            <mat-icon matListItemIcon>person_add</mat-icon>
            <span matListItemTitle>Tenants</span>
          </a>

          <a mat-list-item routerLink="/vouchers" routerLinkActive="active"
             *ngIf="hasPermission('VOUCHER_VIEW')" (click)="closeSidenavOnMobile()">
            <mat-icon matListItemIcon>receipt_long</mat-icon>
            <span matListItemTitle>Vouchers</span>
          </a>

          <a mat-list-item routerLink="/maintenance" routerLinkActive="active"
             (click)="closeSidenavOnMobile()">
            <mat-icon matListItemIcon>payments</mat-icon>
            <span matListItemTitle>Maintenance</span>
          </a>

          <a mat-list-item routerLink="/transactions" routerLinkActive="active"
             *ngIf="hasPermission('TRANSACTION_VIEW') || hasAnyRole(['SUPER_ADMIN', 'CHAIRMAN', 'SECRETARY'])"
             (click)="closeSidenavOnMobile()">
            <mat-icon matListItemIcon>swap_horiz</mat-icon>
            <span matListItemTitle>Transactions</span>
          </a>

          <mat-divider *ngIf="hasAnyRole(['SUPER_ADMIN', 'CHAIRMAN', 'SECRETARY'])"></mat-divider>

          <a mat-list-item routerLink="/users" routerLinkActive="active"
             *ngIf="hasAnyRole(['SUPER_ADMIN', 'CHAIRMAN', 'SECRETARY'])"
             (click)="closeSidenavOnMobile()">
            <mat-icon matListItemIcon>manage_accounts</mat-icon>
            <span matListItemTitle>User Management</span>
          </a>

          <a mat-list-item routerLink="/users/member-requests" routerLinkActive="active"
             *ngIf="hasPermission('MEMBER_REQUEST_VIEW') || hasAnyRole(['SUPER_ADMIN', 'CHAIRMAN', 'SECRETARY'])"
             (click)="closeSidenavOnMobile()">
            <mat-icon matListItemIcon>contact_mail</mat-icon>
            <span matListItemTitle>Member Requests</span>
          </a>

          <a mat-list-item routerLink="/reports" routerLinkActive="active"
             *ngIf="hasPermission('REPORT_FINANCIAL') || hasPermission('REPORT_OCCUPANCY')"
             (click)="closeSidenavOnMobile()">
            <mat-icon matListItemIcon>assessment</mat-icon>
            <span matListItemTitle>Reports</span>
          </a>

          <a mat-list-item routerLink="/settings" routerLinkActive="active"
             *ngIf="hasPermission('SETTINGS_VIEW')" (click)="closeSidenavOnMobile()">
            <mat-icon matListItemIcon>settings</mat-icon>
            <span matListItemTitle>Settings</span>
          </a>

          <a mat-list-item routerLink="/committee" routerLinkActive="active"
             *ngIf="hasPermission('COMMITTEE_VIEW') || hasAnyRole(['SUPER_ADMIN', 'CHAIRMAN', 'SECRETARY'])"
             (click)="closeSidenavOnMobile()">
            <mat-icon matListItemIcon>groups</mat-icon>
            <span matListItemTitle>Committee</span>
          </a>
        </mat-nav-list>
      </mat-sidenav>

      <mat-sidenav-content>
        <mat-toolbar color="primary" class="app-toolbar">
          <button mat-icon-button (click)="sidenav.toggle()">
            <mat-icon>menu</mat-icon>
          </button>
          <span class="toolbar-title">Society Management</span>
          <span class="toolbar-spacer"></span>

          <!-- User Menu -->
          <button mat-icon-button [matMenuTriggerFor]="userMenu">
            <mat-icon>account_circle</mat-icon>
          </button>
          <mat-menu #userMenu="matMenu">
            <div class="user-menu-header">
              <strong>{{ currentUser?.fullName }}</strong>
              <small>{{ currentUser?.roles?.join(', ') }}</small>
            </div>
            <mat-divider></mat-divider>
            <a mat-menu-item routerLink="/change-password">
              <mat-icon>lock</mat-icon> Change Password
            </a>
            <button mat-menu-item (click)="onLogout()">
              <mat-icon>logout</mat-icon> Sign Out
            </button>
          </mat-menu>
        </mat-toolbar>
        <div class="content">
          <ng-content></ng-content>
        </div>
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: [`
    .sidenav-container { height: 100vh; }
    .sidenav { width: 240px; }
    .sidenav-header { padding: 16px; text-align: center; border-bottom: 1px solid #e0e0e0; }
    .sidenav-header h3 { margin: 4px 0 0; color: #1976d2; }
    .society-icon { font-size: 32px; height: 32px; width: 32px; color: #1976d2; }
    .content { padding: 20px; }
    .active { background: rgba(25, 118, 210, 0.08) !important; }
    .toolbar-spacer { flex: 1 1 auto; }
    .toolbar-title { margin-left: 8px; font-size: 18px; }
    .app-toolbar { position: sticky; top: 0; z-index: 1000; }
    .user-menu-header { padding: 12px 16px; }
    .user-menu-header strong { display: block; }
    .user-menu-header small { color: #666; font-size: 11px; }

    /* Mobile adjustments */
    @media (max-width: 768px) {
      .sidenav { width: 260px; }
      .content { padding: 12px; }
      .toolbar-title { font-size: 15px; }
    }
  `]
})
export class LayoutComponent implements OnInit {
  @ViewChild('sidenav') sidenav!: MatSidenav;
  isLoggedIn = false;
  currentUser: LoginResponse | null = null;
  isMobile = false;

  constructor(
    private authService: AuthService,
    private breakpointObserver: BreakpointObserver
  ) {}

  ngOnInit(): void {
    this.authService.currentUser$.subscribe(user => {
      this.currentUser = user;
      this.isLoggedIn = this.authService.isLoggedIn();
    });

    this.breakpointObserver.observe([Breakpoints.Handset, '(max-width: 768px)'])
      .subscribe(result => {
        this.isMobile = result.matches;
      });
  }

  closeSidenavOnMobile(): void {
    if (this.isMobile) {
      this.sidenav.close();
    }
  }

  hasPermission(permission: string): boolean {
    return this.authService.hasPermission(permission);
  }

  hasAnyRole(roles: string[]): boolean {
    return this.authService.hasAnyRole(roles);
  }

  onLogout(): void {
    this.authService.logout();
  }
}
