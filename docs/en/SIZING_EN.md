# Sizing and concurrent load

This document answers two questions: how many concurrent users the core can take, and how many cores the smallest configuration needs to meet the published threshold.
The figures were measured by `e2e/api/load_test.py` on a temporary stack, on 2026-11-12.
Every figure is a lower bound because the load generator runs on the same machine as the system being measured.

## The published threshold

A concurrency level passes when the p95 of all requests is under 500 ms and fewer than 1% of requests fail.
p99 is only reported and is not used to decide a pass.

## How it was measured

- The measuring machine has 12 cores and 30 GB of RAM; the 12-core run uses the whole machine.
- The 8, 4 and 2-core levels pin every container to the core range `0` to `N-1` with `cpuset`, and the load generator is pinned outside that range.
- At the 12-core level only, the load generator shares cores with the system and uses 0.04 to 0.33 of a core.
- The memory limits of every configuration, about 4.7 GB in total: PostgreSQL, the backend and Keycloak 1 GB; APISIX, monitor and SeaweedFS 512 MB; frontend and portal 64 MB.
- One extra run at 2 cores uses smaller limits, about 3.06 GB in total: backend and Keycloak 768 MB, PostgreSQL 512 MB, APISIX 384 MB, SeaweedFS and monitor 256 MB, frontend and portal 32 MB.
- The measured model has one entity `ticket` with the `work_item` workflow family, 10 submitters and 5 handlers.
- Each virtual user loops: create a record, list the open records with a filter, take the first action of the workflow, read the history, list their own records.
- Before each concurrency level, the data is brought back to exactly 10,000 records; each level warms up for 30 seconds and then measures for 60 seconds.
- Each virtual user gets a token once and reuses it; sign-in is measured separately.
- The rate limits of the API gateway and of the core service are switched off on the temporary stack, so the figures are the capacity of the core, not the threshold for one IP address.

## Results by number of cores

Each cell gives the throughput (requests per second), then p50/p95/p99 in ms.
Errors are 0% in every cell.
The bold row is the largest level that still passes the threshold.

### 12 cores

| Concurrency | Requests/second | p50 / p95 / p99 (ms) | Pass |
|---|---|---|---|
| 1 | 113 | 6 / 16 / 88 | yes |
| 10 | 800 | 10 / 26 / 42 | yes |
| 25 | 683 | 28 / 100 / 164 | yes |
| 50 | 828 | 52 / 134 / 285 | yes |
| 75 | 755 | 88 / 224 / 344 | yes |
| **100** | 833 | 111 / 244 / 487 | yes |
| 200 | 714 | 244 / 627 / 901 | no |

### 8 cores

| Concurrency | Requests/second | p50 / p95 / p99 (ms) | Pass |
|---|---|---|---|
| 1 | 173 | 5 / 12 / 14 | yes |
| 10 | 778 | 10 / 28 / 86 | yes |
| 25 | 656 | 32 / 83 / 162 | yes |
| 50 | 807 | 57 / 125 / 222 | yes |
| 75 | 686 | 98 / 241 / 396 | yes |
| **100** | 750 | 122 / 287 / 505 | yes |
| 200 | 694 | 248 / 656 / 1042 | no |

### 4 cores

| Concurrency | Requests/second | p50 / p95 / p99 (ms) | Pass |
|---|---|---|---|
| 1 | 116 | 5 / 13 / 98 | yes |
| 10 | 676 | 11 / 37 / 47 | yes |
| 25 | 503 | 42 / 128 / 198 | yes |
| 50 | 643 | 71 / 159 / 255 | yes |
| 75 | 518 | 127 / 324 / 449 | yes |
| **100** | 585 | 160 / 367 / 512 | yes |
| 200 | 587 | 310 / 733 / 954 | no |

### 2 cores

| Concurrency | Requests/second | p50 / p95 / p99 (ms) | Pass |
|---|---|---|---|
| 1 | 143 | 5 / 12 / 74 | yes |
| 10 | 366 | 18 / 78 / 105 | yes |
| 25 | 331 | 67 / 160 / 239 | yes |
| 50 | 354 | 132 / 287 / 366 | yes |
| **75** | 348 | 207 / 426 / 568 | yes |
| 100 | 354 | 273 / 568 / 777 | no |
| 200 | 349 | 560 / 1198 / 1664 | no |

## The smallest configuration

The smallest configuration measured is 2 cores; it passes the threshold up to 75 concurrent users with a peak throughput of about 350 requests per second.
From 4 cores up it passes the threshold up to 100 concurrent users; the breaking point of every configuration lies between 100 and 200.

| Cores | Largest concurrency that still passes | p95 at that level (ms) | Peak throughput (requests/second) |
|---|---|---|---|
| 12 | 100 | 244 | about 830 |
| 8 | 100 | 287 | about 800 |
| 4 | 100 | 367 | about 680 |
| 2 | 75 | 426 | about 365 |

## Resources

CPU is taken from the `cpu.stat` of the cgroup, expressed as the average number of cores really used at 100 concurrent users.

| Cores | PostgreSQL | Backend | APISIX | Total |
|---|---|---|---|---|
| 12 | 3.2 | 2.5 | 1.0 | about 6.7 |
| 8 | 2.8 | 2.1 | 0.8 | about 5.7 |
| 4 | 1.6 | 1.35 | 0.5 | about 3.5 |
| 2 | 0.85 to 1.0 | 0.75 | 0.29 | about 1.9 |

- At 2 cores, the CPU is used up (about 1.9 of 2) from 10 concurrent users on, and the throughput does not rise any further.
- At 4 cores and below, the cores are the limit.
- At 8 and 12 cores, the throughput stays at about 800 requests per second even though only about 6 cores are used.
- The default database connection pool of 10 connections accounts for only a small part of this plateau; the rest of the cause has not been verified, see the connection pool section below.
- The peak RAM in every configuration: backend 555 to 583 MiB, Keycloak 496 to 591 MiB, APISIX 363 to 382 MiB, PostgreSQL 240 to 289 MiB, SeaweedFS 117 to 140 MiB.
- In every measurement the peak RAM stays under the limit that was set.
- Adding the highest peaks of the five main services (backend 583, Keycloak 591, APISIX 382, PostgreSQL 289, SeaweedFS 140 MiB), the real RAM used is about 2.0 GB.

### Smaller memory limits, 2 cores

The configuration with memory limits of about 3.06 GB gives the same result as the 1 GB configuration at 2 cores: it passes up to 75 concurrent users and breaks at 100.
No container was stopped for lack of memory or restarted, and the backend log has no `OutOfMemoryError`.
The peak RAM in this run: backend 494 MiB, Keycloak 476, APISIX 356, PostgreSQL 242, SeaweedFS 111.

| Concurrency | Requests/second | p50 / p95 / p99 (ms) | Pass |
|---|---|---|---|
| 10 | 243 | 19 / 90 / 691 | yes |
| 50 | 338 | 136 / 308 / 404 | yes |
| **75** | 326 | 220 / 460 / 611 | yes |
| 100 | 355 | 271 / 571 / 774 | no |

The p95 at 75 concurrent users is 460 ms, closer to the threshold than the 426 ms of the 1 GB configuration; it is not clear whether this difference is measurement noise.
Sign-in at levels 1, 10 and 50: 22, 31 and 34 per second, p95 of 49, 341 and 1634 ms, with no errors.
The variables that set the limits are `LOAD_MEM_BACKEND`, `LOAD_MEM_KEYCLOAK`, `LOAD_MEM_POSTGRES`, `LOAD_MEM_APISIX`, `LOAD_MEM_SEAWEEDFS`, `LOAD_MEM_MONITOR`, `LOAD_MEM_FRONTEND` and `LOAD_MEM_PORTAL`.

### A larger connection pool, 12 cores

The database connection pool of the backend was set to 30 (`SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=30`) and measured again at 12 cores with the same 1 GB memory limits; PostgreSQL sees 32 connections.
Each cell gives the pool-30 run, with the throughput and p95 of the default pool of 10 in the last column.

| Concurrency | Requests/second | p50 / p95 / p99 (ms) | Errors | Pool 10: requests/second, p95 |
|---|---|---|---|---|
| 25 | 877 | 10 / 61 / 242 | 0% | 683, 100 ms |
| 50 | 967 | 33 / 106 / 157 | 0% | 828, 134 ms |
| **100** | 859 | 84 / 224 / 753 | 0% | 833, 244 ms |
| 200 | 792 | 209 / 561 / 1133 | 0% | 714, 627 ms |

- Pool 30 raises the throughput by about 3 to 17% and lowers the p95 at 25 and 50 concurrent users; the breaking point is still between 100 and 200.
- CPU at 100 concurrent users: PostgreSQL 3.0 cores, backend 2.4, APISIX 0.9, about 6.3 of 12 in total.
- Peak RAM: backend 582 MiB, Keycloak 514, PostgreSQL 376, APISIX 378, SeaweedFS 156.
- Sign-in at levels 1, 10 and 50: 31, 33 and 35 per second, p95 of 35, 335 and 1531 ms.
- The throughput still stays below about 1000 requests per second although only 6 to 7 of 12 cores are used; the rest of the cause has not been verified.
- A hypothesis: every write of a history row takes the same global advisory lock of PostgreSQL (`HistoryChain.lock()`) to put the hash chain in order, so writes follow one after another; writes are three of the five steps of the measuring loop.

## Sign-in

Signing in is many times heavier than one API request, so it is measured separately, with 1, 10 and 50 concurrent sign-ins.
Keycloak saturates at about 23 to 34 sign-ins per second (28 to 34 at 50 concurrent sign-ins), whatever the number of cores from 2 to 12.

| Cores | Per second at 1 / 10 / 50 | p95 at 1 / 10 / 50 (ms) |
|---|---|---|
| 12 | 23 / 28 / 28 | 50 / 375 / 1933 |
| 8 | 28 / 32 / 32 | 43 / 337 / 1608 |
| 4 | 28 / 32 / 34 | 49 / 326 / 1536 |
| 2 | 27 / 29 / 34 | 41 / 435 / 1617 |

So a large number of users signing in at once within a few seconds will be slow, while users who already have a token are not affected.

## Latency by data size

Latency depends on the number of records in the table.
At the same 12 cores and 10 concurrent users: with 10,000 records, the throughput is 800 requests per second and the p95 is 26 ms; with about 198,000 records, the throughput is 335 requests per second and the p95 is 43 ms, with PostgreSQL then using about 2.8 to 3 cores.
Every table of figures above was measured on a table of 10,000 records; a larger table needs a new measurement.

## A bug found and fixed while measuring

The first run showed that from 50 concurrent users up, the backend hung for 30 seconds and then returned error 500 (74% to 92% errors), while the CPU of the backend was close to 0.
The id generator of Flowable takes a block of ids from the database inside a synchronized block and needs one more connection; the 10 connections of the pool were all held by threads waiting for that synchronized block, so no thread could move.
Flowable now uses UUID ids generated in memory, so it no longer needs a connection to get ids.

## What has not been measured

- A 1-core configuration.
- Finer concurrency levels between 75 and 200.
- Memory limits smaller than the 3.06 GB configuration above; the default JVM heap is a quarter of the limit of the container, so a backend under about 640 MB has not been tried.
- Verifying the advisory-lock hypothesis of the history hash chain: measure again with history writes that are not queued, or with a measuring loop that has fewer write steps.
- A connection pool larger than 30.
- A data table larger than 10,000 records.
- Load through an API gateway that still has rate limits on: one IP address is limited to 600 requests per minute on `/api/*` and 120 per minute on `/token`.

## Run it again

Run it on a temporary copy of `deploy/`, never on a stack in use: `--reset` deletes the data of the stack.

1. Copy `deploy/` (without the `data/` folder) to a temporary folder and build the `.env` of the copy with `COMPOSE_PROJECT_NAME=dxscratch`, `APISIX_HOST_PORT=9180`, `POSTGRES_HOST_PORT=127.0.0.1:5532`, `KEYCLOAK_ADMIN_CONSOLE=true` and `RATE_LIMIT_ENABLED=false`.
2. Loosen the `limit-count` values in `apisix/common.yaml` of the copy.
3. Have the images ready with the same `DXOS_IMAGE_TAG`.
4. Bring the stack up with the core range of the simulated machine, in the copy of `deploy/`.
   If you change `LOAD_CPUSET` you must recreate the containers because the JVM reads the number of cores at startup:

```
LOAD_CPUSET=0-3 docker compose -p dxscratch -f docker-compose.yml -f ../e2e/api/load_override.yml up -d --force-recreate
```

5. Run the load generator from the root of the repository.
   From 8 cores down, pin the load generator outside the core range of the stack with `taskset -c N-11` (N is the number of cores):

```
DXOS_ENV_FILE=<.env of the copy> DXOS_BASE_URL=http://localhost:9180 DXOS_PROJECT=dxscratch \
  taskset -c 4-11 python3 e2e/api/load_test.py --levels 1,10,25,50,75,100,200 --reset \
  --warmup 30 --duration 60 --login-levels 1,10,50 --out result.json
```

`LOAD_CPUSET` is the core range of the simulated machine, for example `0-3` for four cores.
`DXOS_PROJECT` is required: `--reset` and the CPU and RAM figures all go through the `docker compose` of that project.
The CPU figures are read from `/sys/fs/cgroup`, so you need cgroup v2 and permission to read that folder.
