#!/usr/bin/env python3
"""Run sanitized upload/analysis load tests against the public API."""

from __future__ import annotations

import argparse
import concurrent.futures
import datetime as dt
import json
import os
import secrets
import statistics
import subprocess
import threading
import time
import urllib.error
import urllib.request
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

from api_latency_test import summarize


TERMINAL = {"DONE", "FAILED", "CANCELED", "START_FAILED"}
CONTAINERS = {
    "docker-backend-1": "backend",
    "docker-analysis-engine-1": "analysis-engine",
    "docker-video-llm-engine-1": "video-llm-engine",
    "docker-mysql-1": "mysql",
}


def now_utc() -> str:
    return dt.datetime.now(dt.timezone.utc).isoformat()


def parse_time(value: str | None) -> dt.datetime | None:
    if not value:
        return None
    return dt.datetime.fromisoformat(value.replace("Z", "+00:00"))


def seconds_between(start: str | None, end: str | None) -> float | None:
    left, right = parse_time(start), parse_time(end)
    if not left or not right:
        return None
    return round(max(0.0, (right - left).total_seconds()), 3)


class ApiClient:
    def __init__(self, base_url: str, timeout: float = 30.0) -> None:
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self.token: str | None = None

    def json(self, method: str, path: str, payload: dict[str, Any] | None = None) -> tuple[int, Any, float]:
        body = json.dumps(payload).encode("utf-8") if payload is not None else None
        headers = {"Accept": "application/json"}
        if body is not None:
            headers["Content-Type"] = "application/json"
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        request = urllib.request.Request(self.base_url + path, data=body, headers=headers, method=method)
        started = time.perf_counter()
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                raw = response.read()
                return response.status, json.loads(raw) if raw else None, (time.perf_counter() - started) * 1000
        except urllib.error.HTTPError as exc:
            raw = exc.read()
            parsed = json.loads(raw) if raw else None
            return exc.code, parsed, (time.perf_counter() - started) * 1000

    def upload(self, path: Path) -> tuple[int, Any, float, float]:
        boundary = "----perf-" + secrets.token_hex(12)
        payload = path.read_bytes()
        body = (
            f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"sample.mp4\"\r\n"
            "Content-Type: video/mp4\r\n\r\n"
        ).encode("ascii") + payload + f"\r\n--{boundary}--\r\n".encode("ascii")
        headers = {
            "Accept": "application/json",
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "Authorization": f"Bearer {self.token}",
        }
        request = urllib.request.Request(self.base_url + "/api/videos", data=body, headers=headers, method="POST")
        started = time.perf_counter()
        try:
            with urllib.request.urlopen(request, timeout=max(self.timeout, 120)) as response:
                raw = response.read()
                elapsed = time.perf_counter() - started
                throughput = path.stat().st_size / 1024 / 1024 / elapsed if elapsed > 0 else 0.0
                return response.status, json.loads(raw), elapsed * 1000, throughput
        except urllib.error.HTTPError as exc:
            raw = exc.read()
            elapsed = time.perf_counter() - started
            return exc.code, json.loads(raw) if raw else None, elapsed * 1000, 0.0


class ResourceSampler:
    def __init__(self, interval: float) -> None:
        self.interval = interval
        self.samples: dict[str, list[dict[str, float]]] = defaultdict(list)
        self.stop_event = threading.Event()
        self.thread = threading.Thread(target=self._run, name="docker-stats-sampler", daemon=True)

    def start(self) -> None:
        self.thread.start()

    def stop(self) -> None:
        self.stop_event.set()
        self.thread.join(timeout=self.interval + 5)

    def _run(self) -> None:
        while not self.stop_event.is_set():
            try:
                output = subprocess.run(
                    ["docker", "stats", "--no-stream", "--format", "{{json .}}"],
                    check=True,
                    capture_output=True,
                    text=True,
                    timeout=20,
                ).stdout
                for line in output.splitlines():
                    row = json.loads(line)
                    name = row.get("Name")
                    if name not in CONTAINERS:
                        continue
                    self.samples[CONTAINERS[name]].append(
                        {
                            "cpuPercent": float(str(row.get("CPUPerc", "0")).rstrip("%") or 0),
                            "memoryMiB": parse_memory_mib(str(row.get("MemUsage", "0B")).split("/")[0].strip()),
                        }
                    )
            except (OSError, subprocess.SubprocessError, ValueError, json.JSONDecodeError):
                pass
            self.stop_event.wait(self.interval)

    def summary(self) -> dict[str, Any]:
        report: dict[str, Any] = {}
        for service, values in self.samples.items():
            cpus = [item["cpuPercent"] for item in values]
            memory = [item["memoryMiB"] for item in values]
            report[service] = {
                "sampleCount": len(values),
                "averageCpuPercent": round(statistics.fmean(cpus), 2),
                "maxCpuPercent": round(max(cpus), 2),
                "averageMemoryMiB": round(statistics.fmean(memory), 2),
                "maxMemoryMiB": round(max(memory), 2),
            }
        return report


def parse_memory_mib(value: str) -> float:
    units = {"GiB": 1024, "MiB": 1, "KiB": 1 / 1024, "B": 1 / 1024 / 1024}
    for unit, multiplier in units.items():
        if value.endswith(unit):
            return float(value[: -len(unit)].strip()) * multiplier
    return 0.0


def fallback_flags(result: dict[str, Any] | None) -> dict[str, bool]:
    data = (result or {}).get("data") or {}
    metrics = data.get("metrics") or {}
    video_metadata = data.get("videoLlmMetadata") or {}
    text_metadata = ((data.get("summary") or {}).get("textLlm") or {})
    return {
        "vision": bool(metrics.get("visionAnalysisFallback")),
        "gesture": bool(metrics.get("gestureAnalysisFallback")),
        "videoLlm": bool(video_metadata.get("fallbackUsed")),
        "textLlm": bool(text_metadata.get("fallbackUsed", text_metadata.get("fallback"))),
    }


def analysis_performance(result: dict[str, Any] | None) -> dict[str, Any]:
    data = (result or {}).get("data") or {}
    return ((data.get("metrics") or {}).get("analysisPerformance") or {})


def provider_metadata(result: dict[str, Any] | None) -> dict[str, Any]:
    data = (result or {}).get("data") or {}
    video = data.get("videoLlmMetadata") or {}
    text = ((data.get("summary") or {}).get("textLlm") or {})
    return {
        "videoLlm": {
            key: video.get(key)
            for key in (
                "requestedProvider", "actualProvider", "requestedModel", "actualModel",
                "fallbackUsed", "fallbackReason",
            )
            if video.get(key) is not None
        },
        "textLlm": {
            key: text.get(key)
            for key in ("provider", "model", "fallback", "fallbackReason")
            if text.get(key) is not None
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost")
    parser.add_argument("--sample", type=Path, action="append", required=True)
    parser.add_argument("--total-jobs", type=int, default=20)
    parser.add_argument("--batch-size", type=int, default=5)
    parser.add_argument("--poll-seconds", type=float, default=2.0)
    parser.add_argument("--stats-seconds", type=float, default=3.0)
    parser.add_argument("--job-timeout-seconds", type=float, default=1800.0)
    parser.add_argument("--cleanup", action="store_true")
    parser.add_argument("--exercise-retry", action="store_true")
    parser.add_argument("--latency-iterations", type=int, default=50)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    samples = [path.resolve() for path in args.sample]
    missing = [str(path) for path in samples if not path.is_file()]
    if missing:
        raise SystemExit(f"Missing sample count: {len(missing)}")

    configured_email = os.getenv("PERF_EMAIL")
    configured_password = os.getenv("PERF_PASSWORD")
    if bool(configured_email) != bool(configured_password):
        raise SystemExit("PERF_EMAIL and PERF_PASSWORD must be set together.")
    suffix = secrets.token_hex(6)
    email = configured_email or f"perf-{suffix}@example.invalid"
    password = configured_password or "Perf-" + secrets.token_urlsafe(18)
    client = ApiClient(args.base_url)
    signup = {"email": email, "password": password, "passwordConfirm": password, "nickname": "performance-test"}
    status, _, _ = client.json("POST", "/api/auth/signup", signup)
    if status != 200:
        raise SystemExit(f"Performance account signup failed with HTTP {status}.")
    status, login, _ = client.json("POST", "/api/auth/login", {"email": email, "password": password})
    if status != 200:
        raise SystemExit(f"Performance account login failed with HTTP {status}.")
    client.token = login["data"]["accessToken"]

    sampler = ResourceSampler(args.stats_seconds)
    sampler.start()
    jobs: list[dict[str, Any]] = []
    upload_samples: list[float] = []
    upload_throughputs: list[float] = []
    start_samples: list[float] = []
    retry_samples: list[float] = []
    retry_exercised = False
    try:
        for batch_start in range(0, args.total_jobs, args.batch_size):
            batch_count = min(args.batch_size, args.total_jobs - batch_start)
            pending: list[dict[str, Any]] = []
            for offset in range(batch_count):
                sample = samples[(batch_start + offset) % len(samples)]
                upload_status, upload, upload_ms, throughput = client.upload(sample)
                upload_samples.append(upload_ms)
                upload_throughputs.append(throughput)
                if upload_status != 200:
                    jobs.append({"finalStatus": "UPLOAD_FAILED", "failureReason": safe_error(upload)})
                    continue
                pending.append(
                    {
                        "sampleIndex": (batch_start + offset) % len(samples),
                        "videoId": upload["data"]["videoId"],
                        "videoDurationSeconds": upload["data"].get("durationSeconds"),
                    }
                )

            def start_job(item: dict[str, Any]) -> dict[str, Any]:
                request_at = now_utc()
                response_status, response, elapsed_ms = client.json(
                    "POST", "/api/analyses", {"videoId": item["videoId"]}
                )
                return {
                    **item,
                    "requestAt": request_at,
                    "startHttpStatus": response_status,
                    "startApiMs": round(elapsed_ms, 2),
                    "jobId": ((response or {}).get("data") or {}).get("jobId"),
                    "finalStatus": ((response or {}).get("data") or {}).get("status")
                    if response_status == 200 else "START_FAILED",
                    "failureReason": None if response_status == 200 else safe_error(response),
                }

            with concurrent.futures.ThreadPoolExecutor(max_workers=batch_count) as executor:
                active = list(executor.map(start_job, pending))
            start_samples.extend(item["startApiMs"] for item in active)

            if args.exercise_retry and not retry_exercised:
                time.sleep(0.25)
                for candidate in reversed(active):
                    status_code, state_response, _ = client.json(
                        "GET", f"/api/analyses/{candidate['jobId']}/status"
                    )
                    if status_code != 200 or state_response["data"].get("status") != "QUEUED":
                        continue
                    cancel_code, cancel_response, _ = client.json(
                        "POST", f"/api/analyses/{candidate['jobId']}/cancel"
                    )
                    if cancel_code != 200:
                        break
                    canceled_status = cancel_response["data"].get("status")
                    if canceled_status != "CANCELED":
                        break
                    retry_code, retry_response, retry_ms = client.json(
                        "POST", f"/api/analyses/{candidate['jobId']}/retry"
                    )
                    if retry_code == 200:
                        retry_samples.append(retry_ms)
                        candidate["finalStatus"] = retry_response["data"].get("status", "QUEUED")
                        retry_exercised = True
                    break

            deadline = time.monotonic() + args.job_timeout_seconds
            while any(item.get("finalStatus") not in TERMINAL for item in active):
                if time.monotonic() >= deadline:
                    for item in active:
                        if item.get("finalStatus") not in TERMINAL:
                            item["finalStatus"] = "TIMEOUT"
                            item["failureReason"] = "TIMEOUT"
                    break
                for item in active:
                    if item.get("finalStatus") in TERMINAL or not item.get("jobId"):
                        continue
                    response_status, response, _ = client.json(
                        "GET", f"/api/analyses/{item['jobId']}/status"
                    )
                    if response_status != 200:
                        item["failureReason"] = safe_error(response)
                        continue
                    state = response["data"]
                    item["queuedAt"] = state.get("createdAt")
                    item["processingAt"] = state.get("startedAt")
                    item["completedAt"] = state.get("completedAt")
                    item["finalStatus"] = state.get("status")
                    if state.get("errorMessage"):
                        item["failureReason"] = state["errorMessage"]
                if any(item.get("finalStatus") not in TERMINAL for item in active):
                    time.sleep(args.poll_seconds)

            for item in active:
                item["queueWaitSeconds"] = seconds_between(item.get("queuedAt"), item.get("processingAt"))
                item["analysisSeconds"] = seconds_between(item.get("processingAt"), item.get("completedAt"))
                item["totalSeconds"] = seconds_between(item.get("queuedAt"), item.get("completedAt"))
                if item.get("finalStatus") == "DONE":
                    _, result, _ = client.json("GET", f"/api/analyses/{item['jobId']}/result")
                    item["resultId"] = ((result or {}).get("data") or {}).get("resultId")
                    item["fallback"] = fallback_flags(result)
                    item["providers"] = provider_metadata(result)
                    item["performance"] = analysis_performance(result)
                item.pop("videoId", None)
            jobs.extend(active)
    finally:
        sampler.stop()

    latency_results = measure_read_api_latency(
        client,
        email,
        password,
        jobs,
        max(1, args.latency_iterations),
    )

    if args.cleanup:
        for item in jobs:
            if item.get("finalStatus") in TERMINAL and item.get("jobId"):
                client.json("DELETE", f"/api/analyses/{item['jobId']}")

    status_counts = Counter(item.get("finalStatus", "UNKNOWN") for item in jobs)
    failures = Counter(
        str(item.get("failureReason") or "UNKNOWN")
        for item in jobs
        if item.get("finalStatus") in {"FAILED", "TIMEOUT", "UPLOAD_FAILED", "START_FAILED"}
    )
    fallback_counts = {
        name: sum(1 for item in jobs if (item.get("fallback") or {}).get(name))
        for name in ("vision", "gesture", "videoLlm", "textLlm")
    }
    done = status_counts.get("DONE", 0)
    failed = (status_counts.get("FAILED", 0) + status_counts.get("TIMEOUT", 0)
              + status_counts.get("UPLOAD_FAILED", 0) + status_counts.get("START_FAILED", 0))
    report = {
        "testStartedAt": jobs[0].get("requestAt") if jobs else None,
        "testCompletedAt": now_utc(),
        "baseUrl": args.base_url,
        "totalRequestedJobs": args.total_jobs,
        "batchSize": args.batch_size,
        "statusCounts": dict(status_counts),
        "successRate": round(done / (done + failed), 4) if done + failed else None,
        "failureReasonCounts": dict(failures),
        "fallbackCounts": fallback_counts,
        "uploadLatency": numeric_summary(upload_samples, "ms"),
        "uploadThroughput": numeric_summary(upload_throughputs, "MiBPerSecond"),
        "analysisStartLatency": numeric_summary(start_samples, "ms"),
        "retryLatency": numeric_summary(retry_samples, "ms"),
        "apiLatency": latency_results,
        "resources": sampler.summary(),
        "jobs": jobs,
        "security": "Generated account credentials, token, source paths, and filenames are not stored.",
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: report[key] for key in (
        "totalRequestedJobs", "batchSize", "statusCounts", "successRate",
        "failureReasonCounts", "fallbackCounts", "uploadLatency", "analysisStartLatency", "resources"
    )}, ensure_ascii=False, indent=2))
    return 0 if failed == 0 else 1


def numeric_summary(values: list[float], unit: str) -> dict[str, Any]:
    if not values:
        return {"count": 0, "unit": unit}
    ordered = sorted(values)
    return {
        "count": len(values),
        "unit": unit,
        "average": round(statistics.fmean(values), 2),
        "median": round(statistics.median(values), 2),
        "p95": round(ordered[min(len(ordered) - 1, int((len(ordered) - 1) * 0.95))], 2),
        "p99": round(ordered[min(len(ordered) - 1, int((len(ordered) - 1) * 0.99))], 2),
        "max": round(max(values), 2),
    }


def measure_read_api_latency(
    client: ApiClient,
    email: str,
    password: str,
    jobs: list[dict[str, Any]],
    iterations: int,
) -> list[dict[str, Any]]:
    done_job = next((item for item in jobs if item.get("finalStatus") == "DONE"), None)
    scenarios: list[tuple[str, str, str, dict[str, Any] | None, bool]] = [
        ("login", "POST", "/api/auth/login", {"email": email, "password": password}, False),
        ("reportList", "GET", "/api/analyses/reports?page=0&size=10", None, True),
    ]
    if done_job:
        scenarios.extend(
            [
                ("analysisStatus", "GET", f"/api/analyses/{done_job['jobId']}/status", None, True),
                ("resultByJob", "GET", f"/api/analyses/{done_job['jobId']}/result", None, True),
                ("resultDetail", "GET", f"/api/results/{done_job['resultId']}", None, True),
            ]
        )

    results: list[dict[str, Any]] = []
    for name, method, path, payload, authenticated in scenarios:
        samples: list[tuple[int | str, float]] = []
        for _ in range(iterations):
            previous_token = client.token
            if not authenticated:
                client.token = None
            try:
                status, _, elapsed_ms = client.json(method, path, payload)
            finally:
                client.token = previous_token
            samples.append((status, elapsed_ms))
        results.append(summarize(name, samples))
    return results


def safe_error(response: dict[str, Any] | None) -> str:
    code = (response or {}).get("errorCode")
    return str(code or "UNKNOWN")


if __name__ == "__main__":
    raise SystemExit(main())
