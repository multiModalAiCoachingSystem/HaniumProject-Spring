from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request


def request(
    base_url: str,
    method: str,
    path: str,
    *,
    payload: dict | None = None,
    token: str | None = None,
    headers: dict[str, str] | None = None,
) -> tuple[int, dict, dict]:
    body = json.dumps(payload).encode("utf-8") if payload is not None else None
    request_headers = {"Content-Type": "application/json"}
    if token:
        request_headers["Authorization"] = f"Bearer {token}"
    if headers:
        request_headers.update(headers)

    req = urllib.request.Request(
        f"{base_url}{path}",
        data=body,
        headers=request_headers,
        method=method,
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            raw = response.read().decode("utf-8")
            return response.status, json.loads(raw) if raw else {}, dict(response.headers)
    except urllib.error.HTTPError as error:
        raw = error.read().decode("utf-8")
        try:
            parsed = json.loads(raw) if raw else {}
        except json.JSONDecodeError:
            parsed = {}
        return error.code, parsed, dict(error.headers)


def expect(name: str, actual: int, expected: set[int]) -> None:
    print(f"{name}: status={actual}")
    if actual not in expected:
        raise AssertionError(f"{name} expected {sorted(expected)}, got {actual}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    args = parser.parse_args()

    email = "ci-smoke@example.com"
    password = "CiTestPassword123!"

    status, _, _ = request(args.base_url, "GET", "/api/health")
    expect("health", status, {200})

    status, _, _ = request(
        args.base_url,
        "POST",
        "/api/auth/signup",
        payload={
            "email": email,
            "password": password,
            "passwordConfirm": password,
            "nickname": "CI 사용자",
        },
    )
    expect("signup", status, {200, 201})

    status, login_body, _ = request(
        args.base_url,
        "POST",
        "/api/auth/login",
        payload={"email": email, "password": password},
    )
    expect("login", status, {200})
    token = login_body.get("data", {}).get("accessToken")
    if not token:
        raise AssertionError("login response did not contain an access token")

    status, _, _ = request(args.base_url, "GET", "/api/auth/me", token=token)
    expect("authenticated-me", status, {200})

    status, _, _ = request(
        args.base_url,
        "GET",
        "/api/analyses/reports?page=0&size=10",
        token=token,
    )
    expect("authenticated-reports", status, {200})

    status, _, _ = request(args.base_url, "GET", "/api/auth/me")
    expect("unauthenticated-me", status, {401, 403})

    status, _, response_headers = request(
        args.base_url,
        "OPTIONS",
        "/api/auth/me",
        headers={
            "Origin": "http://localhost:5173",
            "Access-Control-Request-Method": "GET",
        },
    )
    expect("cors-preflight", status, {200})
    allowed_origin = response_headers.get("Access-Control-Allow-Origin")
    if allowed_origin != "http://localhost:5173":
        raise AssertionError("CORS did not return the configured origin")

    rate_limited = False
    for _ in range(6):
        status, _, _ = request(
            args.base_url,
            "POST",
            "/api/auth/login",
            payload={"email": email, "password": "incorrect-password"},
        )
        if status == 429:
            rate_limited = True
            break
    print(f"login-rate-limit: status={status}")
    if not rate_limited:
        raise AssertionError("login rate limit did not return 429")

    print("backend smoke test completed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
