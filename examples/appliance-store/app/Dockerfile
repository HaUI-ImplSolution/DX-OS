# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
# The store has no npm package: Node.js runs the four modules as they are.
FROM node:24.16.0-alpine@sha256:21f403ab171f2dc89bad4dd69d7721bfd15f084ccb46cdd225f31f2bc59b5c9a
WORKDIR /srv
COPY app ./app
COPY catalog ./catalog
RUN mkdir /data && chown node:node /data
ENV APP_DB=/data/store.sqlite APP_CATALOG=/srv/catalog/products.tsv
USER node
WORKDIR /srv/app
CMD ["node", "--disable-warning=ExperimentalWarning", "server.mjs"]
