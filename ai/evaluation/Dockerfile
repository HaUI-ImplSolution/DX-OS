FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1
WORKDIR /app

# Match the locked runtime layer; the build context contains no app, env or weights.
RUN apt-get update \
    && apt-get install --no-install-recommends -y libgomp1 \
    && rm -rf /var/lib/apt/lists/*
COPY requirements.txt requirements.lock ./
RUN pip install --extra-index-url https://download.pytorch.org/whl/cpu -r requirements.txt -c requirements.lock

RUN apt-get update \
    && apt-get install --no-install-recommends -y ca-certificates curl docker-cli \
    && rm -rf /var/lib/apt/lists/*
COPY requirements-eval.txt ./
RUN python -m venv --system-site-packages /opt/eval \
    && /opt/eval/bin/pip install -r requirements-eval.txt
ARG K6_VERSION
RUN test -n "$K6_VERSION" \
    && curl -fsSL "https://github.com/grafana/k6/releases/download/v${K6_VERSION}/k6-v${K6_VERSION}-linux-amd64.tar.gz" \
       | tar -xz -C /usr/local/bin --strip-components=1 "k6-v${K6_VERSION}-linux-amd64/k6"
ENV PATH="/opt/eval/bin:${PATH}"
ENTRYPOINT ["python"]
