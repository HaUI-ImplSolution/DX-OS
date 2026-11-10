# SPDX-License-Identifier: Apache-2.0
FROM node:24.16.0-bookworm-slim@sha256:2c87ef9bd3c6a3bd4b472b4bec2ce9d16354b0c574f736c476489d09f560a203 AS builder
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts --no-audit --no-fund
COPY tsconfig.json ./
COPY src ./src
RUN npm run build && npm prune --omit=dev --ignore-scripts --no-audit --no-fund

FROM node:24.16.0-bookworm-slim@sha256:2c87ef9bd3c6a3bd4b472b4bec2ce9d16354b0c574f736c476489d09f560a203 AS runtime-dependencies
WORKDIR /opt/openclaw
COPY runtime/package.json runtime/package-lock.json ./
RUN npm ci --omit=dev --ignore-scripts --no-audit --no-fund \
    && npm rebuild openclaw --ignore-scripts=false --no-audit --no-fund \
    && node node_modules/openclaw/openclaw.mjs --version \
    && npm cache clean --force

FROM node:24.16.0-bookworm-slim@sha256:2c87ef9bd3c6a3bd4b472b4bec2ce9d16354b0c574f736c476489d09f560a203
WORKDIR /app
COPY --from=builder /app/dist ./dist
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/package.json ./
COPY --from=runtime-dependencies /opt/openclaw /opt/openclaw
COPY runtime/plugin ./runtime/plugin
COPY runtime/AGENTS.md ./runtime/AGENTS.md
USER node
ENTRYPOINT ["node"]
CMD ["dist/server.js"]
