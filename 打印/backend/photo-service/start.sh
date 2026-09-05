#!/bin/bash
cd /home/lihaoguang/photo-api
source venv/bin/activate
python3 -m uvicorn app.main:app --host 0.0.0.0 --port ${PHOTO_PORT:-8091}
