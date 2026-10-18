#!/usr/bin/env bash
# =============================================================
# ONBOARDING Let's Encrypt auto-renewal setup (new society stack).
# Separate from scripts/setup-cert-renewal.sh (the live stack's script).
#
# The onboarding frontend container holds ports 80/443, so renewals use the
# WEBROOT method served by that container, then reload its nginx.
#
# Run ONCE on the new server (as root), from the PROJECT ROOT:
#   sudo bash onboarding/setup-cert-renewal.sh
# The domain is read from onboarding/.env (DOMAIN=...), or pass it explicitly:
#   sudo DOMAIN=greenvalleychs.in bash onboarding/setup-cert-renewal.sh
# =============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.yml"
ENV_FILE="${SCRIPT_DIR}/.env"
# Onboarding compose sets `name: society-onboarding`, so volumes are prefixed with it.
VOLUME_NAME="${CERTBOT_WEBROOT_VOLUME:-society-onboarding_certbot_webroot}"

# Resolve the domain: env var / CLI arg / onboarding .env
DOMAIN_PRIMARY="${DOMAIN:-${1:-}}"
if [[ -z "${DOMAIN_PRIMARY}" && -f "${ENV_FILE}" ]]; then
  DOMAIN_PRIMARY="$(grep -E '^DOMAIN=' "${ENV_FILE}" | head -n1 | cut -d= -f2- | tr -d '"'"'"' ')"
fi
if [[ -z "${DOMAIN_PRIMARY}" ]]; then
  echo "ERROR: could not determine the domain." >&2
  echo "Set DOMAIN in ${ENV_FILE}, or run: sudo DOMAIN=yourdomain.in bash onboarding/setup-cert-renewal.sh" >&2
  exit 1
fi
DOMAIN_WWW="${DOMAIN_WWW:-www.${DOMAIN_PRIMARY}}"

echo "Compose file : $COMPOSE_FILE"
echo "Cert domain  : $DOMAIN_PRIMARY (+ $DOMAIN_WWW)"

# 1) Resolve the host path of the certbot webroot volume.
if ! WEBROOT_PATH="$(docker volume inspect "$VOLUME_NAME" --format '{{ .Mountpoint }}' 2>/dev/null)"; then
  echo "ERROR: could not inspect volume '$VOLUME_NAME'." >&2
  echo "Start the stack once first, or list volumes: docker volume ls (look for *_certbot_webroot)" >&2
  echo "Then re-run with: CERTBOT_WEBROOT_VOLUME=<name> sudo bash onboarding/setup-cert-renewal.sh" >&2
  exit 1
fi
echo "Webroot path : $WEBROOT_PATH"

# 2) Switch renewal to webroot (dry-run first, then persist).
echo ">> Switching renewal method to webroot and testing (dry-run first)..."
certbot certonly --webroot -w "$WEBROOT_PATH" \
  -d "$DOMAIN_PRIMARY" -d "$DOMAIN_WWW" \
  --dry-run

echo ">> Dry-run OK. Persisting webroot config..."
certbot certonly --webroot -w "$WEBROOT_PATH" \
  -d "$DOMAIN_PRIMARY" -d "$DOMAIN_WWW" \
  --keep-until-expiring

# 3) Install a deploy hook so the onboarding frontend nginx reloads after renewals.
HOOK_DIR="/etc/letsencrypt/renewal-hooks/deploy"
mkdir -p "$HOOK_DIR"
cat > "${HOOK_DIR}/reload-onboarding-nginx.sh" <<EOF
#!/usr/bin/env bash
docker compose -f "${COMPOSE_FILE}" exec -T frontend nginx -s reload
EOF
chmod +x "${HOOK_DIR}/reload-onboarding-nginx.sh"
echo "Installed deploy hook: ${HOOK_DIR}/reload-onboarding-nginx.sh"

# 4) Final end-to-end verification (no changes made).
echo ">> Final verification dry-run..."
certbot renew --dry-run

echo
echo "============================================================"
echo "Onboarding auto-renewal configured for ${DOMAIN_PRIMARY}."
echo " - Renewals use webroot: $WEBROOT_PATH"
echo " - After each renewal, the onboarding frontend nginx is reloaded."
echo "Check the timer:   systemctl list-timers | grep certbot"
echo "============================================================"
