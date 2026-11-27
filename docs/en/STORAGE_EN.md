# File storage (SeaweedFS)

An S3-compatible object store keeps the attachments of records; on the backend side it is `core.storage` (`StorageService`).
The `seaweedfs` service is declared in [`deploy/docker-compose.yml`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/deploy/docker-compose.yml) and uses the `chrislusf/seaweedfs` image, pinned by tag and digest.

## How it works

- `seaweedfs` runs `weed server` with the S3 gateway; the master, the volume and the filer share one process, and the filer opens no HTTP, so objects can be reached only through the S3 API with the keys `S3_ACCESS_KEY` / `S3_SECRET_KEY`.
- The filer's gRPC (which manages S3 users, policies and STS) accepts only calls signed with `S3_SECRET_KEY` (`WEED_JWT_FILER_SIGNING_KEY`), so another container on the compose network cannot create an S3 user by itself.
- The log of `seaweedfs` holds only warnings and errors (`-v=-1` in compose); change it to `-v=0` to see informational logs.
- The bucket `S3_BUCKET` (default `dxos-uploads`) is private, and the backend creates it at startup.
- The object key is `<uuid>.<file extension>`, with the metadata `uploaded-by` (the `sub` of the uploader) and `source`.
- The backend scans files for malware with ClamAV (`STORAGE_SCAN_HOST`, see [Install and operations](../OPERATIONS.md), Vietnamese) before handling them; an infected file is refused and never touches the store.
- The backend checks the file name and type (`engine.files.allowed-extensions`) before saving, and refuses a file with a RAR signature because the project's ClamAV cannot open RAR; clients never write to the store directly.
- The client receives a download link with an expiry (15 minutes by default, `storage.presign-ttl`), signed for the address the client used to call the API, so the file can be downloaded through any address of the gateway.
- The S3 port is not published to the host: the link goes through the gateway (the route `/<bucket>/*`, `GET` and `HEAD` only, keeping the path unchanged because the signature covers the path).

## Environment variables (`deploy/.env`)

| Variable | Meaning |
|---|---|
| `S3_ENDPOINT` | The internal address, `http://seaweedfs:8333` by default |
| `S3_ACCESS_KEY`, `S3_SECRET_KEY` | The S3 keys of the backend; the secret is at least 16 characters because it is also the gRPC signing key of the filer |
| `S3_BUCKET` | The bucket name; the file download route in `deploy/apisix/common.yaml` points at `/dxos-uploads/*`, so change the route too if you rename it |
| `STORAGE_PRESIGN_TTL` | The lifetime of a download link, `15m` by default |
| `GATEWAY_PUBLIC_URL` | The gateway address that browsers use, used to sign links when there is no request |

The data lives in `deploy/data/seaweedfs` (a bind mount) and is lost when `deploy/reset.sh` runs.
`deploy/backup.sh` backs up this folder together with the database, see [Install and operations](../OPERATIONS.md) (Vietnamese).

## Limits

- The backend uses one shared S3 key; there is no narrow-permission service account yet.
- The data files are backed up while `seaweedfs` is running; if you need a copy that is certainly consistent, stop `seaweedfs` while `backup.sh` runs.
