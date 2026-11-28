# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
FROM node:24.22.3-alpine@sha256:116b800c3754b440ee0aa3ed2f20f5bf3d99a554bfd9adeea09ea8017f6e85f5 AS builder
WORKDIR /app
COPY assistant/package.json assistant/package-lock.json ./
RUN npm ci --ignore-scripts --no-audit --no-fund
COPY assistant/tsconfig.json ./
COPY assistant/src ./src
COPY assistant/scripts/third-party-licenses.mjs ./scripts/
RUN npm run build && npm prune --omit=dev --ignore-scripts --no-audit --no-fund \
    && node scripts/third-party-licenses.mjs . third-party-licenses.txt

FROM node:24.22.3-alpine@sha256:116b800c3754b440ee0aa3ed2f20f5bf3d99a554bfd9adeea09ea8017f6e85f5 AS runtime
RUN rm -rf /usr/local/lib/node_modules /opt/yarn-v1.22.22 \
    /usr/local/bin/npm /usr/local/bin/npx /usr/local/bin/corepack /usr/local/bin/yarn /usr/local/bin/yarnpkg
WORKDIR /app
COPY --from=builder /app/dist ./dist
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/package.json ./
COPY --from=builder /app/third-party-licenses.txt /licenses/THIRD-PARTY-LICENSES.txt
COPY assistant/licenses/ /licenses/
COPY --chmod=0444 tools/classifier/model/onestop.json tools/classifier/model/support.json tools/classifier/model/clinic.json ./classifiers/
RUN chmod 0755 ./classifiers

# One flat layer, so the removed npm, corepack and yarn files are not left in a lower layer.
FROM scratch
LABEL org.opencontainers.image.licenses="Apache-2.0" \
      org.opencontainers.image.source="https://github.com/HaUI-ImplSolution/DX-OS"
COPY --from=runtime / /
ENV PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin NODE_VERSION=24.16.0
WORKDIR /app
USER node
ENTRYPOINT ["node"]
CMD ["dist/server.js"]
