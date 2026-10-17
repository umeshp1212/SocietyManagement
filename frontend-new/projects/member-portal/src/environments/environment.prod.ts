export const environment = {
  production: true,
  apiUrl: '/api',

  // Firebase Cloud Messaging (free web push). Leave fields blank to disable push —
  // the app then falls back to in-app polling of pending visitor requests.
  // Fill these from your Firebase project (Project settings > General / Cloud Messaging).
  fcm: {
    enabled: true,
    apiKey: 'AIzaSyDgFO-ryhMmV4lskLB7Mmd5QsFXmmOW10U',
    authDomain: 'security-gate-4a552.firebaseapp.com',
    projectId: 'security-gate-4a552',
    messagingSenderId: '343007706510',
    appId: '1:343007706510:web:1c05bc1190772b295778df',
    // Web Push certificate key pair (VAPID public key) from Cloud Messaging settings.
    vapidKey: 'BBKZwwWgH71aQtR5lqIiq76ku3W_fnjyzitwBel5LhTS3YQcxVBooKddhjRuYTYMSP0mVo3crPj_axYFMsqbTWw'
  }
};
