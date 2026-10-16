FROM python:3.11-slim AS cv-weights
WORKDIR /build
COPY scripts/assemble_cv_weights.py ./assemble_cv_weights.py
COPY weights/dx-farm-efficientnetv2m ./parts
RUN python assemble_cv_weights.py --source /build/parts --output /build/model.pth

FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

# libgomp is required by the CPU builds of PyTorch used for CV inference.
RUN apt-get update \
    && apt-get install --no-install-recommends -y libgomp1 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt requirements.lock ./
RUN pip install --extra-index-url https://download.pytorch.org/whl/cpu -r requirements.txt -c requirements.lock

COPY app ./app
COPY scripts ./scripts
COPY --from=cv-weights /build/model.pth /opt/dxos/models/efficientnetv2m_rice_disease.pth
ENV CV_WEIGHTS_PATH=/opt/dxos/models/efficientnetv2m_rice_disease.pth

RUN useradd --create-home --uid 10001 appuser \
    && mkdir /app/weights \
    && chown -R appuser:appuser /app

USER appuser

# Cache embedding assets here; the bundled CV checkpoint stays outside this volume.
VOLUME ["/app/weights"]

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
    CMD python -c "from urllib.request import urlopen; urlopen('http://127.0.0.1:8000/api/v1/disease/health', timeout=3)"

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
