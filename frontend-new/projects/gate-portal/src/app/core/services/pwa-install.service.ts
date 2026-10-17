import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

/**
 * Captures the browser's {@code beforeinstallprompt} event so the gate app can offer
 * an "Install" button (Add to Home Screen) for guards. The event only fires over HTTPS
 * with a valid manifest + service worker, on a supported browser, when the app is not
 * already installed.
 */
@Injectable({ providedIn: 'root' })
export class PWAInstallService {
  private deferredPrompt: any;
  private installPromptSubject = new BehaviorSubject<boolean>(false);

  canInstall$ = this.installPromptSubject.asObservable();

  constructor() {
    this.initPWAInstall();
  }

  private initPWAInstall(): void {
    window.addEventListener('beforeinstallprompt', (e) => {
      e.preventDefault();
      this.deferredPrompt = e;
      this.installPromptSubject.next(true);
    });

    window.addEventListener('appinstalled', () => {
      this.deferredPrompt = null;
      this.installPromptSubject.next(false);
    });
  }

  async installPWA(): Promise<boolean> {
    if (!this.deferredPrompt) {
      return false;
    }
    try {
      this.deferredPrompt.prompt();
      const { outcome } = await this.deferredPrompt.userChoice;
      this.deferredPrompt = null;
      this.installPromptSubject.next(false);
      return outcome === 'accepted';
    } catch {
      return false;
    }
  }

  isStandalone(): boolean {
    return window.matchMedia('(display-mode: standalone)').matches ||
      (window.navigator as any).standalone ||
      document.referrer.includes('android-app://');
  }

  dismissInstallPrompt(): void {
    this.installPromptSubject.next(false);
  }
}
