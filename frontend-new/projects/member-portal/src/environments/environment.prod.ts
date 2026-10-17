export const environment = {
  production: true,
  apiUrl: '/api',

  // Firebase Cloud Messaging (free web push). Leave fields blank to disable push —
  // the app then falls back to in-app polling of pending visitor requests.
  // Fill these from your Firebase project (Project settings > General / Cloud Messaging).
  fcm: {
    enabled: true,
    apiKey: 'AIzaSyDgFO-ryhMmV4lskLB7Md5QsFXmmOW10U',
    authDomain: 'security-gate-4a552',
    projectId: 'security-gate-4a552.firebasestorage.app',
    messagingSenderId: '343007706510',
    appId: '1:343007706510:1c05bc1190772b295778df',
    // Web Push certificate key pair (VAPID public key) from Cloud Messaging settings.
    vapidKey: ''
  }
};
