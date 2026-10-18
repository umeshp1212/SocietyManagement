# =============================================================
# ONBOARDING frontend image — DOMAIN-PARAMETERIZED
# -------------------------------------------------------------
# This is a SEPARATE build for onboarding a NEW society. It does NOT touch the
# existing frontend-new/Dockerfile or nginx.prod.conf. It reuses the SAME Angular
# source (build context is ./frontend-new) but serves nginx from a config that is
# rendered at container start from a single ${DOMAIN} variable — so one image
# works for any society/domain with no rebuild.
#
# Build context MUST be ./frontend-new (so the Angular source is present):
#   build:
#     context: ./frontend-new
#     dockerfile: ../onboarding/frontend.Dockerfile
#
# The nginx template + entrypoint are generated INSIDE this Dockerfile (here-docs)
# so nothing new has to live inside frontend-new/.
# =============================================================

# ---------------------------------------------------------------
# Stage 1: Build all three Angular apps (identical to the existing build)
# ---------------------------------------------------------------
FROM node:18-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npx ng build admin-portal --configuration production \
 && npx ng build member-portal --configuration production \
 && npx ng build gate-portal --configuration production

# ---------------------------------------------------------------
# Stage 2: Serve with nginx, domain injected at runtime
# ---------------------------------------------------------------
FROM nginx:1.25-alpine

RUN rm -rf /usr/share/nginx/html/*
COPY --from=build /app/dist/admin-portal/browser   /usr/share/nginx/html/admin-portal
COPY --from=build /app/dist/member-portal/browser  /usr/share/nginx/html/member
COPY --from=build /app/dist/gate-portal/browser    /usr/share/nginx/html/gate

RUN rm -f /etc/nginx/conf.d/default.conf

# ---- nginx config TEMPLATE (written here so frontend-new/ is untouched) ----
# ${DOMAIN} / ${DOMAIN_WWW} are substituted at container start by the entrypoint.
# All nginx runtime vars ($host, $uri, ...) are escaped as $$ so the here-doc and
# envsubst leave them intact.
RUN mkdir -p /etc/nginx/templates && cat > /etc/nginx/templates/society.conf.template <<'NGINX'
# HTTP -> redirect to HTTPS (ACME challenge passes through for renewals)
server {
    listen 80;
    server_name ${DOMAIN} ${DOMAIN_WWW};
    location /.well-known/acme-challenge/ { root /var/www/certbot; }
    location / { return 301 https://$host$request_uri; }
}

server {
    listen 443 ssl;
    server_name ${DOMAIN} ${DOMAIN_WWW};

    ssl_certificate     /etc/letsencrypt/live/${DOMAIN}/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/${DOMAIN}/privkey.pem;
    include             /etc/letsencrypt/options-ssl-nginx.conf;
    ssl_dhparam         /etc/letsencrypt/ssl-dhparams.pem;

    add_header X-Frame-Options SAMEORIGIN always;
    add_header X-Content-Type-Options nosniff always;
    add_header X-XSS-Protection "1; mode=block" always;

    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml text/javascript application/manifest+json;
    gzip_min_length 1000;

    resolver 127.0.0.11 valid=10s ipv6=off;

    location /api/ {
        set $backend_upstream http://backend:8080;
        proxy_pass $backend_upstream$request_uri;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 90s;
        proxy_connect_timeout 90s;
        client_max_body_size 10M;
    }

    # ---- Member PWA (/member/) ----
    location = /member/ngsw-worker.js { root /usr/share/nginx/html; add_header Cache-Control "no-cache, no-store, must-revalidate"; add_header Service-Worker-Allowed "/member/"; expires off; }
    location = /member/ngsw.json { root /usr/share/nginx/html; add_header Cache-Control "no-cache, no-store, must-revalidate"; expires off; }
    location = /member/safety-worker.js { root /usr/share/nginx/html; add_header Cache-Control "no-cache, no-store, must-revalidate"; expires off; }
    location = /member/manifest.json { root /usr/share/nginx/html; add_header Content-Type application/manifest+json; expires 1d; }
    location = /member/firebase-messaging-sw.js { root /usr/share/nginx/html; add_header Cache-Control "no-cache, no-store, must-revalidate"; add_header Service-Worker-Allowed "/member/"; expires off; }
    location /member/ {
        root /usr/share/nginx/html;
        try_files $uri $uri/ @member_index;
        location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf)$ { expires 1y; add_header Cache-Control "public, immutable"; }
    }
    location @member_index { root /usr/share/nginx/html; try_files /member/index.html =404; add_header Cache-Control "no-cache, no-store, must-revalidate"; expires 0; }

    # ---- Gate (guard) PWA (/gate/) ----
    location = /gate/ngsw-worker.js { root /usr/share/nginx/html; add_header Cache-Control "no-cache, no-store, must-revalidate"; add_header Service-Worker-Allowed "/gate/"; expires off; }
    location = /gate/ngsw.json { root /usr/share/nginx/html; add_header Cache-Control "no-cache, no-store, must-revalidate"; expires off; }
    location = /gate/safety-worker.js { root /usr/share/nginx/html; add_header Cache-Control "no-cache, no-store, must-revalidate"; expires off; }
    location = /gate/manifest.json { root /usr/share/nginx/html; add_header Content-Type application/manifest+json; expires 1d; }
    location /gate/ {
        root /usr/share/nginx/html;
        try_files $uri $uri/ @gate_index;
        location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf)$ { expires 1y; add_header Cache-Control "public, immutable"; }
    }
    location @gate_index { root /usr/share/nginx/html; try_files /gate/index.html =404; add_header Cache-Control "no-cache, no-store, must-revalidate"; expires 0; }

    # ---- Admin + Public (/) ----
    location / {
        root /usr/share/nginx/html/admin-portal;
        try_files $uri $uri/ /index.html;
        location = /index.html { add_header Cache-Control "no-cache, no-store, must-revalidate"; expires 0; }
        location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf)$ { expires 1y; add_header Cache-Control "public, immutable"; }
    }
}
NGINX

# ---- Entrypoint: render the template (DOMAIN only), then start nginx ----
RUN cat > /usr/local/bin/society-entrypoint.sh <<'SH'
#!/bin/sh
set -eu
TEMPLATE="/etc/nginx/templates/society.conf.template"
TARGET="/etc/nginx/conf.d/society.conf"
: "${DOMAIN:?DOMAIN is required (set it in onboarding/.env, e.g. DOMAIN=greenvalleychs.in)}"
: "${DOMAIN_WWW:=www.${DOMAIN}}"
export DOMAIN DOMAIN_WWW
echo "[entrypoint] Rendering nginx config for DOMAIN=${DOMAIN} DOMAIN_WWW=${DOMAIN_WWW}"
# Substitute ONLY these two placeholders; every nginx $variable is preserved.
envsubst '${DOMAIN} ${DOMAIN_WWW}' < "$TEMPLATE" > "$TARGET"
exec /docker-entrypoint.sh "$@"
SH
RUN chmod +x /usr/local/bin/society-entrypoint.sh

EXPOSE 80 443
ENTRYPOINT ["/usr/local/bin/society-entrypoint.sh"]
CMD ["nginx", "-g", "daemon off;"]
