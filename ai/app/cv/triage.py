# SPDX-License-Identifier: Apache-2.0
"""Errors raised when an image is unsafe to continue through diagnosis."""


class CvTriageError(ValueError):
    """The image must be replaced before the diagnosis workflow can continue."""
