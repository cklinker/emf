# Multi-stage build for Kelta Admin UI
# Build context must be the repo root (same as other Dockerfiles)

# =============================================================================
# Stage 1: Install dependencies and build
# =============================================================================
FROM node:18-alpine AS builder

WORKDIR /app

# Copy kelta-web packages (local dependencies for kelta-ui)
COPY kelta-web/package.json kelta-web/package-lock.json* kelta-web/tsconfig.json ./kelta-web/
COPY kelta-web/packages/sdk/package.json ./kelta-web/packages/sdk/
COPY kelta-web/packages/components/package.json ./kelta-web/packages/components/
COPY kelta-web/packages/plugin-sdk/package.json ./kelta-web/packages/plugin-sdk/

# Install kelta-web dependencies
WORKDIR /app/kelta-web
RUN npm install

# Copy kelta-web source and build SDK + plugin-sdk
COPY kelta-web/packages ./packages
RUN npm run build --workspace=packages/sdk && npm run build --workspace=packages/plugin-sdk

# Copy kelta-ui package files
WORKDIR /app/kelta-ui/app
COPY kelta-ui/app/package.json kelta-ui/app/package-lock.json* ./

# Install kelta-ui dependencies (file: refs resolve to /app/kelta-web/packages/*)
RUN npm install

# Copy kelta-ui source
COPY kelta-ui/app/tsconfig.json kelta-ui/app/tsconfig.app.json kelta-ui/app/tsconfig.node.json ./
COPY kelta-ui/app/vite.config.ts ./
COPY kelta-ui/app/index.html ./
COPY kelta-ui/app/src ./src
COPY kelta-ui/app/public ./public

# Set API base URL for production (frontend calls gateway directly, no nginx proxy)
ARG VITE_API_BASE_URL=https://emf.rzware.com
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

# Build the Vite app (skip tsc — CI validates types separately)
RUN npx vite build

# =============================================================================
# Stage 2: Runtime with Nginx
# =============================================================================
FROM nginx:1.25-alpine

LABEL org.opencontainers.image.title="Kelta Admin UI"
LABEL org.opencontainers.image.description="Admin/Builder UI for Kelta Platform"
LABEL org.opencontainers.image.vendor="Kelta"

# Remove default nginx config
RUN rm /etc/nginx/conf.d/default.conf

# Add custom nginx config
COPY kelta-ui/nginx.conf /etc/nginx/conf.d/default.conf

# Copy built assets from builder stage
COPY --from=builder /app/kelta-ui/app/dist /usr/share/nginx/html

# Create non-root user
RUN addgroup -g 1000 -S kelta && \
    adduser -u 1000 -S kelta -G kelta && \
    chown -R kelta:kelta /usr/share/nginx/html && \
    chown -R kelta:kelta /var/cache/nginx && \
    chown -R kelta:kelta /var/log/nginx && \
    touch /var/run/nginx.pid && \
    chown -R kelta:kelta /var/run/nginx.pid

USER kelta

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget -qO- http://localhost:8080/health || exit 1

CMD ["nginx", "-g", "daemon off;"]
