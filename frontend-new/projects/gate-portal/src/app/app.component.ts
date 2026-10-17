import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Subject, takeUntil } from 'rxjs';
import { PWAInstallService } from './core/services/pwa-install.service';

@Component({
  selector: 'gate-app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, MatButtonModule, MatIconModule, MatSnackBarModule],
  template: `
    <div class="gate-app">
      <!-- PWA install prompt -->
      <div class="pwa-install-prompt" *ngIf="showInstallPrompt">
        <div class="install-content">
          <mat-icon>get_app</mat-icon>
          <span>Install Gate Security for faster access</span>
          <div class="install-buttons">
            <button mat-button (click)="installPWA()">Install</button>
            <button mat-button (click)="dismissInstall()">Later</button>
          </div>
        </div>
      </div>

      <router-outlet></router-outlet>
    </div>
  `,
  styles: [`
    .gate-app { min-height: 100vh; }
    .pwa-install-prompt {
      position: fixed; bottom: 16px; left: 16px; right: 16px;
      background: #37474f; color: #fff; border-radius: 8px;
      box-shadow: 0 4px 12px rgba(0,0,0,0.2); z-index: 1000;
    }
    .install-content { display: flex; align-items: center; padding: 14px 16px; gap: 12px; }
    .install-content span { flex: 1; font-size: 14px; }
    .install-buttons { display: flex; gap: 8px; }
    .install-buttons button { color: #fff; min-width: auto; padding: 4px 12px; }
    @media (max-width: 480px) {
      .pwa-install-prompt { left: 8px; right: 8px; bottom: 8px; }
    }
  `]
})
export class AppComponent implements OnInit, OnDestroy {
  title = 'Society Gate Security';
  showInstallPrompt = false;
  private destroy$ = new Subject<void>();

  constructor(private pwaInstall: PWAInstallService, private snackBar: MatSnackBar) {}

  ngOnInit(): void {
    this.pwaInstall.canInstall$
      .pipe(takeUntil(this.destroy$))
      .subscribe(canInstall => {
        this.showInstallPrompt = canInstall && !this.pwaInstall.isStandalone();
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  async installPWA(): Promise<void> {
    const installed = await this.pwaInstall.installPWA();
    if (installed) {
      this.snackBar.open('App installed!', 'Close', { duration: 3000 });
    }
    this.showInstallPrompt = false;
  }

  dismissInstall(): void {
    this.pwaInstall.dismissInstallPrompt();
    this.showInstallPrompt = false;
  }
}
