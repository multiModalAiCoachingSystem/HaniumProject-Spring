#!/usr/bin/env python3
"""Measure authenticated API latency without persisting credentials or tokens."""

from __future__ import annotations

import argparse
import json
import os
import statistics
import time
import urllib.error
import urllib.request
from collections import Counter
from pathlib import Path
from typing import Any


def percentile(values: list[float], percent: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    index = (len(ordered) - 1) * percent
    lower = int(index)
    upper = min(lower + 1, len(ordered) - 1)
    fraction = index - lower
    return ordered[lower] + (ordered[upper] - ordered[lower]) * fraction


def request_json(
    base_url: str,
    method: str,
    path: str,
    *,
    token: str | None = None,
    payload: dict[str, Any] | None = None,
    timeout: float = 10.0,
) -> tuple[int | str, dict[str, Any] | None, float]:
    body = json.dumps(payload).encode("utf-8") if payload is not None else None
    headers = {"Accept": "application/json"}
    if body is not None:
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(base_url.rstrip("/") + path, data=body, headers=headers, method=method)
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read()
            parsed = json.loads(raw) if raw else None
            return response.status, parsed, (time.perf_counter() - started) * 1000.0
    except urllib.error.HTTPError as exc:
        exc.read()
        return exc.code, None, (time.perf_counter() - started) * 1000.0
    except (TimeoutError, urllib.error.URLError):
        return "TIMEOUT", None, (time.perf_counter() - started) * 1000.0


def summarize(name: str, samples: list[tuple[int | str, float]]) -> dict[str, Any]:
    latencies = [latency for _, latency in samples]
    statuses = Counter(str(status) for status, _ in samples)
    successful = sum(count for status, count in statuses.items() if status.startswith("2"))
    return {
        "name": name,
        "count": len(samples),
        "averageMs": round(statistics.fmean(latencies), 2) if latencies else None,
        "medianMs": round(statistics.median(latencies), 2) if latencies else None,
        "p95Ms": round(percentile(latencies, 0.95), 2) if latencies else None,
        "p99Ms": round(percentile(latencies, 0.99), 2) if latencies else None,
        "maxMs": round(max(latencies), 2) if latencies else None,
        "errorRate": round(1.0 - successful / len(samples), 4) if samples else None,
        "statusCounts": dict(statuses),
        "timeoutCount": statuses.get("TIMEOUT", 0),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost")
    parser.add_argument("--iterations", type=int, default=50)
    parser.add_argument("--warmup", type=int, default=5)
    parser.add_argument("--job-id", type=int)
    parser.add_argument("--result-id", type=int)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()

    email = os.getenv("PERF_EMAIL")
    password = os.getenv("PERF_PASSWORD")
    if not email or not password:
        raise SystemExit("PERF_EMAIL and PERF_PASSWORD must be set in the current process.")

    login_payload = {"email": email, "password": password}
    status, login_response, _ = request_json(args.base_url, "POST", "/api/auth/login", payload=login_payload)
    if status != 200 or not login_response:
        raise SystemExit(f"Login failed with HTTP {status}.")
    token = login_response.get("data", {}).get("accessToken")
    if not token:
        raise SystemExit("Login response did not contain an access token.")

    for _ in range(max(0, args.warmup)):
        request_json(args.base_url, "GET", "/api/analyses/reports?page=0&size=10", token=token)

    scenarios: list[tuple[str, str, str, str | None]] = [
        ("login", "POST", "/api/auth/login", "login"),
        ("reportList", "GET", "/api/analyses/reports?page=0&size=10", None),
    ]
    if args.job_id:
        scenarios.append(("analysisStatus", "GET", f"/api/analyses/{args.job_id}/status", None))
        scenarios.append(("resultByJob", "GET", f"/api/analyses/{args.job_id}/result", None))
    if args.result_id:
        scenarios.append(("resultDetail", "GET", f"/api/results/{args.result_id}", None))

    results = []
    for name, method, path, payload_kind in scenarios:
        samples: list[tuple[int | str, float]] = []
        for _ in range(args.iterations):
            payload = login_payload if payload_kind == "login" else None
            request_token = None if name == "login" else token
            response_status, _, elapsed_ms = request_json(
                args.base_url,
                method,
                path,
                token=request_token,
                payload=payload,
            )
            samples.append((response_status, elapsed_ms))
        results.append(summarize(name, samples))

    report = {
        "baseUrl": args.base_url,
        "iterations": args.iterations,
        "warmup": args.warmup,
        "results": results,
        "security": "Credentials, user identifiers, and authorization tokens are not stored.",
    }
    rendered = json.dumps(report, ensure_ascii=False, indent=2)
    print(rendered)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
