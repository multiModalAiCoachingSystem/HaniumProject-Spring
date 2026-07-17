# Video LLM Engine

MulMoCo video frame analysis service.

Default provider-oriented settings:

```env
VIDEO_LLM_PROVIDER=NVIDIA_NIM
VIDEO_LLM_MODEL=nvidia/nemotron-nano-12b-v2-vl
VIDEO_LLM_FALLBACK_PROVIDER=OLLAMA
NVIDIA_API_BASE_URL=https://integrate.api.nvidia.com/v1/chat/completions
NVIDIA_API_KEY=
OLLAMA_BASE_URL=http://host.docker.internal:11434
OLLAMA_VIDEO_LLM_MODEL=qwen3-vl:4b
TEXT_LLM_MODEL=qwen3:4b
```

`nvidia/nemotron-nano-12b-v2-vl` is used for visual frame observations when `VIDEO_LLM_PROVIDER=NVIDIA_NIM`.
`qwen3-vl:4b` remains available as the Ollama Video LLM fallback.
`qwen3:4b` is reserved for backend text explanation/final Korean feedback.

Do not commit real `NVIDIA_API_KEY` values.
