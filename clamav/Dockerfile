# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
# ClamAV of the virus scan: the official image without libclamunrar, whose UnRAR license is not an OSI license.
# Without it clamd cannot open a RAR archive, so the backend refuses RAR files (AttachmentProcessor).
FROM clamav/clamav:1.5.4@sha256:638d90201ee7ee6a82d249a2fc23b132e578673b5f1f2766852b127676ca1ef6
RUN rm -f /usr/lib/libclamunrar.so* /usr/lib/libclamunrar_iface.so*
COPY licenses/ /licenses/
