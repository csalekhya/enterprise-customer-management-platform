# syntax=docker/dockerfile:1.7
# ---------------------------------------------------------------------------------------
# Stage 1 - build the static bundle
# ---------------------------------------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /app

# Dependencies resolve in their own layer, reused until the manifest changes. `npm ci`
# when a lockfile is present (reproducible), `npm install` otherwise.
COPY package.json package-lock.json* ./
RUN if [ -f package-lock.json ]; then \
      npm ci --no-audit --no-fund; \
    else \
      npm install --no-audit --no-fund; \
    fi

COPY tsconfig.json tsconfig.node.json vite.config.ts tailwind.config.js postcss.config.js index.html ./
COPY src ./src

RUN npm run build

# ---------------------------------------------------------------------------------------
# Stage 2 - serve
# The runtime image carries no Node, no npm and no source: just nginx and ~1 MB of
# pre-compressed assets.
# ---------------------------------------------------------------------------------------
FROM nginx:1.27-alpine AS runtime

RUN rm -rf /usr/share/nginx/html/*

# Placed in templates/ so the official entrypoint runs envsubst over it at start-up: the
# API upstream is then a deploy-time variable (compose service name locally, service
# discovery name on ECS) instead of something baked into the image.
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html

# nginx drops its workers to the unprivileged `nginx` user; only the master, which binds
# port 80, runs as root.
ENV API_UPSTREAM=api:8080

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -q --spider http://localhost/healthz || exit 1

CMD ["nginx", "-g", "daemon off;"]
