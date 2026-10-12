import { Injectable, NgZone } from '@angular/core';
import { Router } from '@angular/router';
import { environment } from '@env/environment';
import { VisitorService } from './visitor.service';

/** The Firebase compat SDK is loaded from the CDN at runtime and exposed as a global. */
declare const firebase: any;

const FIREBASE_VERSION = '10.12.2';
const FIREBASE_SCRIPTS = [
  `https://www.gstatic.com/firebasejs/${FIREBASE_VERSION}/firebase-app-compat.js`,
  `https://www.gstatic.com/firebasejs/${FIREBASE_VERSION}/firebase-messaging-compat.js`
];

/**
 * Wires the member PWA to Firebase Cloud Messaging (free web push) so an owner is
 * notified of a new delivery/visitor even when the app is closed.
 *
 * <p><b>Degrades gracefully.</b> Everything is gated on {@code environment.fcm.enabled}.
 * When push is not configured (or the browser lacks support, or the user denies
 * permission), this service does nothing and the approvals screen still works via
 * in-app polling. The Firebase SDK is loaded from the CDN at runtime, so the app builds
 * and runs without any npm dependency when push is off.</p>
 *
 * <p>Background notifications are handled by {@code firebase-messaging-sw.js} (served
 * from the app root); this service handles foreground messages and token registration.</p>
 */
@Injectable({ providedIn: 'root' })
export class FcmService {

  private initialized = false;

  constructor(
    private visitorService: VisitorService,
    private router: Router,
    private zone: NgZone
  ) {}

  /**
   * Initialise FCM for the logged-in member: request permission, obtain the device
   * token, register it with the backend, and listen for foreground messages. Safe to
   * call multiple times and safe to call when push is disabled (no-op).
   */
  async init(): Promise<void> {
    if (this.initialized || !environment.fcm?.enabled) {
      return;
    }
    if (!('serviceWorker' in navigator) || !('Notification' in window)) {
      return;
    }

    try {
      const permission = await Notification.requestPermission();
      if (permission !== 'granted') {
        return;
      }

      await this.loadFirebaseSdk();

      firebase.initializeApp({
        apiKey: environment.fcm.apiKey,
        authDomain: environment.fcm.authDomain,
        projectId: environment.fcm.projectId,
        messagingSenderId: environment.fcm.messagingSenderId,
        appId: environment.fcm.appId
      });

      const messaging = firebase.messaging();
      const swReg = await navigator.serviceWorker.register('firebase-messaging-sw.js');

      const token = await messaging.getToken({
        vapidKey: environment.fcm.vapidKey,
        serviceWorkerRegistration: swReg
      });

      if (token) {
        this.visitorService.registerDeviceToken(token, 'WEB').subscribe({
          error: () => { /* registration failure must not break the app */ }
        });
      }

      // Foreground messages: route to the approvals screen when received.
      messaging.onMessage((payload: any) => {
        this.zone.run(() => {
          const click = payload?.data?.click_action;
          if (click) {
            this.router.navigateByUrl(click);
          }
        });
      });

      this.initialized = true;
    } catch {
      // Any failure (unsupported browser, CDN blocked, bad config) → silently fall back
      // to in-app polling. Push is a convenience, not a requirement.
    }
  }

  /** Loads the Firebase compat scripts from the CDN once. */
  private loadFirebaseSdk(): Promise<void> {
    if (typeof firebase !== 'undefined') {
      return Promise.resolve();
    }
    return FIREBASE_SCRIPTS.reduce<Promise<void>>(
      (chain, src) => chain.then(() => this.loadScript(src)),
      Promise.resolve()
    );
  }

  private loadScript(src: string): Promise<void> {
    return new Promise<void>((resolve, reject) => {
      const existing = document.querySelector(`script[src="${src}"]`);
      if (existing) {
        resolve();
        return;
      }
      const script = document.createElement('script');
      script.src = src;
      script.async = true;
      script.onload = () => resolve();
      script.onerror = () => reject(new Error('Failed to load ' + src));
      document.head.appendChild(script);
    });
  }
}
