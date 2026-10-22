# SPDX-License-Identifier: Apache-2.0
"""PyTorch kernel selection for the CPU the service runs on."""

import logging
from functools import cache
from pathlib import Path

import torch

logger = logging.getLogger(__name__)

_CPUINFO = Path("/proc/cpuinfo")


def _cpu_flags() -> set[str] | None:
    try:
        text = _CPUINFO.read_text()
    except OSError:
        return None
    for line in text.splitlines():
        if line.startswith("flags"):
            return set(line.partition(":")[2].split())
    return None


@cache
def configure_torch_cpu() -> None:
    """Use the built-in PyTorch kernels on an x86 CPU without SSE4.1.

    oneDNN cannot create convolutions on such a CPU and NNPACK rejects it.
    """
    flags = _cpu_flags()
    if flags is None or "sse4_1" in flags:
        return
    torch.backends.mkldnn.enabled = False
    torch.backends.nnpack.set_flags(False)
    logger.warning("CPU without SSE4.1: oneDNN and NNPACK are off, inference is slower")
