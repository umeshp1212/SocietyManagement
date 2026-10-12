/*
 * Firebase Cloud Messaging background handler for the member PWA.
 *
 * Receives push messages when the app is in the background/closed and shows a
 * notification; tapping it opens the visitor-approvals screen. This file must be
 * served from the app root so its service-worker scope covers the whole app.
 *
 * It uses the Firebase "compat" scripts loaded from the CDN so the handler works as a
 * plain service worker without a build step. If FCM is not configured on the frontend
 * (environment.fcm.enabled = false) this worker is simply never registered.
 *
 * IMPORTANT: replace the firebaseConfig values below with your Firebase project's
 * config at deploy time (same values as environment.fcm). They are safe to expose
 * (they are public client config), but must match your project for delivery to work.
 */
importScripts('https://www.gstatic.com/firebasejs/10.12.2/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/10.12.2/firebase-messaging-compat.js');

// TODO (deploy): fill with your Firebase project config (mirror environment.fcm).
const firebaseConfig = {
  apiKey: '',
  authDomain: '',
  projectId: '',
  messagingSenderId: '',
  appId: ''
};

try {
  if (firebaseConfig.projectId) {
    firebase.initializeApp(firebaseConfig);
    const messaging = firebase.messaging();

    messaging.onBackgroundMessage((payload) => {
      const title = (payload.notification && payload.notification.title) || 'Visitor at the gate';
      const options = {
        body: (payload.notification && payload.notification.body) || 'Tap to review the request.',
        icon: 'icons/icon-192x192.png',
        data: { click_action: (payload.data && payload.data.click_action) || '/member/visitor-approvals' }
      };
      self.registration.showNotification(title, options);
    });
  }
} catch (e) {
  // No-op: push not configured or SDK unavailable. In-app polling remains the fallback.
}

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const url = (event.notification.data && event.notification.data.click_action) || '/member/visitor-approvals';
  event.waitUntil(clients.openWindow(url));
});
