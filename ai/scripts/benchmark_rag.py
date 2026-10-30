# SPDX-License-Identifier: AGPL-3.0-only
"""Run a local k6 retrieval pilot, sample Docker resources, and publish saved summaries."""

from __future__ import annotations

import argparse
import json
import os
import platform
import re
import subprocess
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

from evaluate_rag import load_questions, outside_repository, read_json, write_json


def serve(args: argparse.Namespace) -> None:
    """Serve the existing application for retrieval only, without loading CV models."""
    import importlib.metadata

    import torch
    import uvicorn
    from evaluate_rag import code_identity, corpus_index, validate_snapshot

    from app.core.config import get_settings
    from app.db.session import SessionLocal
    from app.main import create_app
    from app.rag.embedding_service import EmbeddingService

    output = outside_repository(args.output)
    if output.exists():
        raise ValueError("Use a new server record path")
    settings, provenance = get_settings(), read_json(args.ingest_record)
    versions = {
        n: importlib.metadata.version(n)
        for n in (
            "torch",
            "sentence-transformers",
            "transformers",
            "SQLAlchemy",
            "pgvector",
            "rank-bm25",
        )
    }
    metadata = read_json(
        Path(__file__).resolve().parents[2] / "data/rag/evaluation/metadata.json"
    )
    if (
        provenance.get("embedding_model") != settings.embedding_model
        or provenance.get("runtime_versions") != versions
        or provenance.get("corpora") != metadata["corpora"]
        or not provenance.get("model_artifact")
    ):
        raise ValueError(
            "Ingest provenance does not identify the same model, runtime and corpora"
        )
    expected = corpus_index()
    with SessionLocal() as db:
        if (
            db.connection()
            .exec_driver_sql("SHOW default_transaction_read_only")
            .scalar()
            != "on"
        ):
            raise ValueError(
                "The isolated benchmark requires a default read-only database role"
            )
        validate_snapshot(db, expected)
    EmbeddingService().embed_text("Khoi dong model embedding")
    output.parent.mkdir(parents=True, exist_ok=True)
    write_json(
        output,
        {
            "boundary": "Existing AI app, HTTP chatbot search; CV lifespan disabled; no Backend/gateway/LLM",
            "strategy": settings.chat_retrieval_strategy,
            "code_identity": code_identity(),
            "ingestion": provenance,
            "runtime_versions": versions,
            "python": platform.python_version(),
            "uvicorn_workers": 1,
            "torch_threads": torch.get_num_threads(),
            "documents": len(expected),
            "chunks": sum(len(d["chunks"]) for d in expected.values()),
            "transaction_read_only": True,
        },
    )
    uvicorn.run(
        create_app(),
        host=args.host,
        port=args.port,
        lifespan="off",
        workers=1,
        access_log=False,
    )


def mib(value: str) -> float:
    match = re.fullmatch(r"([0-9.]+)\s*(B|kB|KB|MB|GB|KiB|MiB|GiB)", value.strip())
    if not match:
        raise ValueError("Unrecognized Docker memory unit")
    multipliers = {
        "B": 1,
        "kB": 1000,
        "KB": 1000,
        "MB": 1000**2,
        "GB": 1000**3,
        "KiB": 1024,
        "MiB": 1024**2,
        "GiB": 1024**3,
    }
    return float(match[1]) * multipliers[match[2]] / 1024**2


def parse_resource(line: str) -> dict | None:
    # Docker stats emits terminal cursor/erase sequences even through a pipe.
    clean = re.sub(r"\x1b\[[0-?]*[ -/]*[@-~]", "", line).strip()
    if not clean:
        return None
    row = json.loads(clean)
    return {
        "time": datetime.now(timezone.utc).isoformat(),
        "container": row["Name"],
        "cpu_percent": float(row["CPUPerc"].rstrip("%")),
        "ram_mib": mib(row["MemUsage"].split("/")[0]),
        "ram_limit_mib": mib(row["MemUsage"].split("/")[1]),
        "net_io": row["NetIO"],
        "block_io": row["BlockIO"],
    }


def sample_resources(
    containers: list[str], stop: threading.Event, samples: list, errors: list
) -> None:
    # Docker CLI supplies its own cache-adjusted memory/CPU calculations.
    process = subprocess.Popen(
        ["docker", "stats", "--format", "{{json .}}", *containers],
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
    )

    def terminate() -> None:
        stop.wait()
        process.terminate()

    watcher = threading.Thread(target=terminate, daemon=True)
    watcher.start()
    try:
        for line in process.stdout:
            sample = parse_resource(line)
            if sample is not None:
                samples.append(sample)
    except Exception as exc:
        errors.append(type(exc).__name__)
    finally:
        process.wait()
        if process.returncode and not stop.is_set():
            errors.append("Docker stats stopped before the trial ended")


def summarize(directory: Path, containers: list[str]) -> dict:
    summary = read_json(directory / "summary.json")["metrics"]
    samples = read_json(directory / "resources.json")["samples"]
    resources = {}
    for name in containers:
        rows = [r for r in samples if r["container"] == name]
        if not rows:
            raise ValueError("Missing container resource samples")
        resources[name] = {
            "samples": len(rows),
            "cpu_mean_percent": sum(r["cpu_percent"] for r in rows) / len(rows),
            "cpu_peak_percent": max(r["cpu_percent"] for r in rows),
            "ram_peak_mib": max(r["ram_mib"] for r in rows),
        }
    return {
        "requests": summary["http_reqs"]["values"]["count"],
        "requests_per_second": summary["http_reqs"]["values"]["rate"],
        "http_failure_rate": summary["http_req_failed"]["values"]["rate"],
        "invalid_response_rate": summary["rag_invalid_response"]["values"]["rate"],
        "latency_ms": summary["http_req_duration"]["values"],
        "resources": resources,
    }


def run(args: argparse.Namespace) -> None:
    output = outside_repository(args.output)
    if output.exists():
        raise ValueError(
            "Use a new output directory; benchmark evidence is never overwritten"
        )
    url = os.environ.get("RAG_LOAD_URL", "")
    parsed = urllib.parse.urlsplit(url)
    key = os.environ.get("AI_SERVICE_API_KEY")
    if (
        parsed.scheme != "http"
        or parsed.hostname not in {"localhost", "127.0.0.1"}
        or parsed.username
        or parsed.password
        or parsed.query
        or parsed.path != "/api/v1/knowledge/search"
        or not key
    ):
        raise ValueError(
            "Set a local RAG_LOAD_URL for the existing retrieval endpoint and AI_SERVICE_API_KEY"
        )
    if not 10 <= args.seconds <= 300 or not 1 <= args.repeats <= 3:
        raise ValueError("Use 10..300 seconds and 1..3 repeats")
    if len(set(args.container)) != len(args.container) or any(
        not re.fullmatch(r"[A-Za-z0-9_.-]+", n) for n in args.container
    ):
        raise ValueError("Pass unique named benchmark containers")
    version_text = subprocess.check_output([str(args.k6), "version"], text=True)
    version = re.search(r"k6 v[0-9.]+", version_text).group()
    server = read_json(args.server_record)
    if not server.get("transaction_read_only") or not server.get("ingestion"):
        raise ValueError(
            "Provide the verified isolated server record, with read-only database and ingestion provenance"
        )
    config = []
    for name in args.container:
        container = json.loads(
            subprocess.check_output(["docker", "inspect", name], text=True)
        )[0]
        if not container["State"]["Running"]:
            raise ValueError("All target containers must be running")
        config.append(
            {
                "name": name,
                "image": container["Config"]["Image"],
                "memory_bytes": container["HostConfig"]["Memory"],
                "nano_cpus": container["HostConfig"]["NanoCpus"],
            }
        )
    questions = load_questions()
    output.mkdir(parents=True)
    manifest = {
        "started_at": datetime.now(timezone.utc).isoformat(),
        "k6": version,
        "host": {
            "os": platform.platform(),
            "logical_cpus": os.cpu_count(),
            "memory": Path("/proc/meminfo").read_text(),
        },
        "containers": config,
        "seconds": args.seconds,
        "http_timeout_seconds": 20,
        "repeats": args.repeats,
        "boundary": "AI HTTP retrieval only; CV lifespan excluded; no Backend, gateway or LLM",
        "server": server,
        "workload": "closed model, no think time; each VU waits for its response",
        "question_groups": ["farm_qa", "lab_qa"],
        "trials": [],
    }
    write_json(output / "benchmark.json", manifest)
    for namespace in ("farm", "lab"):
        # Warm all questions outside k6, so loading the embedding model is not counted.
        for q in questions:
            if q["group"] != f"{namespace}_qa":
                continue
            request = urllib.request.Request(
                url,
                data=json.dumps(
                    {
                        "query": q["query"],
                        "namespace": namespace,
                        "top_k": 4,
                        "document_type": None,
                    }
                ).encode(),
                headers={"Content-Type": "application/json", "X-Service-Api-Key": key},
            )
            try:
                with urllib.request.urlopen(request, timeout=90) as response:
                    result = json.load(response)
                    if (
                        result.get("namespace") != namespace
                        or result.get("reason_code") == "RERANK_UNAVAILABLE"
                        or len(result.get("passages", [])) > 4
                    ):
                        raise ValueError("Warmup returned an invalid search envelope")
            except urllib.error.URLError:
                raise RuntimeError(
                    "Local retrieval warmup failed; check the isolated AI service"
                ) from None
        for repeat in range(1, args.repeats + 1):
            for vus in (1, 2, 5):
                trial = output / f"{namespace}-vu{vus}-repeat{repeat}"
                trial.mkdir()
                env = {
                    **os.environ,
                    "RAG_LOAD_NAMESPACE": namespace,
                    "RAG_LOAD_VUS": str(vus),
                    "RAG_LOAD_SECONDS": str(args.seconds),
                    "RAG_LOAD_OUTPUT": str(trial),
                    "K6_NO_USAGE_REPORT": "true",
                    "K6_WEB_DASHBOARD": "true",
                    "K6_WEB_DASHBOARD_HOST": "127.0.0.1",
                    "K6_WEB_DASHBOARD_PORT": "-1",
                    "K6_WEB_DASHBOARD_PERIOD": "2s",
                    "K6_WEB_DASHBOARD_EXPORT": str(trial / "dashboard.html"),
                }
                samples, errors, stop = [], [], threading.Event()
                thread = threading.Thread(
                    target=sample_resources,
                    args=(args.container, stop, samples, errors),
                    daemon=True,
                )
                thread.start()
                started = datetime.now(timezone.utc).isoformat()
                try:
                    with (trial / "k6.log").open("w") as log:
                        result = subprocess.run(
                            [
                                str(args.k6),
                                "run",
                                "--out",
                                f"json={trial / 'metrics.json.gz'}",
                                str(Path(__file__).with_name("load_rag.js")),
                            ],
                            env=env,
                            stdout=log,
                            stderr=subprocess.STDOUT,
                            timeout=args.seconds + 60,
                        )
                finally:
                    stop.set()
                    thread.join(timeout=10)
                    write_json(
                        trial / "resources.json", {"samples": samples, "errors": errors}
                    )
                if thread.is_alive() or errors:
                    raise RuntimeError("Resource collection failed; evidence retained")
                row = {
                    "namespace": namespace,
                    "vus": vus,
                    "repeat": repeat,
                    "directory": trial.name,
                    "started_at": started,
                    "ended_at": datetime.now(timezone.utc).isoformat(),
                    "k6_exit_code": result.returncode,
                    **summarize(trial, args.container),
                }
                manifest["trials"].append(row)
                write_json(output / "benchmark.json", manifest)
                print(
                    f"{trial.name}: {row['requests']} requests, {row['requests_per_second']:.2f} req/s, p95 {row['latency_ms']['p(95)']:.2f} ms, invalid {row['invalid_response_rate']:.3f}",
                    flush=True,
                )
                if result.returncode not in {0, 99}:
                    raise RuntimeError("k6 execution failed; evidence retained")
                time.sleep(3)
    manifest["completed_at"] = datetime.now(timezone.utc).isoformat()
    write_json(output / "benchmark.json", manifest)


def publish(path: Path) -> None:
    from langfuse import Evaluation, Langfuse

    path = outside_repository(path)
    data = read_json(path / "benchmark.json")
    if not data.get("completed_at") or len(data["trials"]) != data["repeats"] * 6:
        raise ValueError("Only publish a complete benchmark")
    expected = {
        (ns, vus, repeat)
        for ns in ("farm", "lab")
        for vus in (1, 2, 5)
        for repeat in range(1, data["repeats"] + 1)
    }
    if {(t["namespace"], t["vus"], t["repeat"]) for t in data["trials"]} != expected:
        raise ValueError(
            "Benchmark trials are missing, duplicated or outside the workload"
        )
    for trial in data["trials"]:
        name = f"{trial['namespace']}-vu{trial['vus']}-repeat{trial['repeat']}"
        if trial["directory"] != name or trial["k6_exit_code"] not in {0, 99}:
            raise ValueError("Invalid benchmark trial record")
        if any(
            trial[key] != value
            for key, value in summarize(
                path / name, [c["name"] for c in data["containers"]]
            ).items()
        ):
            raise ValueError(
                "Benchmark summary differs from the saved k6 or resource evidence"
            )
    receipt = path / "publish.json"
    if receipt.exists():
        print("Benchmark already published; no new replay created")
        return
    client = Langfuse()
    try:
        if not client.auth_check():
            raise RuntimeError("Langfuse authentication failed")

        def replay(*, item, **_):
            return next(
                t for t in data["trials"] if t["directory"] == item["input"]["trial"]
            )

        def evaluate(*, output, **_):
            values = {
                "latency_p95_ms": output["latency_ms"]["p(95)"],
                "requests_per_second": output["requests_per_second"],
                "http_failure_rate": output["http_failure_rate"],
                "invalid_response_rate": output["invalid_response_rate"],
            }
            for index, container in enumerate(data["containers"]):
                stats = output["resources"][container["name"]]
                values[f"container_{index + 1}_cpu_peak_percent"] = stats[
                    "cpu_peak_percent"
                ]
                values[f"container_{index + 1}_ram_peak_mib"] = stats["ram_peak_mib"]
            return [
                Evaluation(
                    name=k,
                    value=v,
                    comment="Saved k6/Docker pilot; sampled resources; not live tracing or quality scores",
                )
                for k, v in values.items()
            ]

        result = client.run_experiment(
            name="RAG HTTP load pilot",
            run_name=path.name,
            experiment_id=Langfuse.create_trace_id(seed=f"rag-load:{path.name}"),
            data=[
                {
                    "input": {
                        "trial": t["directory"],
                        "namespace": t["namespace"],
                        "vus": t["vus"],
                        "repeat": t["repeat"],
                    }
                }
                for t in data["trials"]
            ],
            task=replay,
            evaluators=[evaluate],
            max_concurrency=1,
            metadata={
                "recording": "Replay of saved k6 trial summaries; trace latency is publishing time",
                "boundary": data["boundary"],
                "containers": data["containers"],
                "seconds": data["seconds"],
                "k6": data["k6"],
                "quality_evaluation": False,
            },
        )
        if len(result.item_results) != len(data["trials"]) or any(
            len(r.evaluations) != 4 + 2 * len(data["containers"])
            for r in result.item_results
        ):
            raise RuntimeError("Not every benchmark trial was published")
        client.flush()
        write_json(receipt, {"url": result.experiment_url})
        print("Published benchmark summary to local Langfuse")
    finally:
        client.shutdown()


def self_check() -> None:
    import tempfile

    assert mib("1GiB") == 1024 and mib("512MiB") == 512
    assert abs(mib("1GB") - 953.67431640625) < 1e-9
    sample = parse_resource(
        "\x1b[H"
        + json.dumps(
            {
                "Name": "ai",
                "CPUPerc": "150.25%",
                "MemUsage": "512MiB / 1.5GiB",
                "NetIO": "1kB / 2kB",
                "BlockIO": "0B / 0B",
            }
        )
        + "\x1b[K\n"
    )
    assert (
        sample["cpu_percent"] == 150.25
        and sample["ram_mib"] == 512
        and sample["ram_limit_mib"] == 1536
    )
    assert parse_resource("\x1b[K\n") is None
    with tempfile.TemporaryDirectory() as tmp:
        directory = Path(tmp)
        write_json(
            directory / "summary.json",
            {
                "metrics": {
                    name: {"values": value}
                    for name, value in {
                        "http_reqs": {"count": 10, "rate": 2},
                        "http_req_failed": {"rate": 0.2},
                        "rag_invalid_response": {"rate": 0.3},
                        "http_req_duration": {"p(95)": 900},
                    }.items()
                }
            },
        )
        write_json(
            directory / "resources.json",
            {
                "samples": [
                    {"container": "ai", "cpu_percent": 50, "ram_mib": 100},
                    {"container": "ai", "cpu_percent": 150, "ram_mib": 200},
                ]
            },
        )
        result = summarize(directory, ["ai"])
        assert (
            result["invalid_response_rate"] == 0.3
            and result["http_failure_rate"] == 0.2
        )
        assert result["resources"]["ai"] == {
            "samples": 2,
            "cpu_mean_percent": 100,
            "cpu_peak_percent": 150,
            "ram_peak_mib": 200,
        }
        try:
            summarize(directory, ["missing"])
        except ValueError:
            pass
        else:
            raise AssertionError("Missing resource evidence accepted")
    print(
        "Benchmark self-check: PASS (units, failures retained, sampled aggregates, missing evidence)"
    )


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("serve", "run", "publish", "self-check"))
    parser.add_argument("--output", type=Path)
    parser.add_argument("--k6", type=Path)
    parser.add_argument("--container", action="append")
    parser.add_argument("--server-record", type=Path)
    parser.add_argument("--ingest-record", type=Path)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int)
    parser.add_argument("--seconds", type=int, default=30)
    parser.add_argument("--repeats", type=int, default=3)
    args = parser.parse_args()
    if args.command == "self-check":
        self_check()
    elif args.command == "serve":
        if (
            not args.output
            or not args.ingest_record
            or not args.port
            or not 1 <= args.port <= 65535
        ):
            parser.error("serve requires --output, --ingest-record and a valid --port")
        serve(args)
    elif args.command == "publish":
        if not args.output:
            parser.error("publish requires --output")
        publish(args.output)
    else:
        if (
            not args.output
            or not args.k6
            or not args.container
            or not args.server_record
        ):
            parser.error(
                "run requires --output, --k6, --server-record and at least one --container"
            )
        run(args)
