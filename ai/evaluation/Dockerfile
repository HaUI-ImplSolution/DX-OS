FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    HF_HOME=/app/weights/huggingface

WORKDIR /app

RUN apt-get update \
    && apt-get install --no-install-recommends -y ca-certificates curl docker.io unzip \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt requirements.lock requirements-eval.txt ./
RUN pip install --extra-index-url https://download.pytorch.org/whl/cpu \
    -r requirements.txt -c requirements.lock \
    && pip install -r requirements-eval.txt

COPY app ./app
COPY scripts ./scripts
COPY evaluation/prepare.py ./evaluation/prepare.py
COPY evaluation/k6.version ./evaluation/k6.version

ARG K6_VERSION=0.49.0
RUN arch="$(dpkg --print-architecture)" \
    && case "$arch" in amd64) k6_arch=amd64 ;; arm64) k6_arch=arm64 ;; *) echo "Unsupported architecture: $arch" >&2; exit 1 ;; esac \
    && curl -fsSL "https://github.com/grafana/k6/releases/download/v${K6_VERSION}/k6-v${K6_VERSION}-linux-${k6_arch}.tar.gz" \
       | tar -xz -C /usr/local/bin --strip-components=1 "k6-v${K6_VERSION}-linux-${k6_arch}/k6"

RUN useradd --create-home --uid 10001 appuser \
    && mkdir -p /app/weights /artifacts \
    && chown -R appuser:appuser /app /artifacts

USER appuser
VOLUME ["/app/weights", "/artifacts"]
EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
