# secrets/

Host-side secrets mounted read-only into the backend container at `/app/secrets`.

## FCM (visitor/delivery push notifications)

To enable free web-push for the delivery/visitor gate approval feature, place your
Firebase **service-account JSON** here as:

```
secrets/fcm-service-account.json
```

Then set in `.env`:

```
FCM_ENABLED=true
FCM_PROJECT_ID=your-firebase-project-id
```

The backend reads the file at `FCM_CREDENTIALS_PATH=/app/secrets/fcm-service-account.json`
(already wired in `docker-compose.yml`).

> This folder is git-ignored except for this README. **Never commit credentials.**
> When FCM is disabled or the file is absent, the gate flow still works via in-app
> polling — push is a convenience, not a requirement.
