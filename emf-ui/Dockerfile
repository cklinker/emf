# Multi-stage build for EMF Admin UI
# Build context must be the repo root (same as other Dockerfiles)

# =============================================================================
# Stage 1: Install dependencies and build
# =============================================================================
FROM node:18-alpine AS builder

WORKDIR /app

# Copy emf-web packages (local dependencies for emf-ui)
COPY emf-web/package.json emf-web/package-lock.json* emf-web/tsconfig.json ./emf-web/
COPY emf-web/packages/sdk/package.json ./emf-web/packages/sdk/
COPY emf-web/packages/components/package.json ./emf-web/packages/components/
COPY emf-web/packages/plugin-sdk/package.json ./emf-web/packages/plugin-sdk/

# Install emf-web dependencies
WORKDIR /app/emf-web
RUN npm install

# Copy emf-web source and build SDK only (components/plugin-sdk are consumed as source)
COPY emf-web/packages ./packages
RUN npm run build --workspace=packages/sdk

# Copy emf-ui package files
WORKDIR /app/emf-ui/app
COPY emf-ui/app/package.json emf-ui/app/package-lock.json* ./

# Install emf-ui dependencies (file: refs resolve to /app/emf-web/packages/*)
RUN npm install

# Copy emf-ui source
COPY emf-ui/app/tsconfig.json emf-ui/app/tsconfig.app.json emf-ui/app/tsconfig.node.json ./
COPY emf-ui/app/vite.config.ts ./
COPY emf-ui/app/index.html ./
COPY emf-ui/app/src ./src
COPY emf-ui/app/public ./public

# Set API base URL for production (frontend calls gateway directly, no nginx proxy)
ARG VITE_API_BASE_URL=https://emf.rzware.com
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

# Build the Vite app (skip tsc — CI validates types separately)
RUN npx vite build

# =============================================================================
# Stage 2: Runtime with Nginx
# =============================================================================
FROM nginx:1.25-alpine

LABEL org.opencontainers.image.title="EMF Admin UI"
LABEL org.opencontainers.image.description="Admin/Builder UI for EMF Platform"
LABEL org.opencontainers.image.vendor="EMF"

# Remove default nginx config
RUN rm /etc/nginx/conf.d/default.conf

# Add custom nginx config
COPY emf-ui/nginx.conf /etc/nginx/conf.d/default.conf

# Copy built assets from builder stage
COPY --from=builder /app/emf-ui/app/dist /usr/share/nginx/html

# Create non-root user
RUN addgroup -g 1000 -S emf && \
    adduser -u 1000 -S emf -G emf && \
    chown -R emf:emf /usr/share/nginx/html && \
    chown -R emf:emf /var/cache/nginx && \
    chown -R emf:emf /var/log/nginx && \
    touch /var/run/nginx.pid && \
    chown -R emf:emf /var/run/nginx.pid

USER emf

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget -qO- http://localhost:8080/health || exit 1

CMD ["nginx", "-g", "daemon off;"]
