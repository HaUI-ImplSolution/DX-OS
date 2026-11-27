# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
FROM nginx:1.30-alpine-slim@sha256:e9eaa7298465eab06d618c51556fdcf3fb5061f9ff40bd28ee898fbd7ee7f900
COPY index.html /usr/share/nginx/html/index.html
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY licenses/ /licenses/
EXPOSE 80
