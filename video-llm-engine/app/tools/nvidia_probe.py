from __future__ import annotations

import base64
import json
import os
import urllib.error
import urllib.request


SYNTHETIC_PNG_BASE64 = (
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII="
)


def main() -> None:
    endpoint = os.environ.get("NVIDIA_API_BASE_URL")
    model = os.environ.get("VIDEO_LLM_MODEL")
    api_key = os.environ.get("NVIDIA_API_KEY")

    print("NVIDIA_API_KEY", "SET" if api_key else "MISSING")
    print("NVIDIA_API_BASE_URL", endpoint or "")
    print("VIDEO_LLM_MODEL", model or "")

    if not endpoint or not model or not api_key:
        print("probeStatus", "SKIPPED_MISSING_ENV")
        return

    _run_probe("textOnly", endpoint, model, api_key, _text_payload(model))
    _run_probe("image1", endpoint, model, api_key, _image_payload(model, 1))
    _run_probe("image8", endpoint, model, api_key, _image_payload(model, 8))


def _text_payload(model: str) -> dict:
    return {
        "model": model,
        "messages": [
            {"role": "system", "content": "/no_think"},
            {"role": "user", "content": "Describe a presentation scene briefly."},
        ],
        "stream": False,
        "max_tokens": 64,
        "temperature": 0.2,
        "top_p": 1.0,
    }


def _image_payload(model: str, image_count: int) -> dict:
    content = [{"type": "text", "text": f"Describe these {image_count} simple test image(s) briefly."}]
    for _ in range(image_count):
        content.append(
            {
                "type": "image_url",
                "image_url": {
                    "url": "data:image/png;base64," + SYNTHETIC_PNG_BASE64,
                },
            }
        )
    return {
        "model": model,
        "messages": [
            {"role": "system", "content": "/no_think"},
            {"role": "user", "content": content},
        ],
        "stream": False,
        "max_tokens": 128,
        "temperature": 0.2,
        "top_p": 1.0,
    }


def _run_probe(name: str, endpoint: str, model: str, api_key: str, payload: dict) -> None:
    encoded = json.dumps(payload).encode("utf-8")
    print(f"{name}.payloadBytes", len(encoded))
    request = urllib.request.Request(
        endpoint,
        data=encoded,
        headers={
            "Authorization": "Bearer " + api_key,
            "Content-Type": "application/json",
            "Accept": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            data = json.loads(response.read().decode("utf-8", errors="replace"))
            content = _extract_content(data)
            print(f"{name}.statusCode", response.status)
            print(f"{name}.choicesPresent", bool(data.get("choices")))
            print(f"{name}.contentPresent", bool(content))
            print(f"{name}.contentLength", len(str(content or "")))
    except urllib.error.HTTPError as exc:
        print(f"{name}.statusCode", exc.code)
        print(f"{name}.errorType", _safe_http_error_type(exc))
    except (urllib.error.URLError, TimeoutError) as exc:
        print(f"{name}.statusCode", "NO_HTTP_RESPONSE")
        print(f"{name}.errorType", exc.__class__.__name__)


def _extract_content(data: dict) -> str:
    choices = data.get("choices")
    if not isinstance(choices, list) or not choices:
        return ""
    message = choices[0].get("message") if isinstance(choices[0], dict) else None
    if not isinstance(message, dict):
        return ""
    return str(message.get("content") or "")


def _safe_http_error_type(exc: urllib.error.HTTPError) -> str:
    try:
        raw = exc.read().decode("utf-8", errors="replace")[:1200]
        data = json.loads(raw)
        error = data.get("error", data)
        if isinstance(error, dict):
            return str(error.get("type") or error.get("code") or error.get("status") or "UNKNOWN")
    except Exception:
        pass
    return "UNKNOWN"


if __name__ == "__main__":
    main()
