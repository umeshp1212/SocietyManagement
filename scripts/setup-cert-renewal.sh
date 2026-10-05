#!/usr/bin/env bash
# =============================================================
# One-time setup for Let's Encrypt auto-renewal with the
# Dockerized nginx (frontend container holds ports 80/443).
#
# Why not plain `certbot renew`?
#   The certs were first issued with `--standalone`, which needs to bind
#   port 80. That port is now owned by the frontend container, so standalone
#   renewal would fail. We switch the renewal to the WEBROOT method, served
#   by the running container's nginx at /.well-known/acme-challenge/, and
#   reload nginx inside the container after each successful renewal.
#
# Run this ONCE on the server (as root), from the project root:
#   sudo bash scripts/setup-cert-renewal.sh
# =============================================================

set -euo pipefail

DOMAIN_PRIMARY="ppvcd.in"
COMPOSE_FILE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/docker-compose.yml"
VOLUME_NAME="${CERTBOT_WEBROOT_VOLUME:-society-management_certbot_webroot}"

echo "Compose file : $COMPOSE_FILE"
echo "Cert domain  : $DOMAIN_PRIMARY"

# 1) Resolve the host path of the certbot webroot volume that the frontend
#    container serves the ACME challenge from.
if ! WEBROOT_PATH="$(docker volume inspect "$VOLUME_NAME" --format '{{ .Mountpoint }}' 2>/dev/null)"; then
  echo "ERROR: could not inspect volume '$VOLUME_NAME'." >&2
  echo "List volumes with: docker volume ls   (look for *_certbot_webroot)" >&2
  echo "Then re-run with:  CERTBOT_WEBROOT_VOLUME=<name> sudo bash scripts/setup-cert-renewal.sh" >&2
  exit 1
fi
echo "Webroot path : $WEBROOT_PATH"

# 2) Rewrite the renewal config for the domain to use webroot + our webroot path,
#    by doing one webroot renewal with --force-renewal flags that certbot then
#    persists into /etc/letsencrypt/renewal/<domain>.conf for all future renewals.
echo ">> Switching renewal method to webroot and testing (dry-run first)..."
certbot certonly --webroot -w "$WEBROOT_PATH" \
  -d "$DOMAIN_PRIMARY" -d "www.${DOMAIN_PRIMARY}" \
  --dry-run

echo ">> Dry-run OK. Performing the real re-authorization to persist webroot config..."
certbot certonly --webroot -w "$WEBROOT_PATH" \
  -d "$DOMAIN_PRIMARY" -d "www.${DOMAIN_PRIMARY}" \
  --keep-until-expiring

# 3) Install a renewal deploy-hook so nginx in the container reloads after renewals.
HOOK_DIR="/etc/letsencrypt/renewal-hooks/deploy"
mkdir -p "$HOOK_DIR"
cat > "${HOOK_DIR}/reload-society-nginx.sh" <<EOF
#!/usr/bin/env bash
# Reload the frontend container's nginx so it picks up the renewed cert.
docker compose -f "${COMPOSE_FILE}" exec -T frontend nginx -s reload
EOF
chmod +x "${HOOK_DIR}/reload-society-nginx.sh"
echo "Installed deploy hook: ${HOOK_DIR}/reload-society-nginx.sh"

# 4) Verify the whole renewal path end-to-end (no changes made).
echo ">> Final verification dry-run (exercises webroot + would run deploy hook)..."
certbot renew --dry-run

echo
echo "============================================================"
echo "Auto-renewal is configured."
echo " - certbot's systemd timer (or cron) runs 'certbot renew' twice daily."
echo " - Renewals use webroot: $WEBROOT_PATH"
echo " - After each renewal, the frontend container nginx is reloaded."
echo
echo "Check the timer:   systemctl list-timers | grep certbot"
echo "Manual test later: sudo certbot renew --dry-run"
echo "============================================================"
