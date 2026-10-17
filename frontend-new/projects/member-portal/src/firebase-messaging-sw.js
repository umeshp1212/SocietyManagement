/*
 * Firebase Cloud Messaging background handler for the member PWA.
 *
 * Shows the delivery/visitor notification when the app is backgrounded/closed, with
 * Approve / Reject action buttons so the owner can decide straight from the
 * notification tray — no need to open the app. Action clicks call the member
 * approve/reject API using the auth token the app mirrored into IndexedDB (a service
 * worker cannot read localStorage).
 *
 * NOTE (web platform limits):
 *  - Custom notification SOUND is not possible for background web push; the OS plays
 *    the default channel sound. A distinct in-app chime is played only when the app is
 *    open (see fcm.service.ts). A custom VIBRATION pattern IS supported and set below.
 *  - Action buttons work on Android Chrome; iOS Safari ignores them and just opens the
 *    app (handled by the notificationclick fallback).
 *
 * Fill firebaseConfig with your project's web config (mirror environment.fcm).
 */
importScripts('https://www.gstatic.com/firebasejs/10.12.2/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/10.12.2/firebase-messaging-compat.js');

const firebaseConfig = {
  apiKey: 'AIzaSyDgFO-ryhMmV4lskLB7Mmd5QsFXmmOW10U',
  authDomain: 'security-gate-4a552.firebaseapp.com',
  projectId: 'security-gate-4a552',
  messagingSenderId: '343007706510',
  appId: '1:343007706510:web:1c05bc1190772b295778df'
};

const APPROVALS_URL = '/member/visitor-approvals';
const VIBRATION = [200, 100, 200, 100, 300];

try {
  if (firebaseConfig.projectId) {
    firebase.initializeApp(firebaseConfig);
    const messaging = firebase.messaging();

    messaging.onBackgroundMessage((payload) => {
      const data = payload.data || {};
      const title = (payload.notification && payload.notification.title) || 'Visitor at the gate';
      const body = (payload.notification && payload.notification.body) || 'Tap to review the request.';
      const requestId = data.visitorRequestId || '';
      const options = {
        body,
        icon: 'icons/icon-192x192.png',
        badge: 'icons/icon-96x96.png',
        vibrate: VIBRATION,
        requireInteraction: true,
        tag: 'visitor-' + requestId,
        renotify: true,
        data: { click_action: data.click_action || APPROVALS_URL, visitorRequestId: requestId },
        actions: [
          { action: 'approve', title: '\u2713 Approve' },
          { action: 'reject', title: '\u2717 Reject' }
        ]
      };
      self.registration.showNotification(title, options);
    });
  }
} catch (e) {
  // No-op: push not configured or SDK unavailable. In-app polling remains the fallback.
}

// ---- IndexedDB helpers: read the apiUrl + member token the app mirrored in ----
function readSwContext() {
  return new Promise((resolve) => {
    try {
      const req = indexedDB.open('gate-sw', 1);
      req.onupgradeneeded = () => req.result.createObjectStore('ctx');
      req.onsuccess = () => {
        try {
          const db = req.result;
          const tx = db.transaction('ctx', 'readonly');
          const store = tx.objectStore('ctx');
          const apiReq = store.get('apiUrl');
          const tokReq = store.get('memberToken');
          const out = {};
          apiReq.onsuccess = () => { out.apiUrl = apiReq.result; };
          tokReq.onsuccess = () => { out.memberToken = tokReq.result; };
          tx.oncomplete = () => { db.close(); resolve(out); };
          tx.onerror = () => { db.close(); resolve({}); };
        } catch (e) { resolve({}); }
      };
      req.onerror = () => resolve({});
    } catch (e) { resolve({}); }
  });
}

function decide(requestId, action) {
  return readSwContext().then((ctx) => {
    if (!ctx.apiUrl || !ctx.memberToken || !requestId) {
      return false;
    }
    const verb = action === 'approve' ? 'approve' : 'reject';
    return fetch(ctx.apiUrl + '/member/visitor-requests/' + requestId + '/' + verb, {
      method: 'POST',
      headers: {
        'Authorization': 'Bearer ' + ctx.memberToken,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({})
    }).then((r) => r.ok).catch(() => false);
  });
}

self.addEventListener('notificationclick', (event) => {
  const notif = event.notification;
  const requestId = (notif.data && notif.data.visitorRequestId) || '';
  const url = (notif.data && notif.data.click_action) || APPROVALS_URL;
  notif.close();

  // Approve / Reject action buttons: act in place, then confirm with a short toast
  // notification. If the API call cannot be made (no token), fall back to opening the app.
  if (event.action === 'approve' || event.action === 'reject') {
    event.waitUntil(
      decide(requestId, event.action).then((ok) => {
        if (ok) {
          return self.registration.showNotification(
            event.action === 'approve' ? 'Entry approved' : 'Entry rejected',
            { body: 'Flat owner decision recorded.', icon: 'icons/icon-192x192.png', tag: 'visitor-' + requestId, silent: true }
          );
        }
        // Could not decide from the SW → open the app so the owner can act there.
        return clients.openWindow(url);
      })
    );
    return;
  }

  // Plain tap → open/focus the approvals screen.
  event.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true }).then((wins) => {
      for (const w of wins) {
        if (w.url.includes('/member') && 'focus' in w) {
          w.focus();
          if ('navigate' in w) { w.navigate(url); }
          return;
        }
      }
      return clients.openWindow(url);
    })
  );
});
