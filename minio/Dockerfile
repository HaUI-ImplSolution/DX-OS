# Official MinIO images are no longer published, so the server is built from the pinned release
# tag of the upstream source (AGPL-3.0).
FROM golang:1.24-alpine AS builder
ARG MINIO_VERSION=RELEASE.2025-10-15T17-29-55Z
RUN apk add --no-cache git
RUN CGO_ENABLED=0 go install -trimpath -ldflags "-s -w" github.com/minio/minio@${MINIO_VERSION}

FROM alpine:3.20
RUN apk add --no-cache ca-certificates curl
COPY --from=builder /go/bin/minio /usr/local/bin/minio
EXPOSE 9000
ENTRYPOINT ["minio"]
