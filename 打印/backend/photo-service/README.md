# Photo Service

AI portrait service for the printshop platform, built with FastAPI. Runs on
port `8091`, is stateless (no database) and is scaled horizontally in
production (`--scale photo-service=3`).

Endpoints (under `/api/photo` via the gateway):

- `POST /api/photo/generate-id-photo`: generate an ID photo from a portrait.
- `POST /api/photo/change-background`: replace the background colour of an ID photo.
- `GET /api/photo/health`: health check.
- `GET /api/photo/metrics`: JSON runtime metrics (`GET /metrics` for Prometheus text).

Layout:

- `app/main.py`: FastAPI entrypoint with lifespan (Nacos registration).
- `app/api/v1/`: route handlers.
- `app/services/`: matting, face detection and image processing services.
- `app/core/`: config, security, Nacos and metrics plumbing.
- `pretrained/`: ONNX models (MODNet matting).
Heavy inference dependencies (onnxruntime, mediapipe, opencv) are installed in
the Docker image; syntax self-check works anywhere with
`python -m compileall app`.
