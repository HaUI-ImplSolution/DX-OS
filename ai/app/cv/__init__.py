# SPDX-License-Identifier: AGPL-3.0-only

"""Computer-vision components for rice leaf disease recognition.

Torchvision is imported lazily by ``app.cv.loader`` so non-CV tooling does not
load the model or trigger any external checkpoint behavior.
"""
