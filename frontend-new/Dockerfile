# ---------------------------------------------------------------
# Stage 1: Build both Angular apps
# ---------------------------------------------------------------
FROM node:18-alpine AS build

WORKDIR /app

# Install dependencies first (better layer caching)
COPY package.json package-lock.json ./
RUN npm ci

# Copy the rest of the workspace
COPY . .

# Build admin portal (root /), member portal PWA (/member/) and gate portal PWA (/gate/)
RUN npx ng build admin-portal --configuration production \
 && npx ng build member-portal --configuration production \
 && npx ng build gate-portal --configuration production

# ---------------------------------------------------------------
# Stage 2: Serve both with Nginx
# ---------------------------------------------------------------
FROM nginx:1.25-alpine

# Clean default content
RUN rm -rf /usr/share/nginx/html/*

# The Angular "application" builder emits to dist/<project>/browser.
# Admin serves at the site root; the member PWA serves under /member/ — the
# on-disk directory name must match the URL path so nginx `root` resolves assets.
COPY --from=build /app/dist/admin-portal/browser   /usr/share/nginx/html/admin-portal
COPY --from=build /app/dist/member-portal/browser  /usr/share/nginx/html/member
# Gate (guard) PWA serves under /gate/ — on-disk dir name matches the URL path.
COPY --from=build /app/dist/gate-portal/browser    /usr/share/nginx/html/gate

# Production nginx config: TLS termination + both frontends + /api proxy.
# Remove the stock default so our server blocks are the only ones.
RUN rm -f /etc/nginx/conf.d/default.conf
COPY nginx.prod.conf /etc/nginx/conf.d/society.conf

EXPOSE 80 443

CMD ["nginx", "-g", "daemon off;"]
