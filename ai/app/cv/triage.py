# SPDX-License-Identifier: AGPL-3.0-only
"""Errors raised when an image is unsafe to continue through diagnosis."""


class CvTriageError(ValueError):
    """The image must be replaced before the diagnosis workflow can continue."""
