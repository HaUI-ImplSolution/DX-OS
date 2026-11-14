# SPDX-License-Identifier: Apache-2.0
"""Load test of the common API: throughput, latency and errors at rising concurrency.

Uses only the Python standard library. Run it against a throwaway stack, never the one in use:
    DXOS_ENV_FILE=<.env of the stack> DXOS_BASE_URL=http://localhost:9180 DXOS_PROJECT=dxscratch \
      python3 e2e/api/load_test.py --levels 1,10,50,100 --warmup 30 --duration 60

Each virtual user loops: create a record, list the open ones with a filter, take the first action
of the process, read the history, list its own records. Every level runs warm-up first, then the
measured window. A level passes when the p95 of all requests is below 500 ms and fewer than 1% of
the requests fail. Logins are measured apart (--login-levels), since a login is far heavier than
an API call and tokens are reused during the run.

The gateway and the service limit the requests of one address and of one user, which a load test
from one machine would hit at once. Raise or switch off those limits on the throwaway stack
(RATE_LIMIT_ENABLED=false and the limit-count values in deploy/apisix/common.yaml) before a run,
and read the numbers as a lower bound: the generator shares the machine with the stack.
"""
import argparse
import http.client
import json
import math
import multiprocessing
import os
import resource
import subprocess
import sys
import threading
import time
import urllib.parse

import kc

MODEL_NAME = "load1"
MODEL = f"""format: 0
name: {MODEL_NAME}
roles:
  - name: ASKER
  - name: AGENT
    staff: true
entities:
  ticket:
    fields:
      title: {{type: text, required: true}}
    access: {{ASKER: own, AGENT: all}}
    process: {{family: work_item, handler: AGENT}}
"""
ASKERS = 10
AGENTS = 5
P95_LIMIT_MS = 500
ERROR_LIMIT = 0.01
LIST_OPEN = f"/api/v1/data/{MODEL_NAME}/ticket?filter.status=submitted&sort=created_at&order=desc&size=25"
LIST_OWN = f"/api/v1/data/{MODEL_NAME}/ticket?sort=created_at&order=desc&size=25"


def percentile(sorted_ms, fraction):
    if not sorted_ms:
        return 0.0
    return sorted_ms[min(len(sorted_ms) - 1, max(0, math.ceil(len(sorted_ms) * fraction) - 1))]


class Client:
    """One keep-alive connection; reconnects after an error."""

    def __init__(self, base):
        parts = urllib.parse.urlsplit(base)
        self.host, self.port = parts.hostname, parts.port or (443 if parts.scheme == "https" else 80)
        self.conn = None

    def call(self, method, path, token, body=None):
        headers = {"Authorization": f"Bearer {token}"}
        data = None
        if body is not None:
            data = json.dumps(body).encode()
            headers["Content-Type"] = "application/json"
        try:
            if self.conn is None:
                self.conn = http.client.HTTPConnection(self.host, self.port, timeout=30)
            self.conn.request(method, path, body=data, headers=headers)
            response = self.conn.getresponse()
            payload = response.read()
            return response.status, payload
        except (OSError, http.client.HTTPException):
            self.close()
            return 0, b""

    def close(self):
        if self.conn is not None:
            try:
                self.conn.close()
            except OSError:
                pass
            self.conn = None


def virtual_user(base, askers, agents, index, started, warm_until, stop_at, samples):
    client = Client(base)
    asker = askers[index % len(askers)]
    agent = agents[index % len(agents)]

    def timed(name, method, path, token, body=None):
        began = time.perf_counter()
        status, payload = client.call(method, path, token, body)
        ms = (time.perf_counter() - began) * 1000
        if began - started >= warm_until:
            samples.append((name, ms, status))
        return status, payload

    while time.perf_counter() - started < stop_at:
        status, payload = timed("create", "POST", f"/api/v1/data/{MODEL_NAME}/ticket", asker,
                                {"title": f"load {index} {time.time_ns()}"})
        record = None
        if status == 200:
            try:
                record = json.loads(payload)["data"]["id"]
            except (ValueError, KeyError, TypeError):
                record = None
        timed("list open", "GET", LIST_OPEN, agent)
        if record is not None:
            timed("action", "POST", f"/api/v1/data/{MODEL_NAME}/ticket/{record}/actions/start", agent, {})
            timed("history", "GET", f"/api/v1/data/{MODEL_NAME}/ticket/{record}/events", agent)
        timed("list own", "GET", LIST_OWN, asker)
    client.close()


def process_main(base, askers, agents, first, count, started, warm_until, stop_at, pipe):
    samples = []
    threads = [threading.Thread(target=virtual_user,
                                args=(base, askers, agents, first + i, started, warm_until, stop_at, samples))
               for i in range(count)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()
    pipe.send(samples)
    pipe.close()


def cgroup_files(project):
    """The cgroup directories of the containers of a stack, by service name."""
    result = subprocess.run(["docker", "ps", "--no-trunc", "--format", "{{.ID}}|{{.Names}}"],
                            capture_output=True, text=True)
    found = {}
    for line in result.stdout.splitlines():
        identifier, name = line.split("|")
        if name.startswith(project + "-"):
            for base in (f"/sys/fs/cgroup/system.slice/docker-{identifier}.scope", f"/sys/fs/cgroup/docker/{identifier}"):
                if os.path.exists(base + "/cpu.stat"):
                    found[name[len(project) + 1:]] = base
                    break
    return found


def cpu_seconds(path):
    with open(path + "/cpu.stat") as stat:
        for line in stat:
            if line.startswith("usage_usec"):
                return int(line.split()[1]) / 1e6
    return 0.0


def memory_mib(path):
    """Memory in use without the file cache the kernel can drop, as docker stats counts it."""
    with open(path + "/memory.current") as current:
        used = int(current.read())
    with open(path + "/memory.stat") as stat:
        for line in stat:
            if line.startswith("inactive_file "):
                used -= int(line.split()[1])
    return used / 1048576


def container_stats(project, warmup, until, seconds, started):
    """CPU seconds of every container over the measured window and its peak memory, read from the
    cgroups of the containers (docker stats is too coarse for a window of a minute)."""
    paths = cgroup_files(project)
    seen = {name: {"cpu_start": None, "cpu_end": None, "mem": []} for name in paths}

    def loop():
        began = False
        while time.perf_counter() - started < until:
            now = time.perf_counter() - started
            if now >= warmup and not began:
                began = True
                for name, path in paths.items():
                    seen[name]["cpu_start"] = (cpu_seconds(path), now)
            if began:
                for name, path in paths.items():
                    seen[name]["mem"].append(memory_mib(path))
            time.sleep(min(seconds, max(0.1, until - (time.perf_counter() - started))))
        end = time.perf_counter() - started
        for name, path in paths.items():
            seen[name]["cpu_end"] = (cpu_seconds(path), end)

    thread = threading.Thread(target=loop)
    thread.start()
    return thread, seen


def run_level(base, askers, agents, concurrency, warmup, duration, project):
    available = len(os.sched_getaffinity(0))
    processes = min(available, max(1, math.ceil(concurrency / 25)))
    shares = [concurrency // processes + (1 if i < concurrency % processes else 0) for i in range(processes)]
    before = resource.getrusage(resource.RUSAGE_CHILDREN)
    started = time.perf_counter()
    stop_at = warmup + duration
    stats_thread, stats = container_stats(project, warmup, stop_at, 5, started) if project else (None, {})
    pipes, workers, first = [], [], 0
    for share in shares:
        parent, child = multiprocessing.Pipe(duplex=False)
        worker = multiprocessing.Process(target=process_main,
                                         args=(base, askers, agents, first, share, started, warmup, stop_at, child))
        worker.start()
        pipes.append(parent)
        workers.append(worker)
        first += share
    samples = []
    for parent in pipes:
        samples.extend(parent.recv())
    for worker in workers:
        worker.join()
    wall = time.perf_counter() - started
    if stats_thread:
        stats_thread.join()
    after = resource.getrusage(resource.RUSAGE_CHILDREN)
    generator_cpu = (after.ru_utime + after.ru_stime - before.ru_utime - before.ru_stime) / wall

    times = sorted(ms for _, ms, status in samples)
    failed = [s for s in samples if not 200 <= s[2] < 300]
    by_status = {}
    for _, _, status in failed:
        by_status[str(status)] = by_status.get(str(status), 0) + 1
    names = sorted({name for name, _, _ in samples})
    endpoints = {}
    for name in names:
        values = sorted(ms for n, ms, _ in samples if n == name)
        endpoints[name] = {"count": len(values), "p50": round(percentile(values, 0.5)),
                           "p95": round(percentile(values, 0.95)), "p99": round(percentile(values, 0.99))}
    total = len(samples)
    error_rate = len(failed) / total if total else 1.0
    p95 = percentile(times, 0.95)
    return {
        "concurrency": concurrency,
        "requests": total,
        "throughput_rps": round(total / duration, 1),
        "p50_ms": round(percentile(times, 0.5)),
        "p95_ms": round(p95),
        "p99_ms": round(percentile(times, 0.99)),
        "error_rate_pct": round(error_rate * 100, 2),
        "errors_by_status": by_status,
        "pass": bool(total) and p95 < P95_LIMIT_MS and error_rate < ERROR_LIMIT,
        "generator_cores_used": round(generator_cpu, 2),
        "endpoints": endpoints,
        "containers": {name: {"cpu_cores_mean": round((v["cpu_end"][0] - v["cpu_start"][0]) / (v["cpu_end"][1] - v["cpu_start"][1]), 2)
                                 if v["cpu_start"] and v["cpu_end"] else 0,
                              "mem_max_mib": round(max(v["mem"], default=0))}
                       for name, v in sorted(stats.items())},
    }


def login_level(concurrency, rounds):
    """Concurrent password logins; each thread logs in several times."""
    url = f"{kc.BASE}/realms/dxos/protocol/openid-connect/token"
    body = urllib.parse.urlencode({"grant_type": "password", "client_id": kc.CLIENT,
                                   "username": "load_asker1", "password": "Test1234!"})
    parts = urllib.parse.urlsplit(url)
    samples = []

    def one():
        conn = http.client.HTTPConnection(parts.hostname, parts.port, timeout=60)
        for _ in range(rounds):
            began = time.perf_counter()
            try:
                conn.request("POST", parts.path, body=body,
                             headers={"Content-Type": "application/x-www-form-urlencoded"})
                response = conn.getresponse()
                response.read()
                status = response.status
            except (OSError, http.client.HTTPException):
                status = 0
                conn.close()
                conn = http.client.HTTPConnection(parts.hostname, parts.port, timeout=60)
            samples.append(((time.perf_counter() - began) * 1000, status))
        conn.close()

    began = time.perf_counter()
    threads = [threading.Thread(target=one) for _ in range(concurrency)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()
    wall = time.perf_counter() - began
    times = sorted(ms for ms, _ in samples)
    failed = sum(1 for _, status in samples if status != 200)
    return {"concurrency": concurrency, "logins": len(samples), "per_second": round(len(samples) / wall, 1),
            "p50_ms": round(percentile(times, 0.5)), "p95_ms": round(percentile(times, 0.95)),
            "p99_ms": round(percentile(times, 0.99)),
            "error_rate_pct": round(100 * failed / len(samples), 2) if samples else 100.0}


def psql(sql):
    project = os.environ.get("DXOS_PROJECT")
    command = ["docker", "compose", "--env-file", kc.ENV_FILE] + (["-p", project] if project else [])
    command += ["exec", "-T", "postgres", "psql", "-U", "dxos", "-d", "dxos", "-tA", "-c", sql]
    result = subprocess.run(command, capture_output=True, text=True, cwd=os.path.join(kc.REPO, "deploy"))
    return result.returncode, (result.stdout + result.stderr).strip()


def prepare(seed):
    kc.ensure_client()
    kc.ensure_user("admin1", ["ADMIN"])
    admin = kc.token("admin1")
    status, body = kc.api(admin, "POST", "/models/apply", MODEL, "application/yaml")
    assert status == 200, (status, body)
    for i in range(1, ASKERS + 1):
        kc.ensure_user(f"load_asker{i}", [f"{MODEL_NAME}/ASKER"])
    for i in range(1, AGENTS + 1):
        kc.ensure_user(f"load_agent{i}", [f"{MODEL_NAME}/AGENT"])
    if seed:
        code, out = psql(f"select count(*) from m_{MODEL_NAME}.ticket")
        have = int(out) if code == 0 and out.isdigit() else 0
        if have < seed:
            code, out = psql(f"insert into m_{MODEL_NAME}.ticket (created_by, title, status) "
                             f"select 'seed-' || (g % 50), 'seed ' || g, 'submitted' "
                             f"from generate_series(1, {seed - have}) g")
            assert code == 0, out


def reset_data(seed):
    """Back to the same data before every level: the records of the model, the workflow runtime and
    history tables, the history lines and the outbox are emptied and the seed rows made again, so
    a level never runs on what an earlier one left behind."""
    code, out = psql(
        "DO $$ DECLARE t text; BEGIN "
        "EXECUTE 'TRUNCATE m_" + MODEL_NAME + ".ticket RESTART IDENTITY CASCADE'; "
        "FOR t IN SELECT format('%I.%I', table_schema, table_name) FROM information_schema.tables "
        "WHERE table_schema = 'engine' AND table_type = 'BASE TABLE' AND (table_name LIKE 'act\\_ru\\_%' "
        "OR table_name LIKE 'act\\_hi\\_%' OR table_name IN ('record_event', 'outbox', 'idempotency')) "
        "LOOP EXECUTE 'TRUNCATE ' || t || ' CASCADE'; END LOOP; END $$")
    assert code == 0, out
    code, out = psql(f"insert into m_{MODEL_NAME}.ticket (created_by, title, status) "
                     f"select 'seed-' || (g % 50), 'seed ' || g, 'submitted' from generate_series(1, {seed}) g")
    assert code == 0, out
    code, out = psql("analyze")
    assert code == 0, out


def tokens(prefix, count):
    return [kc.token(f"{prefix}{i}") for i in range(1, count + 1)]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--levels", default="1,10,50,100", help="concurrent virtual users, comma separated")
    parser.add_argument("--warmup", type=int, default=30, help="seconds before the measured window")
    parser.add_argument("--duration", type=int, default=60, help="seconds of the measured window")
    parser.add_argument("--seed", type=int, default=10000, help="records to have before the run")
    parser.add_argument("--reset", action="store_true", help="restore the seed data before every level")
    parser.add_argument("--login-levels", default="", help="concurrent logins to measure, comma separated")
    parser.add_argument("--login-rounds", type=int, default=5, help="logins per thread at each login level")
    parser.add_argument("--out", help="file to write the results to as JSON")
    args = parser.parse_args()
    project = os.environ.get("DXOS_PROJECT", "")
    levels = [int(x) for x in args.levels.split(",") if x]
    prepare(args.seed)
    results = {"base": kc.BASE, "cores_for_generator": len(os.sched_getaffinity(0)),
               "warmup_s": args.warmup, "duration_s": args.duration, "levels": [], "logins": []}
    for level in [int(x) for x in args.login_levels.split(",") if x]:
        row = login_level(level, args.login_rounds)
        results["logins"].append(row)
        print("login", json.dumps(row), flush=True)
    if levels:
        askers, agents = tokens("load_asker", ASKERS), tokens("load_agent", AGENTS)
        for level in levels:
            if args.reset:
                reset_data(args.seed)
            row = run_level(kc.BASE, askers, agents, level, args.warmup, args.duration, project)
            results["levels"].append(row)
            print(json.dumps({k: v for k, v in row.items() if k not in ("endpoints", "containers")}), flush=True)
            # A token lasts about five minutes; take fresh ones for the next level.
            askers, agents = tokens("load_asker", ASKERS), tokens("load_agent", AGENTS)
    if args.out:
        with open(args.out, "w") as out:
            json.dump(results, out, indent=1)
    return 0


if __name__ == "__main__":
    sys.exit(main())
