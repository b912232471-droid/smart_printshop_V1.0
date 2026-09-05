FROM python:3.12-slim

WORKDIR /app

# 使用阿里云Debian镜像源加速
RUN sed -i 's/deb.debian.org/mirrors.aliyun.com/g' /etc/apt/sources.list.d/debian.sources 2>/dev/null || true; \
    sed -i 's/security.debian.org/mirrors.aliyun.com/g' /etc/apt/sources.list.d/debian.sources 2>/dev/null || true; \
    apt-get update && apt-get install -y \
    libgl1 \
    libglib2.0-0t64 \
    libgomp1 \
    wget \
    && rm -rf /var/lib/apt/lists/*

# 安装Python依赖（分步安装，提高稳定性）
COPY requirements.txt .
RUN pip install --no-cache-dir --retries 5 --timeout 60 \
    -i https://mirrors.aliyun.com/pypi/simple/ \
    -r requirements.txt

# 复制应用代码
COPY . .

# 创建必要目录
RUN mkdir -p pretrained static uploads

# 暴露端口
EXPOSE 8091

# 启动命令
CMD ["sh", "-c", "uvicorn app.main:app --host ${PHOTO_HOST:-0.0.0.0} --port ${PHOTO_PORT:-8091}"]
