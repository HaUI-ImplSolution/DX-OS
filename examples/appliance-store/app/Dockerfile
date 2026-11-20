# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
# The store has no npm package: Node.js runs the four modules as they are.
FROM node:24.16.0-bookworm-slim@sha256:2c87ef9bd3c6a3bd4b472b4bec2ce9d16354b0c574f736c476489d09f560a203
WORKDIR /srv
COPY app ./app
COPY catalog ./catalog
RUN mkdir /data && chown node:node /data
ENV APP_DB=/data/store.sqlite APP_CATALOG=/srv/catalog/products.tsv
USER node
WORKDIR /srv/app
CMD ["node", "--disable-warning=ExperimentalWarning", "server.mjs"]
