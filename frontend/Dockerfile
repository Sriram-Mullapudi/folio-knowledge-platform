# ── Build stage ───────────────────────────────────────────────────────
FROM node:20-alpine AS build
WORKDIR /app

COPY package.json package-lock.json* ./
# Use a clean, reproducible install. Falls back to `npm install` if no lockfile.
RUN npm ci || npm install

COPY . .
RUN npm run build

# ── Runtime stage ─────────────────────────────────────────────────────
FROM nginx:1.27-alpine AS runtime

# SPA routing + /api and /actuator reverse proxy to the backend.
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -qO- http://localhost:80/ >/dev/null 2>&1 || exit 1
