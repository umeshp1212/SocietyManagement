# onboarding/secrets/

Optional. Only needed if you enable Firebase Cloud Messaging push for the new
society (`FCM_ENABLED=true` in `onboarding/.env`).

Place the Firebase service-account JSON here:

```
onboarding/secrets/fcm-service-account.json
```

The onboarding backend mounts this directory read-only at `/app/secrets` and reads
the file from `FCM_CREDENTIALS_PATH=/app/secrets/fcm-service-account.json`.

Do NOT commit real credentials. Keep this file out of git.
