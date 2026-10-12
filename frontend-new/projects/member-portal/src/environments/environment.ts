export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api',

  // Firebase Cloud Messaging (free web push). Leave fields blank to disable push —
  // the app then falls back to in-app polling of pending visitor requests.
  // Fill these from your Firebase project (Project settings > General / Cloud Messaging).
  fcm: {
    enabled: false,
    apiKey: '',
    authDomain: '',
    projectId: '',
    messagingSenderId: '',
    appId: '',
    // Web Push certificate key pair (VAPID public key) from Cloud Messaging settings.
    vapidKey: ''
  }
};
