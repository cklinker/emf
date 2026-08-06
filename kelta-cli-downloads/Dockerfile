# CLI downloads service: compiles the kelta CLI for every platform and serves
# the binaries + release manifest + install scripts from nginx.
# Build context must be the repo root (same as other Dockerfiles).
#
# The binaries are cross-compiled INSIDE the image build (bun compiles all
# five targets from linux), so this image slots into the shared build matrix
# with no special workflow steps — only the CLI_VERSION/GIT_SHA build args.

# =============================================================================
# Stage 1: install workspace deps and build the SDK the CLI links against
# =============================================================================
FROM harbor.rzware.com/dockerhub-cache/library/node:20-alpine AS builder

WORKDIR /app/kelta-web
COPY kelta-web/package.json kelta-web/package-lock.json* kelta-web/tsconfig.json ./
COPY kelta-web/packages/sdk/package.json ./packages/sdk/
COPY kelta-web/packages/components/package.json ./packages/components/
COPY kelta-web/packages/plugin-sdk/package.json ./packages/plugin-sdk/
COPY kelta-web/packages/formula/package.json ./packages/formula/
COPY kelta-web/packages/cli/package.json ./packages/cli/
RUN npm install

COPY kelta-web/packages ./packages
COPY kelta-web/scripts ./scripts
RUN npm run build --workspace=packages/formula \
 && npm run build --workspace=packages/sdk

# =============================================================================
# Stage 2: cross-compile the five release binaries + manifest with bun
# =============================================================================
FROM harbor.rzware.com/dockerhub-cache/oven/bun:1.2.19 AS compiler

ARG CLI_VERSION=0.0.0
ARG GIT_SHA=unknown
ARG DOWNLOADS_BASE_URL=https://downloads.kelta.io

WORKDIR /app/kelta-web
COPY --from=builder /app/kelta-web ./
RUN bun scripts/build-binaries.mjs \
      --version "${CLI_VERSION}" \
      --sha "${GIT_SHA}" \
      --base-url "${DOWNLOADS_BASE_URL}" \
      --out /out

# =============================================================================
# Stage 3: nginx static server
# =============================================================================
FROM harbor.rzware.com/dockerhub-cache/library/nginx:1.25-alpine

COPY kelta-cli-downloads/nginx.conf /etc/nginx/conf.d/default.conf
COPY kelta-cli-downloads/install.sh /usr/share/nginx/html/cli/install.sh
COPY kelta-cli-downloads/install.ps1 /usr/share/nginx/html/cli/install.ps1
COPY --from=compiler /out/ /usr/share/nginx/html/cli/

EXPOSE 8080
