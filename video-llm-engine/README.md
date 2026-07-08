# Video LLM Engine

MulMoCo video frame analysis service.

Default local Ollama model settings:

```env
VIDEO_LLM_PROVIDER=OLLAMA
OLLAMA_BASE_URL=http://host.docker.internal:11434
TEXT_LLM_MODEL=qwen3:4b
VIDEO_LLM_MODEL=qwen3-vl:4b
```

`qwen3:4b` is reserved for text explanation/final feedback.
`qwen3-vl:4b` is used for video frame image interpretation.
