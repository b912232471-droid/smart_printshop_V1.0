FROM python:3.12-slim

WORKDIR /app

RUN sed -i 's/deb.debian.org/mirrors.aliyun.com/g' /etc/apt/sources.list.d/debian.sources 2>/dev/null || true; \
    sed -i 's/security.debian.org/mirrors.aliyun.com/g' /etc/apt/sources.list.d/debian.sources 2>/dev/null || true

COPY requirements.txt .
RUN pip install --no-cache-dir --retries 5 --timeout 60 \
    -i https://mirrors.aliyun.com/pypi/simple/ \
    -r requirements.txt

COPY . .

EXPOSE 8093

CMD ["sh", "-c", "uvicorn app.main:app --host ${CHAT_HOST:-0.0.0.0} --port ${CHAT_PORT:-8093}"]
