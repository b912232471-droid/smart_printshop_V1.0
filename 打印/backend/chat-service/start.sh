#!/bin/sh
set -e
uvicorn app.main:app --host "${CHAT_HOST:-0.0.0.0}" --port "${CHAT_PORT:-8093}"
