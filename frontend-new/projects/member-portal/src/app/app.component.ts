import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';
import { PWAInstallService } from './services/pwa-install.service';
import { Subject, takeUntil } from 'rxjs';

@Component({
  selector: 'member-app-root',
  standalone: true,
  imports: [
    CommonModule,
    RouterOutlet,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
    MatSnackBarModule
  ],
  template: `
    <div class="member-app">
      <!-- PWA Install Prompt -->
      <div class="pwa-install-prompt" *ngIf="showInstallPrompt">
        <div class="install-content">
          <mat-icon>get_app</mat-icon>
          <span>Install Society App for faster access</span>
          <div class="install-buttons">
            <button mat-button (click)="installPWA()" color="primary">
              Install
            </button>
            <button mat-button (click)="dismissInstall()">
              Later
            </button>
          </div>
        </div>
      </div>
      
      <router-outlet></router-outlet>
    </div>
  `,
  styles: [`
    .member-app {
      min-height: 100vh;
      background-color: #f5f5f5;
    }
    
    .pwa-install-prompt {
      position: fixed;
      bottom: 20px;
      left: 20px;
      right: 20px;
      background: #1976d2;
      color: white;
      border-radius: 8px;
      box-shadow: 0 4px 12px rgba(0,0,0,0.15);
      z-index: 1000;
      animation: slideUp 0.3s ease-out;
    }
    
    .install-content {
      display: flex;
      align-items: center;
      padding: 16px;
      gap: 12px;
    }
    
    .install-content span {
      flex: 1;
      font-size: 14px;
    }
    
    .install-buttons {
      display: flex;
      gap: 8px;
    }
    
    .install-buttons button {
      min-width: auto;
      padding: 4px 12px;
      font-size: 12px;
    }
    
    @keyframes slideUp {
      from { 
        transform: translateY(100%);
        opacity: 0;
      }
      to { 
        transform: translateY(0);
        opacity: 1;
      }
    }
    
    @media (max-width: 480px) {
      .pwa-install-prompt {
        left: 10px;
        right: 10px;
        bottom: 10px;
      }
    }
  `]
})
export class AppComponent implements OnInit, OnDestroy {
  title = 'Society Member Portal';
  showInstallPrompt = false;
  private destroy$ = new Subject<void>();

  constructor(
    private pwaInstall: PWAInstallService,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    // Show install prompt after user has been using the app
    setTimeout(() => {
      this.pwaInstall.canInstall$
        .pipe(takeUntil(this.destroy$))
        .subscribe(canInstall => {
          this.showInstallPrompt = canInstall && !this.pwaInstall.isStandalone();
        });
    }, 10000); // Show after 10 seconds
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  async installPWA(): Promise<void> {
    const installed = await this.pwaInstall.installPWA();
    if (installed) {
      this.snackBar.open('App installed successfully!', 'Close', { duration: 3000 });
    }
    this.showInstallPrompt = false;
  }

  dismissInstall(): void {
    this.pwaInstall.dismissInstallPrompt();
    this.showInstallPrompt = false;
  }
}