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
  private swRegistration: ServiceWorkerRegistration | null = null;

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
      // Register the FCM worker at its OWN scope ("/member/fcm/") so it does not
      // collide with Angular's ngsw-worker.js, which controls the "/member/" scope.
      // Two service workers can only coexist on distinct scopes.
      this.swRegistration = await navigator.serviceWorker.register(
        'firebase-messaging-sw.js', { scope: '/member/fcm/' });

      const token = await messaging.getToken({
        vapidKey: environment.fcm.vapidKey,
        serviceWorkerRegistration: this.swRegistration
      });

      if (token) {
        this.visitorService.registerDeviceToken(token, 'WEB').subscribe({
          error: () => { /* registration failure must not break the app */ }
        });
      }

      // Foreground messages: FCM does NOT auto-display a notification while the app is
      // open, so show one ourselves (tapping it routes to the approvals screen).
      // Without this, an open app shows nothing on the device tray — which looks
      // exactly like "push not working".
      messaging.onMessage((payload: any) => {
        this.zone.run(() => this.showForegroundNotification(payload));
      });

      this.initialized = true;
    } catch {
      // Any failure (unsupported browser, CDN blocked, bad config) → silently fall back
      // to in-app polling. Push is a convenience, not a requirement.
    }
  }

  /**
   * Displays a notification for a message received while the app is in the foreground.
   * Uses the FCM service-worker registration (so a tap can reopen/focus the app) when
   * available, falling back to the Notification constructor.
   */
  private showForegroundNotification(payload: any): void {
    try {
      if (Notification.permission !== 'granted') {
        return;
      }
      const title = payload?.notification?.title || 'Visitor at the gate';
      const body = payload?.notification?.body || 'Tap to review the request.';
      const clickAction = payload?.data?.click_action || '/member/visitor-approvals';
      const options: NotificationOptions = {
        body,
        icon: 'icons/icon-192x192.png',
        data: { click_action: clickAction },
        tag: 'visitor-' + (payload?.data?.visitorRequestId || '')
      };

      if (this.swRegistration) {
        this.swRegistration.showNotification(title, options);
      } else {
        // eslint-disable-next-line no-new
        new Notification(title, options);
      }
    } catch {
      // Notification display is best-effort; the in-app list is the source of truth.
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
