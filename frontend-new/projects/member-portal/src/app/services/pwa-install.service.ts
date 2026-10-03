import { Injectable } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { BehaviorSubject, filter } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class PWAInstallService {
  private deferredPrompt: any;
  private installPromptSubject = new BehaviorSubject<boolean>(false);
  
  canInstall$ = this.installPromptSubject.asObservable();

  constructor(private router: Router) {
    this.initPWAInstall();
  }

  private initPWAInstall(): void {
    // Listen for beforeinstallprompt event
    window.addEventListener('beforeinstallprompt', (e) => {
      console.log('PWA install prompt available');
      e.preventDefault();
      this.deferredPrompt = e;
      this.installPromptSubject.next(true);
    });

    // Listen for app installed
    window.addEventListener('appinstalled', () => {
      console.log('PWA was installed');
      this.deferredPrompt = null;
      this.installPromptSubject.next(false);
    });
  }

  async installPWA(): Promise<boolean> {
    if (!this.deferredPrompt) {
      console.log('No install prompt available');
      return false;
    }

    try {
      // Show the install prompt
      this.deferredPrompt.prompt();
      
      // Wait for the user to respond to the prompt
      const { outcome } = await this.deferredPrompt.userChoice;
      
      console.log(`User response to PWA install prompt: ${outcome}`);
      
      // Clear the deferred prompt
      this.deferredPrompt = null;
      this.installPromptSubject.next(false);
      
      return outcome === 'accepted';
    } catch (error) {
      console.error('Error installing PWA:', error);
      return false;
    }
  }

  isStandalone(): boolean {
    // Check if running in standalone mode (installed PWA)
    return window.matchMedia('(display-mode: standalone)').matches ||
           (window.navigator as any).standalone ||
           document.referrer.includes('android-app://');
  }

  dismissInstallPrompt(): void {
    this.installPromptSubject.next(false);
  }
}