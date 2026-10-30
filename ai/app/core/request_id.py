# SPDX-License-Identifier: AGPL-3.0-only
"""Gives every request the id the gateway chose, so one id finds it in the gateway, backend and AI
logs. The backend forwards its X-Request-Id; a request without a usable one gets a new id."""

import logging
import re
import time
import uuid

from fastapi import FastAPI, Request

SAFE_ID = re.compile(r"[A-Za-z0-9._-]{1,64}")

access_log = logging.getLogger("app.access")
if not access_log.handlers:
    # The application sets no logging of its own and uvicorn only configures its own loggers.
    handler = logging.StreamHandler()
    handler.setFormatter(logging.Formatter("%(asctime)s %(message)s"))
    access_log.addHandler(handler)
    access_log.setLevel(logging.INFO)
    access_log.propagate = False


def install_request_id(application: FastAPI) -> None:
    @application.middleware("http")
    async def request_id(request: Request, call_next):
        incoming = request.headers.get("x-request-id", "")
        value = incoming if SAFE_ID.fullmatch(incoming) else uuid.uuid4().hex
        started = time.perf_counter()
        status = 500
        try:
            response = await call_next(request)
            status = response.status_code
            response.headers["X-Request-Id"] = value
            return response
        finally:
            access_log.info(
                "request_id=%s %s %s status=%d %.0f ms",
                value,
                request.method,
                request.url.path,
                status,
                (time.perf_counter() - started) * 1000,
            )
