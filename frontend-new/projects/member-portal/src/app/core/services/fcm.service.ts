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
  private alertCtx: AudioContext | null = null;
  private alertTimer: number | null = null;

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

      // Mirror the member auth token + API base into IndexedDB so the background
      // service worker can act on notification Approve/Reject buttons without the app
      // being open (a service worker cannot read localStorage).
      await this.storeSwContext();

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
   * Browsers block audio until the user interacts with the page at least once. Call this
   * once (e.g. from the app shell) so the first real alert can actually play. Attaches a
   * one-time listener that resumes a throwaway AudioContext on the first tap/click.
   */
  unlockAudioOnFirstGesture(): void {
    const unlock = () => {
      try {
        const Ctx = (window as any).AudioContext || (window as any).webkitAudioContext;
        if (Ctx) {
          const ctx = new Ctx();
          if (ctx.state === 'suspended') {
            ctx.resume().finally(() => ctx.close());
          } else {
            ctx.close();
          }
        }
      } catch {
        /* ignore */
      }
      window.removeEventListener('pointerdown', unlock);
      window.removeEventListener('keydown', unlock);
    };
    window.addEventListener('pointerdown', unlock, { once: true });
    window.addEventListener('keydown', unlock, { once: true });
  }

  /**
   * Displays a notification for a message received while the app is in the foreground.
   * Uses the FCM service-worker registration (so a tap can reopen/focus the app) when
   * available, falling back to the Notification constructor.
   */
  private showForegroundNotification(payload: any): void {
    try {
      // Play a distinct in-app chime (only possible while the page is open; the OS
      // controls the sound for background push, which the web platform cannot override).
      this.playChime();

      if (Notification.permission !== 'granted') {
        return;
      }
      // Data-only messages: title/body are in data (the backend omits the notification
      // block so the SW/foreground handler can render action buttons).
      const title = payload?.data?.title || payload?.notification?.title || 'Visitor at the gate';
      const body = payload?.data?.body || payload?.notification?.body || 'Tap to review the request.';
      const clickAction = payload?.data?.click_action || '/member/visitor-approvals';
      const requestId = payload?.data?.visitorRequestId || '';
      const options: any = {
        body,
        icon: 'icons/icon-192x192.png',
        badge: 'icons/icon-96x96.png',
        vibrate: [400, 200, 400, 200, 600, 200, 400],   // strong, long alert pattern
        requireInteraction: true,                        // stays until the owner acts
        tag: 'visitor-' + requestId,
        renotify: true,
        data: { click_action: clickAction, visitorRequestId: requestId },
        actions: [
          { action: 'approve', title: '✓ Approve' },
          { action: 'reject', title: '✕ Reject' }
        ]
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

  /**
   * Plays a loud, attention-grabbing "doorbell ring" using the Web Audio API and loops
   * it several times so the owner is clearly alerted (foreground only — the OS controls
   * the sound for background push). Also fires a strong repeating vibration. The ring
   * stops automatically after a few cycles, or immediately when {@link #stopAlert} is
   * called (e.g. the owner opened the approvals screen / acted).
   */
  private playChime(): void {
    try {
      this.stopAlert(); // never stack two rings
      const Ctx = (window as any).AudioContext || (window as any).webkitAudioContext;
      if (!Ctx) {
        return;
      }
      const ctx: AudioContext = new Ctx();
      this.alertCtx = ctx;

      // One "ding-dong-dong" burst — brighter + louder than the old single chime.
      const burst = (startAt: number) => {
        const tones = [
          { f: 988, t: 0.00, d: 0.22 },  // B5
          { f: 740, t: 0.22, d: 0.22 },  // F#5
          { f: 988, t: 0.46, d: 0.30 }   // B5
        ];
        for (const { f, t, d } of tones) {
          const osc = ctx.createOscillator();
          const gain = ctx.createGain();
          osc.type = 'triangle';            // richer/louder than a pure sine
          osc.frequency.value = f;
          const s = startAt + t;
          gain.gain.setValueAtTime(0.0001, s);
          gain.gain.exponentialRampToValueAtTime(0.6, s + 0.02);   // louder peak
          gain.gain.exponentialRampToValueAtTime(0.0001, s + d);
          osc.connect(gain);
          gain.connect(ctx.destination);
          osc.start(s);
          osc.stop(s + d + 0.02);
        }
      };

      const CYCLES = 4;           // ring ~4 times
      const CYCLE_GAP = 1.1;      // seconds between bursts
      for (let i = 0; i < CYCLES; i++) {
        burst(ctx.currentTime + i * CYCLE_GAP);
      }

      // Strong repeating vibration alongside the ring (where supported).
      if ('vibrate' in navigator) {
        navigator.vibrate([400, 200, 400, 200, 600]);
      }

      // Auto-close the audio context after the ring finishes.
      this.alertTimer = window.setTimeout(() => this.stopAlert(), (CYCLES * CYCLE_GAP + 1) * 1000);
    } catch {
      /* audio is best-effort */
    }
  }

  /**
   * Public trigger for the loud alert ring — used by the approvals screen when a new
   * pending request appears while the app is open. Safe to call repeatedly (it resets
   * any in-progress ring first).
   */
  ringAlert(): void {
    this.playChime();
  }

  /** Stops any in-progress alert ring/vibration. */
  stopAlert(): void {
    try {
      if (this.alertTimer) {
        clearTimeout(this.alertTimer);
        this.alertTimer = null;
      }
      if (this.alertCtx) {
        this.alertCtx.close();
        this.alertCtx = null;
      }
      if ('vibrate' in navigator) {
        navigator.vibrate(0);
      }
    } catch {
      /* best-effort */
    }
  }

  /**
   * Mirrors the member auth token and API base URL into IndexedDB so the background
   * service worker can call the approve/reject endpoints when the owner taps a
   * notification action button (a service worker cannot read localStorage).
   */
  private storeSwContext(): Promise<void> {
    return new Promise<void>((resolve) => {
      try {
        const token = localStorage.getItem('member_access_token') || '';
        const req = indexedDB.open('gate-sw', 1);
        req.onupgradeneeded = () => req.result.createObjectStore('ctx');
        req.onsuccess = () => {
          try {
            const db = req.result;
            const tx = db.transaction('ctx', 'readwrite');
            const store = tx.objectStore('ctx');
            store.put(environment.apiUrl, 'apiUrl');
            store.put(token, 'memberToken');
            tx.oncomplete = () => { db.close(); resolve(); };
            tx.onerror = () => { db.close(); resolve(); };
          } catch {
            resolve();
          }
        };
        req.onerror = () => resolve();
      } catch {
        resolve();
      }
    });
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
