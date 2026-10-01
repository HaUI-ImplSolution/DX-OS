FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

# libgomp is required by the CPU builds of PyTorch used for CV inference.
RUN apt-get update \
    && apt-get install --no-install-recommends -y libgomp1 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt ./
RUN pip install --extra-index-url https://download.pytorch.org/whl/cpu -r requirements.txt

# Ultralytics imports OpenCV at startup. The non-headless OpenCV wheel needs
# these shared libraries even though inference itself runs without a GUI.
RUN sed -i 's|^URIs: http://deb.debian.org/debian$|URIs: http://mirror.bizflycloud.vn/debian|' \
      /etc/apt/sources.list.d/debian.sources \
    && apt-get update \
    && apt-get install --no-install-recommends -y libgl1 libglib2.0-0 libxcb1 \
    && rm -rf /var/lib/apt/lists/*

COPY app ./app
COPY scripts ./scripts

RUN useradd --create-home --uid 10001 appuser \
    && mkdir /app/weights \
    && chown -R appuser:appuser /app

USER appuser

# Cache embedding assets here. The CV checkpoint is mounted separately as a
# read-only file by Compose and is never downloaded by the service.
VOLUME ["/app/weights"]

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
    CMD python -c "from urllib.request import urlopen; urlopen('http://127.0.0.1:8000/api/v1/disease/health', timeout=3)"

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
