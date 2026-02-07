# EMF Web UI Dockerfile
# Multi-stage build: Node.js build -> Nginx runtime

# =============================================================================
# Stage 1: Install dependencies and build
# =============================================================================
FROM node:18-alpine AS builder

WORKDIR /app

# Copy package files for dependency caching
COPY emf-web/package.json emf-web/package-lock.json* emf-web/tsconfig.json ./
COPY emf-web/packages/sdk/package.json ./packages/sdk/
COPY emf-web/packages/components/package.json ./packages/components/
COPY emf-web/packages/plugin-sdk/package.json ./packages/plugin-sdk/

# Install dependencies
RUN npm install

# Copy source code
COPY emf-web/packages ./packages

# Build all packages
RUN npm run build

# =============================================================================
# Stage 2: Runtime with Nginx
# =============================================================================
FROM nginx:1.25-alpine AS runtime

LABEL org.opencontainers.image.title="EMF Web UI"
LABEL org.opencontainers.image.description="Admin UI for EMF Platform"
LABEL org.opencontainers.image.vendor="EMF"
LABEL org.opencontainers.image.version="1.0.0-SNAPSHOT"

# Remove default nginx config
RUN rm /etc/nginx/conf.d/default.conf

# Add custom nginx config
COPY emf-web/nginx.conf /etc/nginx/conf.d/default.conf

# Copy built assets from builder stage
# Note: Update the source path when an actual app build output directory exists
COPY --from=builder /app/packages/components/dist /usr/share/nginx/html

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
