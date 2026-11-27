# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
# ClamAV of the virus scan: the official image without libclamunrar, whose UnRAR license is not an OSI license.
# Without it clamd cannot open a RAR archive, so the backend refuses RAR files (AttachmentProcessor).
FROM clamav/clamav:1.5.4@sha256:638d90201ee7ee6a82d249a2fc23b132e578673b5f1f2766852b127676ca1ef6 AS stripped
RUN rm -f /usr/lib/libclamunrar.so* /usr/lib/libclamunrar_iface.so*

# The files are copied into an empty image, so no layer keeps the removed libraries.
# The settings below repeat those of the official image: compare them with `docker image inspect` when its version changes.
FROM scratch
COPY --from=stripped / /
COPY licenses/ /licenses/
ENV PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin TZ=Etc/UTC
WORKDIR /
EXPOSE 3310 7357
LABEL maintainer="ClamAV bugs <clamav-bugs@external.cisco.com>"
HEALTHCHECK --start-period=6m CMD clamdcheck.sh
ENTRYPOINT ["/init"]
