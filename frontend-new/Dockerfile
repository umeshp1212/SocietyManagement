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

# Build admin portal (root /) and member portal PWA (/member/)
RUN npx ng build admin-portal --configuration production \
 && npx ng build member-portal --configuration production

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

# Nginx site config
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
