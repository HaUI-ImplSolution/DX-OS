# Gói bên trong các image bên thứ ba

Tệp này do `.github/scripts/check_image_packages.py generate` sinh, không sửa tay.
Mỗi bảng liệt kê gói của một image mà stack chạy, ở đúng tag và digest repository ghim, kèm giấy phép mà bản phân phối của gói ghi lại.
Danh sách do Trivy đọc từ cơ sở dữ liệu gói của image; một gói có nhiều tệp thì cột giấy phép gộp giấy phép của các tệp đó.
Cột Ngoài OSI nêu các giấy phép chưa nằm trong `.github/licenses/osi-approved.json`; chúng không bị ẩn.
Image bên thứ ba chạy trong stack được kéo nguyên bản; image dự án phát hành chứa lớp nền của `eclipse-temurin`, `nginx`, `node` và `clamav/clamav`, nên các lớp đó được phân phối lại.
Các gói này giữ giấy phép do nhà phân phối hệ điều hành của image ghi.
Mô-đun Go biên dịch vào tệp chạy và gói npm không ghi giấy phép trong image; giấy phép của chúng lấy từ deps.dev theo đúng phiên bản, và `(not recorded)` nghĩa là deps.dev không ghi giấy phép nào.
Image `clamav/clamav` là nền của image `clamav` của dự án, chỉ xóa libclamunrar, nên bảng gói của nó vẫn đúng.
Giấy phép của từng thành phần cấp cao và của từng gói thư viện của dự án ở [`DEPENDENCY_INVENTORY.md`](./DEPENDENCY_INVENTORY.md).

| Image | Gói | Gói có giấy phép ngoài OSI |
|---|---|---|
| `alpine:3.24` | 16 | 0 |
| `apache/apisix:3.11.0-debian` | 106 | 41 |
| `chrislusf/seaweedfs:4.51` | 274 | 1 |
| `clamav/clamav:1.5.4` | 41 | 10 |
| `duckdb/duckdb:1.4.1` | 10 | 4 |
| `eclipse-mosquitto:2.0.23` | 20 | 1 |
| `eclipse-temurin:21-jdk-alpine` | 77 | 10 |
| `eclipse-temurin:21-jre-alpine` | 73 | 10 |
| `eclipse-temurin:21-jre-jammy` | 143 | 68 |
| `grafana/grafana:12.4.15` | 588 | 4 |
| `nginx:1.30-alpine-slim` | 21 | 2 |
| `node:22-alpine` | 206 | 2 |
| `node:24.16.0-alpine` | 164 | 2 |
| `pgplex/pgschema:1.13.4` | 32 | 1 |
| `postgres:16-bookworm` | 148 | 93 |
| `prom/prometheus:v3.13.5` | 237 | 1 |
| `python:3.12-alpine` | 39 | 7 |
| `python:3.13.18-alpine3.24` | 48 | 7 |
| `restic/restic:0.17.3` | 100 | 6 |

## alpine:3.24

| Gói | Phiên bản | Giấy phép | Ngoài OSI |
|---|---|---|---|
| `alpine-baselayout` | 3.7.2-r1 | GPL-2.0-only |  |
| `alpine-baselayout-data` | 3.7.2-r1 | GPL-2.0-only |  |
| `alpine-keys` | 2.6-r0 | MIT |  |
| `alpine-release` | 3.24.4-r0 | MIT |  |
| `apk-tools` | 3.0.9-r0 | GPL-2.0-only |  |
| `busybox` | 1.37.0-r31 | GPL-2.0-only |  |
| `busybox-binsh` | 1.37.0-r31 | GPL-2.0-only |  |
| `ca-certificates-bundle` | 20261020-r0 | MIT, MPL-2.0 |  |
| `libapk` | 3.0.9-r0 | GPL-2.0-only |  |
| `libcrypto3` | 3.5.9-r0 | Apache-2.0 |  |
| `libssl3` | 3.5.9-r0 | Apache-2.0 |  |
| `musl` | 1.2.6-r3 | MIT |  |
| `musl-utils` | 1.2.6-r3 | BSD-2-Clause, GPL-2.0-or-later, MIT |  |
| `scanelf` | 1.3.9-r1 | GPL-2.0-only |  |
| `ssl_client` | 1.37.0-r31 | GPL-2.0-only |  |
| `zlib` | 1.3.2-r1 | Zlib |  |

## apache/apisix:3.11.0-debian

| Gói | Phiên bản | Giấy phép | Ngoài OSI |
|---|---|---|---|
| `adduser` | 3.118+deb11u1 | GPL-2.0-only |  |
| `apisix` | 3.11.0-0 | (not recorded) |  |
| `apt` | 2.2.4 | GPL-2.0-only, GPL-2.0-or-later |  |
| `base-files` | 11.1+deb11u11 | GPL-2.0-or-later |  |
| `base-passwd` | 3.5.51 | GPL-2.0-only, public-domain | public-domain |
| `bash` | 5.1-2+deb11u1 | GPL-3.0-only |  |
| `bsdutils` | 1:2.36.1-8+deb11u2 | BSD-2-Clause, BSD-3-Clause, BSD-4-Clause, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.0-only, LGPL-2.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, public-domain | BSD-4-Clause, public-domain |
| `ca-certificates` | 20210119 | GPL-2.0-only, GPL-2.0-or-later, MPL-2.0 |  |
| `coreutils` | 8.32-4+b1 | GPL-3.0-only |  |
| `dash` | 0.5.11+git20200708+dd9ef66-5 | BSD-3-Clause, FSFUL, FSFULLR, GPL-2.0-only, GPL-2.0-or-later, MIT, public-domain | FSFUL, FSFULLR, public-domain |
| `debconf` | 1.5.77 | BSD-2-Clause |  |
| `debian-archive-keyring` | 2021.1.1+deb11u1 | GPL-2.0-or-later |  |
| `debianutils` | 4.11.2 | GPL-2.0-only |  |
| `diffutils` | 1:3.7-5 | GFDL-1.3-or-later, GPL-2.0-or-later | GFDL-1.3-or-later |
| `dpkg` | 1.20.13 | BSD-2-Clause, GPL-2.0-only, GPL-2.0-or-later, public-domain-md5, public-domain-s-s-d | public-domain-md5, public-domain-s-s-d |
| `e2fsprogs` | 1.46.2-2+deb11u1 | GPL-2.0-only, LGPL-2.0-only |  |
| `findutils` | 4.8.0-1 | GFDL-1.3-only, GPL-3.0-only | GFDL-1.3-only |
| `gcc-10-base` | 10.2.1-6 | Artistic-2.0, GFDL-1.2-only, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, LGPL-2.0-or-later | GFDL-1.2-only |
| `gcc-9-base` | 9.3.0-22 | Artistic-2.0, GFDL-1.2-only, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, LGPL-2.0-or-later, LGPL-2.1-or-later | GFDL-1.2-only |
| `gpgv` | 2.2.27-2+deb11u2 | BSD-3-Clause, CC0-1.0, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, RFC-Reference, TinySCHEME, permissive | CC0-1.0, RFC-Reference, TinySCHEME, permissive |
| `grep` | 3.6-1+deb11u1 | GPL-3.0-only, GPL-3.0-or-later |  |
| `gzip` | 1.10-4+deb11u1 | FSF-manpages, GFDL-1.3+-no-invariant, GFDL-3, GPL-3.0-only, GPL-3.0-or-later | FSF-manpages, GFDL-1.3+-no-invariant, GFDL-3 |
| `hostname` | 3.23 | GPL-2.0-only |  |
| `init-system-helpers` | 1.60 | BSD-3-Clause, GPL-2.0-only, GPL-2.0-or-later |  |
| `libacl1` | 2.2.53-10 | GPL-2.0-only, GPL-2.0-or-later, LGPL-2.0-or-later, LGPL-2.1-only |  |
| `libapt-pkg6.0` | 2.2.4 | GPL-2.0-only, GPL-2.0-or-later |  |
| `libattr1` | 1:2.4.48-6 | GPL-2.0-only, GPL-2.0-or-later, LGPL-2.0-or-later, LGPL-2.1-only |  |
| `libaudit-common` | 1:3.0-2 | GPL-1.0-only, GPL-2.0-only, LGPL-2.1-only | GPL-1.0-only |
| `libaudit1` | 1:3.0-2 | GPL-1.0-only, GPL-2.0-only, LGPL-2.1-only | GPL-1.0-only |
| `libblkid1` | 2.36.1-8+deb11u2 | BSD-2-Clause, BSD-3-Clause, BSD-4-Clause, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.0-only, LGPL-2.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, public-domain | BSD-4-Clause, public-domain |
| `libbz2-1.0` | 1.0.8-4 | BSD-3-Clause, GPL-2.0-only |  |
| `libc-bin` | 2.31-13+deb11u11 | GPL-2.0-only, LGPL-2.1-only |  |
| `libc6` | 2.31-13+deb11u11 | GPL-2.0-only, LGPL-2.1-only |  |
| `libcap-ng0` | 0.7.9-2.2+b1 | GPL-2.0-only, GPL-3.0-only, LGPL-2.1-only |  |
| `libcom-err2` | 1.46.2-2+deb11u1 | (not recorded) |  |
| `libcrypt1` | 1:4.4.18-4 | (not recorded) |  |
| `libdb5.3` | 5.3.28+dfsg1-0.8 | (not recorded) |  |
| `libdebconfclient0` | 0.260 | (not recorded) |  |
| `libext2fs2` | 1.46.2-2+deb11u1 | GPL-2.0-only, LGPL-2.0-only |  |
| `libffi7` | 3.3-6 | GPL-2.0-or-later |  |
| `libgcc-s1` | 10.2.1-6 | (not recorded) |  |
| `libgcrypt20` | 1.8.7-6 | GPL-2.0-only, LGPL-2.0-or-later |  |
| `libgmp10` | 2:6.2.1+dfsg-1+deb11u1 | GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, LGPL-3.0-only |  |
| `libgnutls30` | 3.7.1-5+deb11u6 | Apache-2.0, BSD-3-Clause, CC0-1.0, GFDL-1.3-only, GPL-2.0-or-later, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT | CC0-1.0, GFDL-1.3-only |
| `libgpg-error0` | 1.38-2 | BSD-3-Clause, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, g10-permissive | g10-permissive |
| `libgssapi-krb5-2` | 1.18.3-6+deb11u5 | GPL-2.0-only |  |
| `libhogweed6` | 3.7.3-1 | GAP, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-or-later, GPL-3.0-with-autoconf-exception+, LGPL-2.0-only, LGPL-2.0-or-later, LGPL-3.0-or-later, MIT, public-domain | GAP, GPL-3.0-with-autoconf-exception+, public-domain |
| `libidn2-0` | 2.3.0-5 | GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, GPL-3.0-or-later, LGPL-3.0-only, LGPL-3.0-or-later, Unicode | Unicode |
| `libk5crypto3` | 1.18.3-6+deb11u5 | GPL-2.0-only |  |
| `libkeyutils1` | 1.6.1-2 | GPL-2.0-only, GPL-2.0-or-later, LGPL-2.0-only, LGPL-2.0-or-later |  |
| `libkrb5-3` | 1.18.3-6+deb11u5 | GPL-2.0-only |  |
| `libkrb5support0` | 1.18.3-6+deb11u5 | GPL-2.0-only |  |
| `libldap-2.4-2` | 2.4.57+dfsg-3+deb11u1 | (not recorded) |  |
| `libldap2-dev` | 2.4.57+dfsg-3+deb11u1 | (not recorded) |  |
| `liblz4-1` | 1.9.3-2 | BSD-2-Clause, GPL-2.0-only, GPL-2.0-or-later |  |
| `liblzma5` | 5.2.5-2.1~deb11u1 | Autoconf, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, LGPL-2.0-only, LGPL-2.1-only, LGPL-2.1-or-later, PD, PD-debian, config-h, noderivs, none, permissive-fsf, permissive-nowarranty, probably-PD | Autoconf, PD, PD-debian, config-h, noderivs, none, permissive-fsf, permissive-nowarranty, probably-PD |
| `libmount1` | 2.36.1-8+deb11u2 | BSD-2-Clause, BSD-3-Clause, BSD-4-Clause, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.0-only, LGPL-2.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, public-domain | BSD-4-Clause, public-domain |
| `libnettle8` | 3.7.3-1 | GAP, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-or-later, GPL-3.0-with-autoconf-exception+, LGPL-2.0-only, LGPL-2.0-or-later, LGPL-3.0-or-later, MIT, public-domain | GAP, GPL-3.0-with-autoconf-exception+, public-domain |
| `libnsl2` | 1.3.0-2 | BSD-3-Clause, GPL-2+-autoconf-exception, GPL-2+-libtool-exception, GPL-2.0-only, GPL-3+-autoconf-exception, GPL-3.0-only, LGPL-2.1-only, LGPL-2.1-or-later, MIT, permissive-autoconf-m4, permissive-autoconf-m4-no-warranty, permissive-configure, permissive-fsf, permissive-makefile-in | GPL-2+-autoconf-exception, GPL-2+-libtool-exception, GPL-3+-autoconf-exception, permissive-autoconf-m4, permissive-autoconf-m4-no-warranty, permissive-configure, permissive-fsf, permissive-makefile-in |
| `libp11-kit0` | 0.23.22-1 | BSD-3-Clause, ISC, ISC+IBM, permissive-like-automake-output, same-as-rest-of-p11kit | ISC+IBM, permissive-like-automake-output, same-as-rest-of-p11kit |
| `libpam-modules` | 1.4.0-9+deb11u1 | GPL-2.0-or-later |  |
| `libpam-modules-bin` | 1.4.0-9+deb11u1 | GPL-2.0-or-later |  |
| `libpam-runtime` | 1.4.0-9+deb11u1 | GPL-2.0-or-later |  |
| `libpam0g` | 1.4.0-9+deb11u1 | GPL-2.0-or-later |  |
| `libpcre2-8-0` | 10.36-2+deb11u1 | (not recorded) |  |
| `libpcre3` | 2:8.39-13 | (not recorded) |  |
| `libsasl2-2` | 2.1.27+dfsg-2.1+deb11u1 | BSD-4-Clause, GPL-3.0-only, GPL-3.0-or-later | BSD-4-Clause |
| `libsasl2-modules-db` | 2.1.27+dfsg-2.1+deb11u1 | BSD-4-Clause, GPL-3.0-only, GPL-3.0-or-later | BSD-4-Clause |
| `libseccomp2` | 2.5.1-1+deb11u1 | LGPL-2.1-only |  |
| `libselinux1` | 3.1-3 | GPL-2.0-only, LGPL-2.1-only |  |
| `libsemanage-common` | 3.1-1 | GPL-2.0-or-later, LGPL-2.0-or-later |  |
| `libsemanage1` | 3.1-1+b2 | GPL-2.0-or-later, LGPL-2.0-or-later |  |
| `libsepol1` | 3.1-1 | GPL-2.0-or-later, LGPL-2.0-or-later |  |
| `libsmartcols1` | 2.36.1-8+deb11u2 | BSD-2-Clause, BSD-3-Clause, BSD-4-Clause, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.0-only, LGPL-2.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, public-domain | BSD-4-Clause, public-domain |
| `libss2` | 1.46.2-2+deb11u1 | (not recorded) |  |
| `libssl1.1` | 1.1.1w-0+deb11u1 | (not recorded) |  |
| `libstdc++6` | 10.2.1-6 | (not recorded) |  |
| `libsystemd0` | 247.3-7+deb11u6 | CC0-1.0, GPL-2 with Linux-syscall-note exception, GPL-2.0-only, GPL-2.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, MIT, public-domain | CC0-1.0, GPL-2 with Linux-syscall-note exception, public-domain |
| `libtasn1-6` | 4.16.0-2+deb11u1 | GFDL-1.3-only, GPL-3.0-only, LGPL-2.0-or-later, LGPL-2.1-only | GFDL-1.3-only |
| `libtinfo6` | 6.2+20201114-2+deb11u2 | BSD-3-Clause, MIT/X11, X11 | MIT/X11, X11 |
| `libtirpc-common` | 1.3.1-1+deb11u1 | BSD-2-Clause, BSD-3-Clause, BSD-4-Clause, GPL-2.0-only, LGPL-2.1-only, LGPL-2.1-or-later, PERMISSIVE, __AUTO_PERMISSIVE__ | BSD-4-Clause, PERMISSIVE, __AUTO_PERMISSIVE__ |
| `libtirpc3` | 1.3.1-1+deb11u1 | BSD-2-Clause, BSD-3-Clause, BSD-4-Clause, GPL-2.0-only, LGPL-2.1-only, LGPL-2.1-or-later, PERMISSIVE, __AUTO_PERMISSIVE__ | BSD-4-Clause, PERMISSIVE, __AUTO_PERMISSIVE__ |
| `libudev1` | 247.3-7+deb11u6 | CC0-1.0, GPL-2 with Linux-syscall-note exception, GPL-2.0-only, GPL-2.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, MIT, public-domain | CC0-1.0, GPL-2 with Linux-syscall-note exception, public-domain |
| `libunistring2` | 0.9.10-4 | FreeSoftware, GFDL-1.2-only, GFDL-1.2-or-later, GPL-2+ with distribution exception, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, GPL-3.0-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT | FreeSoftware, GFDL-1.2-only, GFDL-1.2-or-later, GPL-2+ with distribution exception |
| `libuuid1` | 2.36.1-8+deb11u2 | BSD-2-Clause, BSD-3-Clause, BSD-4-Clause, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.0-only, LGPL-2.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, public-domain | BSD-4-Clause, public-domain |
| `libxxhash0` | 0.8.0-2 | BSD-2-Clause, GPL-2.0-only |  |
| `libyaml-0-2` | 0.2.2-1 | MIT, permissive | permissive |
| `libyaml-dev` | 0.2.2-1 | MIT, permissive | permissive |
| `libzstd1` | 1.4.8+dfsg-2.1 | BSD-3-Clause, GPL-2.0-only, MIT, Zlib |  |
| `login` | 1:4.8.1-1 | GPL-2.0-only |  |
| `logsave` | 1.46.2-2+deb11u1 | GPL-2.0-only, LGPL-2.0-only |  |
| `lsb-base` | 11.1.0 | BSD-3-Clause, GPL-2.0-only |  |
| `manpages` | 5.10-1 | BSD-3-Clause, BSD-4-Clause, GPL-2.0-only, GPL-2.0-or-later, LDPv1, MIT, freely-redistributable, henry-spencer-regex, public-domain, verbatim | BSD-4-Clause, LDPv1, freely-redistributable, henry-spencer-regex, public-domain, verbatim |
| `mawk` | 1.3.4.20200120-2 | GPL-2.0-only |  |
| `mount` | 2.36.1-8+deb11u2 | BSD-2-Clause, BSD-3-Clause, BSD-4-Clause, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.0-only, LGPL-2.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, public-domain | BSD-4-Clause, public-domain |
| `ncurses-base` | 6.2+20201114-2+deb11u2 | BSD-3-Clause, MIT/X11, X11 | MIT/X11, X11 |
| `ncurses-bin` | 6.2+20201114-2+deb11u2 | BSD-3-Clause, MIT/X11, X11 | MIT/X11, X11 |
| `openssl` | 1.1.1w-0+deb11u1 | (not recorded) |  |
| `passwd` | 1:4.8.1-1 | GPL-2.0-only |  |
| `perl-base` | 5.32.1-4+deb11u3 | Artistic-2, Artistic-2.0, Artistic-dist, BSD-3-Clause, BSD-3-clause-GENERIC, BSD-3-clause-with-weird-numbering, BSD-4-clause-POWERDOG, BZIP, CC0-1.0, DONT-CHANGE-THE-GPL, GPL-1.0-only, GPL-1.0-or-later, GPL-2.0-only, GPL-2.0-or-later, GPL-2.0-with-bison-exception+, HSIEH-BSD, HSIEH-DERIVATIVE, LGPL-2.1-only, MIT, REGCOMP, RRA-KEEP-THIS-NOTICE, SDBM-PUBLIC-DOMAIN, TEXT-TABS, Unicode, Zlib | Artistic-2, Artistic-dist, BSD-3-clause-GENERIC, BSD-3-clause-with-weird-numbering, BSD-4-clause-POWERDOG, BZIP, CC0-1.0, DONT-CHANGE-THE-GPL, GPL-1.0-only, GPL-1.0-or-later, GPL-2.0-with-bison-exception+, HSIEH-BSD, HSIEH-DERIVATIVE, REGCOMP, RRA-KEEP-THIS-NOTICE, SDBM-PUBLIC-DOMAIN, TEXT-TABS, Unicode |
| `sed` | 4.7-1 | GPL-3.0-only |  |
| `sysvinit-utils` | 2.96-7+deb11u1 | GPL-2.0-only, GPL-2.0-or-later |  |
| `tar` | 1.34+dfsg-1+deb11u1 | GPL-2.0-only, GPL-3.0-only |  |
| `tzdata` | 2024a-0+deb11u1 | (not recorded) |  |
| `util-linux` | 2.36.1-8+deb11u2 | BSD-2-Clause, BSD-3-Clause, BSD-4-Clause, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.0-only, LGPL-2.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, public-domain | BSD-4-Clause, public-domain |
| `zlib1g` | 1:1.2.11.dfsg-2+deb11u2 | Zlib |  |

## chrislusf/seaweedfs:4.51

| Gói | Phiên bản | Giấy phép | Ngoài OSI |
|---|---|---|---|
| `alpine-baselayout` | 3.7.2-r1 | GPL-2.0-only |  |
| `alpine-baselayout-data` | 3.7.2-r1 | GPL-2.0-only |  |
| `alpine-keys` | 2.6-r0 | MIT |  |
| `alpine-release` | 3.24.3-r0 | MIT |  |
| `apk-tools` | 3.0.8-r0 | GPL-2.0-only |  |
| `brotli-libs` | 1.2.1-r0 | MIT |  |
| `busybox` | 1.37.0-r31 | GPL-2.0-only |  |
| `busybox-binsh` | 1.37.0-r31 | GPL-2.0-only |  |
| `c-ares` | 1.34.9-r0 | MIT |  |
| `ca-certificates-bundle` | 20261020-r0 | MIT, MPL-2.0 |  |
| `cel.dev/expr` | v0.25.3 | Apache-2.0 |  |
| `cloud.google.com/go` | v0.123.0 | Apache-2.0, BSD-3-Clause |  |
| `cloud.google.com/go/auth` | v0.23.2 | Apache-2.0 |  |
| `cloud.google.com/go/auth/oauth2adapt` | v0.2.8 | Apache-2.0 |  |
| `cloud.google.com/go/compute/metadata` | v0.9.0 | Apache-2.0 |  |
| `cloud.google.com/go/iam` | v1.13.0 | Apache-2.0 |  |
| `cloud.google.com/go/kms` | v1.35.0 | Apache-2.0 |  |
| `cloud.google.com/go/longrunning` | v1.2.0 | Apache-2.0 |  |
| `cloud.google.com/go/monitoring` | v1.30.0 | Apache-2.0 |  |
| `cloud.google.com/go/pubsub` | v1.51.1 | Apache-2.0 |  |
| `cloud.google.com/go/pubsub/v2` | v2.7.0 | Apache-2.0 |  |
| `cloud.google.com/go/storage` | v1.67.0 | Apache-2.0 |  |
| `curl` | 8.23.0-r0 | curl |  |
| `filippo.io/edwards25519` | v1.2.0 | BSD-3-Clause |  |
| `fuse` | 2.9.9-r7 | GPL-2.0-only, LGPL-2.1-only |  |
| `fuse-common` | 3.18.3-r0 | GPL-2.0-only, LGPL-2.1-only |  |
| `github.com/Azure/azure-sdk-for-go/sdk/azcore` | v1.23.2 | MIT |  |
| `github.com/Azure/azure-sdk-for-go/sdk/azidentity` | v1.14.1 | MIT |  |
| `github.com/Azure/azure-sdk-for-go/sdk/internal` | v1.12.0 | MIT |  |
| `github.com/Azure/azure-sdk-for-go/sdk/storage/azblob` | v1.8.2 | MIT |  |
| `github.com/Azure/azure-sdk-for-go/sdk/storage/internal` | v0.1.0 | MIT |  |
| `github.com/Azure/go-ntlmssp` | v0.1.1 | MIT |  |
| `github.com/AzureAD/microsoft-authentication-library-for-go` | v1.9.0 | MIT |  |
| `github.com/GoogleCloudPlatform/opentelemetry-operations-go/detectors/gcp` | v1.37.0 | Apache-2.0 |  |
| `github.com/GoogleCloudPlatform/opentelemetry-operations-go/exporter/metric` | v0.61.0 | Apache-2.0 |  |
| `github.com/GoogleCloudPlatform/opentelemetry-operations-go/internal/resourcemapping` | v0.61.0 | Apache-2.0 |  |
| `github.com/Jille/raft-grpc-transport` | v1.6.1 | BSD-2-Clause |  |
| `github.com/RoaringBitmap/roaring/v2` | v2.26.0 | Apache-2.0, BSD-3-Clause |  |
| `github.com/Shopify/sarama` | v1.38.1 | MIT |  |
| `github.com/ThreeDotsLabs/watermill` | v1.5.2 | MIT |  |
| `github.com/a-h/templ` | v0.3.1070 | MIT, BSD-3-Clause |  |
| `github.com/andybalholm/brotli` | v1.2.6 | MIT, BSD-3-Clause |  |
| `github.com/antlr4-go/antlr/v4` | v4.13.1 | BSD-3-Clause |  |
| `github.com/apache/arrow-go/v18` | v18.8.0 | Apache-2.0, BSD-3-Clause |  |
| `github.com/apache/cassandra-gocql-driver/v2` | v2.1.2 | Apache-2.0 |  |
| `github.com/apache/iceberg-go` | v0.7.0 | Apache-2.0 |  |
| `github.com/apache/thrift` | v0.24.0 | Apache-2.0, BSD-3-Clause |  |
| `github.com/arangodb/go-driver` | v1.6.9 | Apache-2.0 |  |
| `github.com/arangodb/go-velocypack` | v0.0.0-20200318135517-5af53c29c67e | Apache-2.0 |  |
| `github.com/armon/go-metrics` | v0.4.1 | MIT |  |
| `github.com/aws/aws-sdk-go` | v1.55.8 | Apache-2.0, BSD-3-Clause |  |
| `github.com/beorn7/perks` | v1.0.1 | MIT |  |
| `github.com/bits-and-blooms/bitset` | v1.24.4 | BSD-3-Clause |  |
| `github.com/boltdb/bolt` | v1.3.1 | MIT |  |
| `github.com/bwmarrin/snowflake` | v0.3.0 | BSD-2-Clause |  |
| `github.com/cenkalti/backoff/v4` | v4.3.0 | MIT |  |
| `github.com/cenkalti/backoff/v5` | v5.0.3 | MIT |  |
| `github.com/cespare/xxhash/v2` | v2.3.0 | MIT |  |
| `github.com/clipperhouse/uax29/v2` | v2.7.0 | MIT |  |
| `github.com/cncf/xds/go` | v0.0.0-20260202195803-dba9d589def2 | Apache-2.0 |  |
| `github.com/cockroachdb/apd/v3` | v3.2.1 | Apache-2.0 |  |
| `github.com/cognusion/imaging` | v1.0.4 | MIT |  |
| `github.com/coreos/go-semver` | v0.3.1 | Apache-2.0 |  |
| `github.com/coreos/go-systemd/v22` | v22.7.0 | Apache-2.0 |  |
| `github.com/creasty/defaults` | v1.8.0 | MIT |  |
| `github.com/davecgh/go-spew` | v1.1.2-0.20180830191138-d8f796af33cc | ISC |  |
| `github.com/dustin/go-humanize` | v1.1.0 | MIT |  |
| `github.com/eapache/go-resiliency` | v1.6.0 | MIT |  |
| `github.com/eapache/go-xerial-snappy` | v0.0.0-20230731223053-c322873962e3 | MIT |  |
| `github.com/eapache/queue` | v1.1.0 | MIT |  |
| `github.com/envoyproxy/go-control-plane/envoy` | v1.39.1-0.20260819172001-e6e3fd93e4be | Apache-2.0 |  |
| `github.com/envoyproxy/protoc-gen-validate` | v1.3.3 | Apache-2.0 |  |
| `github.com/facebookgo/clock` | v0.0.0-20150410010913-600d898af40a | MIT |  |
| `github.com/facebookgo/stats` | v0.0.0-20151006221625-1b76add642e4 | BSD-3-Clause |  |
| `github.com/fatih/color` | v1.19.0 | MIT |  |
| `github.com/felixge/httpsnoop` | v1.1.0 | MIT |  |
| `github.com/fluent/fluent-logger-golang` | v1.10.1 | Apache-2.0 |  |
| `github.com/fsnotify/fsnotify` | v1.10.1 | BSD-3-Clause |  |
| `github.com/geoarrow/geoarrow-go` | v0.0.0-20260403143023-f54751c3e3a1 | (not recorded) |  |
| `github.com/getsentry/sentry-go` | v0.49.0 | MIT |  |
| `github.com/go-asn1-ber/asn1-ber` | v1.5.8 | MIT |  |
| `github.com/go-jose/go-jose/v4` | v4.1.5 | Apache-2.0, BSD-3-Clause |  |
| `github.com/go-ldap/ldap/v3` | v3.4.14 | MIT |  |
| `github.com/go-logr/logr` | v1.4.4 | Apache-2.0 |  |
| `github.com/go-logr/stdr` | v1.2.2 | Apache-2.0 |  |
| `github.com/go-redsync/redsync/v4` | v4.18.0 | BSD-3-Clause |  |
| `github.com/go-sql-driver/mysql` | v1.10.1 | MPL-2.0 |  |
| `github.com/go-viper/mapstructure/v2` | v2.5.0 | MIT |  |
| `github.com/go-zookeeper/zk` | v1.0.4 | BSD-3-Clause |  |
| `github.com/goccy/go-json` | v0.10.6 | MIT |  |
| `github.com/goccy/go-yaml` | v1.18.0 | MIT |  |
| `github.com/golang-jwt/jwt/v5` | v5.3.1 | MIT |  |
| `github.com/golang/protobuf` | v1.5.4 | BSD-3-Clause |  |
| `github.com/golang/snappy` | v1.0.0 | BSD-3-Clause |  |
| `github.com/google/btree` | v1.1.3 | Apache-2.0 |  |
| `github.com/google/flatbuffers/go` | v0.0.0-20230108230133-3b8644d32c50 | (not recorded) |  |
| `github.com/google/s2a-go` | v0.1.9 | Apache-2.0 |  |
| `github.com/google/uuid` | v1.6.0 | BSD-3-Clause |  |
| `github.com/googleapis/enterprise-certificate-proxy` | v0.3.21 | Apache-2.0 |  |
| `github.com/googleapis/gax-go/v2` | v2.24.1 | BSD-3-Clause |  |
| `github.com/gorilla/mux` | v1.8.1 | BSD-3-Clause |  |
| `github.com/gorilla/securecookie` | v1.1.2 | BSD-3-Clause |  |
| `github.com/gorilla/sessions` | v1.4.0 | BSD-3-Clause |  |
| `github.com/grpc-ecosystem/grpc-gateway/v2` | v2.30.0 | BSD-3-Clause |  |
| `github.com/hashicorp/errwrap` | v1.1.0 | MPL-2.0 |  |
| `github.com/hashicorp/go-cleanhttp` | v0.5.2 | MPL-2.0 |  |
| `github.com/hashicorp/go-hclog` | v1.6.3 | MIT |  |
| `github.com/hashicorp/go-immutable-radix` | v1.3.1 | MPL-2.0 |  |
| `github.com/hashicorp/go-metrics` | v0.7.0 | MIT |  |
| `github.com/hashicorp/go-msgpack/v2` | v2.1.5 | MIT |  |
| `github.com/hashicorp/go-multierror` | v1.1.1 | MPL-2.0 |  |
| `github.com/hashicorp/go-retryablehttp` | v0.7.8 | MPL-2.0 |  |
| `github.com/hashicorp/go-rootcerts` | v1.0.2 | MPL-2.0 |  |
| `github.com/hashicorp/go-secure-stdlib/parseutil` | v0.2.0 | MPL-2.0 |  |
| `github.com/hashicorp/go-secure-stdlib/strutil` | v0.1.2 | MPL-2.0 |  |
| `github.com/hashicorp/go-sockaddr` | v1.0.7 | MPL-2.0 |  |
| `github.com/hashicorp/go-uuid` | v1.0.3 | MPL-2.0 |  |
| `github.com/hashicorp/golang-lru` | v1.0.2 | MPL-2.0 |  |
| `github.com/hashicorp/golang-lru/v2` | v2.0.7 | MPL-2.0 |  |
| `github.com/hashicorp/hcl` | v1.0.1-vault-7 | MPL-2.0 |  |
| `github.com/hashicorp/raft` | v1.8.0 | MPL-2.0 |  |
| `github.com/hashicorp/raft-boltdb/v2` | v2.4.2 | MPL-2.0 |  |
| `github.com/hashicorp/vault/api` | v1.23.0 | MPL-2.0 |  |
| `github.com/jackc/pgpassfile` | v1.0.0 | MIT |  |
| `github.com/jackc/pgservicefile` | v0.0.0-20240606120523-5a60cdf6a761 | MIT |  |
| `github.com/jackc/pgx/v5` | v5.10.0 | MIT |  |
| `github.com/jackc/puddle/v2` | v2.2.2 | MIT |  |
| `github.com/jcmturner/aescts/v2` | v2.0.0 | Apache-2.0 |  |
| `github.com/jcmturner/dnsutils/v2` | v2.0.0 | Apache-2.0 |  |
| `github.com/jcmturner/gofork` | v1.7.6 | BSD-3-Clause |  |
| `github.com/jcmturner/gokrb5/v8` | v8.4.4 | Apache-2.0 |  |
| `github.com/jcmturner/rpc/v2` | v2.0.3 | Apache-2.0 |  |
| `github.com/jhump/protoreflect` | v1.18.1 | Apache-2.0 |  |
| `github.com/jhump/protoreflect/v2` | v2.0.0-beta.1 | Apache-2.0 |  |
| `github.com/jmespath/go-jmespath` | v0.4.0 | Apache-2.0 |  |
| `github.com/karlseguin/ccache/v2` | v2.0.8 | MIT |  |
| `github.com/klauspost/compress` | v1.20.3 | BSD-3-Clause, Apache-2.0, MIT |  |
| `github.com/klauspost/cpuid/v2` | v2.4.0 | MIT |  |
| `github.com/klauspost/reedsolomon` | v1.14.2 | MIT |  |
| `github.com/kr/fs` | v0.1.0 | BSD-3-Clause |  |
| `github.com/kurin/blazer` | v0.5.3 | Apache-2.0 |  |
| `github.com/kylelemons/godebug` | v1.1.0 | Apache-2.0 |  |
| `github.com/linkedin/goavro/v2` | v2.15.0 | Apache-2.0 |  |
| `github.com/lithammer/shortuuid/v3` | v3.0.7 | MIT |  |
| `github.com/mattn/go-colorable` | v0.1.15 | MIT |  |
| `github.com/mattn/go-isatty` | v0.0.24 | MIT |  |
| `github.com/mattn/go-runewidth` | v0.0.24 | MIT |  |
| `github.com/minio/crc64nvme` | v1.1.1 | Apache-2.0 |  |
| `github.com/mitchellh/colorstring` | v0.0.0-20190213212951-d06e56a500db | MIT |  |
| `github.com/mitchellh/mapstructure` | v1.5.1-0.20220423185008-bf980b35cac4 | MIT |  |
| `github.com/montanaflynn/stats` | v0.7.1 | MIT |  |
| `github.com/munnerz/goautoneg` | v0.0.0-20191010083416-a7dc8b61c822 | BSD-3-Clause |  |
| `github.com/oklog/ulid` | v1.3.1 | Apache-2.0 |  |
| `github.com/orcaman/concurrent-map/v2` | v2.0.1 | MIT |  |
| `github.com/parquet-go/bitpack` | v1.0.0 | Apache-2.0 |  |
| `github.com/parquet-go/jsonlite` | v1.0.0 | MIT |  |
| `github.com/parquet-go/parquet-go` | v0.32.0 | Apache-2.0, MIT |  |
| `github.com/pelletier/go-toml/v2` | v2.4.3 | MIT |  |
| `github.com/peterh/liner` | v1.2.2 | MIT |  |
| `github.com/philhofer/fwd` | v1.2.0 | MIT |  |
| `github.com/pierrec/lz4/v4` | v4.1.29 | BSD-3-Clause |  |
| `github.com/pkg/browser` | v0.0.0-20240102092130-5ac0b6a4141c | BSD-2-Clause |  |
| `github.com/pkg/errors` | v0.9.1 | BSD-2-Clause |  |
| `github.com/pkg/sftp` | v1.13.11 | BSD-2-Clause |  |
| `github.com/planetscale/vtprotobuf` | v0.6.1-0.20240319094008-0393e58bdf10 | BSD-3-Clause, MIT |  |
| `github.com/posener/complete` | v1.2.3 | MIT |  |
| `github.com/pquerna/cachecontrol` | v0.2.0 | Apache-2.0 |  |
| `github.com/prometheus/client_golang` | v1.24.1 | Apache-2.0, BSD-3-Clause |  |
| `github.com/prometheus/client_model` | v0.6.3 | Apache-2.0 |  |
| `github.com/prometheus/common` | v0.71.0 | Apache-2.0 |  |
| `github.com/prometheus/procfs` | v0.22.0 | Apache-2.0 |  |
| `github.com/rcrowley/go-metrics` | v0.0.0-20201227073835-cf1acfcdf475 | BSD-2-Clause-Views | BSD-2-Clause-Views |
| `github.com/rdleal/intervalst` | v1.5.0 | MIT |  |
| `github.com/redis/go-redis/v9` | v9.22.0 | BSD-2-Clause |  |
| `github.com/rivo/uniseg` | v0.4.7 | MIT |  |
| `github.com/ryanuber/go-glob` | v1.0.0 | MIT |  |
| `github.com/sagikazarmark/locafero` | v0.11.0 | MIT |  |
| `github.com/schollz/progressbar/v3` | v3.19.1 | MIT |  |
| `github.com/seaweedfs/go-fuse/v2` | v2.9.4 | BSD-3-Clause |  |
| `github.com/seaweedfs/goexif` | v1.0.3 | BSD-2-Clause |  |
| `github.com/seaweedfs/raft` | v1.2.2 | MIT |  |
| `github.com/seaweedfs/seaweedfs` | v0.0.0-20261026040937-ffa87c25fdf5 | Apache-2.0 |  |
| `github.com/shirou/gopsutil/v4` | v4.26.7 | BSD-3-Clause |  |
| `github.com/sirupsen/logrus` | v1.9.4 | MIT |  |
| `github.com/sony/gobreaker` | v1.0.0 | MIT |  |
| `github.com/sourcegraph/conc` | v0.3.1-0.20240121214520-5f936abd7ae8 | MIT |  |
| `github.com/spf13/afero` | v1.15.0 | Apache-2.0 |  |
| `github.com/spf13/cast` | v1.10.0 | MIT |  |
| `github.com/spf13/pflag` | v1.0.10 | BSD-3-Clause |  |
| `github.com/spf13/viper` | v1.21.0 | MIT |  |
| `github.com/spiffe/go-spiffe/v2` | v2.8.1 | Apache-2.0 |  |
| `github.com/stretchr/objx` | v0.5.3 | MIT |  |
| `github.com/stretchr/testify` | v1.12.1 | MIT, BSD-3-Clause, ISC |  |
| `github.com/subosito/gotenv` | v1.6.0 | MIT |  |
| `github.com/substrait-io/substrait` | v0.87.0 | Apache-2.0 |  |
| `github.com/substrait-io/substrait-go/v8` | v8.1.1 | Apache-2.0 |  |
| `github.com/substrait-io/substrait-protobuf/go` | v0.85.0 | Apache-2.0 |  |
| `github.com/syndtr/goleveldb` | v1.0.1-0.20190318030020-c3a204f8e965 | BSD-2-Clause |  |
| `github.com/tidwall/gjson` | v1.20.0 | MIT |  |
| `github.com/tidwall/match` | v1.2.0 | MIT |  |
| `github.com/tidwall/pretty` | v1.2.0 | MIT |  |
| `github.com/tinylib/msgp` | v1.6.4 | MIT |  |
| `github.com/tklauser/go-sysconf` | v0.4.0 | BSD-3-Clause |  |
| `github.com/tklauser/numcpus` | v0.12.0 | Apache-2.0 |  |
| `github.com/tsuna/gohbase` | v0.0.0-20201125011725-348991136365 | Apache-2.0 |  |
| `github.com/twmb/avro` | v1.9.0 | MIT |  |
| `github.com/twmb/murmur3` | v1.1.8 | BSD-3-Clause |  |
| `github.com/twpayne/go-geom` | v1.6.1 | BSD-2-Clause |  |
| `github.com/tylertreat/BoomFilters` | v0.0.0-20210315201527-1a82519a3e43 | Apache-2.0 |  |
| `github.com/valyala/bytebufferpool` | v1.0.0 | MIT |  |
| `github.com/viant/ptrie` | v1.0.1 | Apache-2.0 |  |
| `github.com/xdg-go/scram` | v1.2.0 | Apache-2.0 |  |
| `github.com/xdg-go/stringprep` | v1.0.4 | Apache-2.0 |  |
| `github.com/xeipuuv/gojsonpointer` | v0.0.0-20190905194746-02993c407bfb | Apache-2.0 |  |
| `github.com/xeipuuv/gojsonreference` | v0.0.0-20180127040603-bd5ef7bd5415 | Apache-2.0 |  |
| `github.com/xeipuuv/gojsonschema` | v1.2.0 | Apache-2.0 |  |
| `github.com/youmark/pkcs8` | v0.0.0-20240726163527-a2c0da244d78 | MIT |  |
| `github.com/zeebo/xxh3` | v1.1.0 | BSD-2-Clause |  |
| `go.etcd.io/bbolt` | v1.5.0 | MIT |  |
| `go.etcd.io/etcd/api/v3` | v3.7.1 | Apache-2.0 |  |
| `go.etcd.io/etcd/client/pkg/v3` | v3.7.1 | Apache-2.0 |  |
| `go.etcd.io/etcd/client/v3` | v3.7.1 | Apache-2.0 |  |
| `go.mongodb.org/mongo-driver` | v1.17.9 | Apache-2.0 |  |
| `go.opencensus.io` | v0.24.0 | Apache-2.0 |  |
| `go.opentelemetry.io/auto/sdk` | v1.2.1 | Apache-2.0 |  |
| `go.opentelemetry.io/contrib/detectors/gcp` | v1.46.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/contrib/instrumentation/google.golang.org/grpc/otelgrpc` | v0.71.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp` | v0.71.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/otel` | v1.46.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/otel/metric` | v1.46.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/otel/sdk` | v1.46.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/otel/sdk/metric` | v1.46.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/otel/trace` | v1.46.0 | Apache-2.0, BSD-3-Clause |  |
| `go.uber.org/atomic` | v1.12.0 | MIT |  |
| `go.uber.org/multierr` | v1.11.0 | MIT |  |
| `go.uber.org/zap` | v1.28.0 | MIT |  |
| `go.yaml.in/yaml/v3` | v3.0.5 | MIT, Apache-2.0 |  |
| `golang.org/x/crypto` | v0.58.0 | BSD-3-Clause |  |
| `golang.org/x/exp` | v0.0.0-20260813180055-c1d0aacb2297 | BSD-3-Clause |  |
| `golang.org/x/image` | v0.46.0 | BSD-3-Clause |  |
| `golang.org/x/net` | v0.61.0 | BSD-3-Clause |  |
| `golang.org/x/oauth2` | v0.37.0 | BSD-3-Clause |  |
| `golang.org/x/sync` | v0.24.0 | BSD-3-Clause |  |
| `golang.org/x/sys` | v0.49.0 | BSD-3-Clause |  |
| `golang.org/x/term` | v0.47.0 | BSD-3-Clause |  |
| `golang.org/x/text` | v0.43.0 | BSD-3-Clause |  |
| `golang.org/x/time` | v0.16.0 | BSD-3-Clause |  |
| `google.golang.org/api` | v0.297.0 | BSD-3-Clause |  |
| `google.golang.org/genproto` | v0.0.0-20260904194346-d0f1323225a4 | Apache-2.0 |  |
| `google.golang.org/genproto/googleapis/api` | v0.0.0-20260904194346-d0f1323225a4 | Apache-2.0 |  |
| `google.golang.org/genproto/googleapis/rpc` | v0.0.0-20260904194346-d0f1323225a4 | Apache-2.0 |  |
| `google.golang.org/grpc` | v1.85.0-dev.0.20260915183914-4e49413dcab7 | Apache-2.0 |  |
| `google.golang.org/grpc/security/advancedtls` | v1.0.0 | Apache-2.0 |  |
| `google.golang.org/protobuf` | v1.36.12 | BSD-3-Clause |  |
| `gopkg.in/inf.v0` | v0.9.1 | BSD-3-Clause |  |
| `gopkg.in/yaml.v3` | v3.0.1 | MIT, Apache-2.0 |  |
| `libapk` | 3.0.8-r0 | GPL-2.0-only |  |
| `libcrypto3` | 3.5.9-r0 | Apache-2.0 |  |
| `libcurl` | 8.23.0-r0 | curl |  |
| `libgcc` | 15.2.0-r5 | GPL-2.0-or-later, LGPL-2.1-or-later |  |
| `libidn2` | 2.3.8-r0 | GPL-2.0-or-later, LGPL-3.0-or-later |  |
| `libpsl` | 0.21.5-r3 | MIT |  |
| `libssl3` | 3.5.9-r0 | Apache-2.0 |  |
| `libunistring` | 1.4.2-r0 | GPL-2.0-or-later, LGPL-3.0-or-later |  |
| `modernc.org/b` | v1.0.0 | BSD-3-Clause |  |
| `musl` | 1.2.6-r3 | MIT |  |
| `musl-utils` | 1.2.6-r3 | BSD-2-Clause, GPL-2.0-or-later, MIT |  |
| `nghttp2-libs` | 1.70.0-r0 | MIT |  |
| `scanelf` | 1.3.9-r1 | GPL-2.0-only |  |
| `ssl_client` | 1.37.0-r31 | GPL-2.0-only |  |
| `stdlib` | v1.26.10 | (not recorded) |  |
| `su-exec` | 0.3-r0 | MIT |  |
| `zlib` | 1.3.2-r1 | Zlib |  |
| `zstd-libs` | 1.5.7-r2 | BSD-3-Clause, GPL-2.0-or-later |  |

## clamav/clamav:1.5.4

| Gói | Phiên bản | Giấy phép | Ngoài OSI |
|---|---|---|---|
| `alpine-baselayout` | 3.7.2-r1 | GPL-2.0-only |  |
| `alpine-baselayout-data` | 3.7.2-r1 | GPL-2.0-only |  |
| `alpine-keys` | 2.6-r0 | MIT |  |
| `alpine-release` | 3.24.2-r0 | MIT |  |
| `apk-tools` | 3.0.8-r0 | GPL-2.0-only |  |
| `brotli-libs` | 1.2.0-r1 | MIT |  |
| `busybox` | 1.37.0-r31 | GPL-2.0-only |  |
| `busybox-binsh` | 1.37.0-r31 | GPL-2.0-only |  |
| `c-ares` | 1.34.8-r0 | MIT |  |
| `ca-certificates-bundle` | 20260909-r0 | MIT, MPL-2.0 |  |
| `json-c` | 0.18-r1 | MIT |  |
| `libapk` | 3.0.8-r0 | GPL-2.0-only |  |
| `libbz2` | 1.0.8-r6 | bzip-2-1.0.6 | bzip-2-1.0.6 |
| `libcrypto3` | 3.5.9-r0 | Apache-2.0 |  |
| `libcurl` | 8.22.0-r0 | curl |  |
| `libformw` | 6.6_p20260516-r0 | X-11 | X-11 |
| `libgcc` | 15.2.0-r5 | GPL-2.0-or-later, LGPL-2.1-or-later |  |
| `libidn2` | 2.3.8-r0 | GPL-2.0-or-later, LGPL-3.0-or-later |  |
| `libmenuw` | 6.6_p20260516-r0 | X-11 | X-11 |
| `libmilter` | 1.0.2-r11 | Sendmail | Sendmail |
| `libncursesw` | 6.6_p20260516-r0 | X-11 | X-11 |
| `libpanelw` | 6.6_p20260516-r0 | X-11 | X-11 |
| `libpsl` | 0.21.5-r3 | MIT |  |
| `libssl3` | 3.5.9-r0 | Apache-2.0 |  |
| `libstdc++` | 15.2.0-r5 | GPL-2.0-or-later, LGPL-2.1-or-later |  |
| `libunistring` | 1.4.2-r0 | GPL-2.0-or-later, LGPL-3.0-or-later |  |
| `libxml2` | 2.13.9-r2 | MIT |  |
| `musl` | 1.2.6-r2 | MIT |  |
| `musl-fts` | 1.2.7-r7 | BSD-3-Clause |  |
| `musl-utils` | 1.2.6-r2 | BSD-2-Clause, GPL-2.0-or-later, MIT |  |
| `ncurses-libs` | 6.6_p20260516-r0 | X-11 | X-11 |
| `ncurses-terminfo-base` | 6.6_p20260516-r0 | X-11 | X-11 |
| `nghttp2-libs` | 1.70.0-r0 | MIT |  |
| `pcre2` | 10.49-r0 | BSD-3-Clause |  |
| `scanelf` | 1.3.9-r1 | GPL-2.0-only |  |
| `ssl_client` | 1.37.0-r31 | GPL-2.0-only |  |
| `tini` | 0.19.0-r3 | MIT |  |
| `tzdata` | 2026e-r0 | Public-Domain | Public-Domain |
| `xz-libs` | 5.8.4-r0 | 0BSD, GPL-2.0-or-later, LGPL-2.1-or-later, Public-Domain | Public-Domain |
| `zlib` | 1.3.2-r1 | Zlib |  |
| `zstd-libs` | 1.5.7-r2 | BSD-3-Clause, GPL-2.0-or-later |  |

## duckdb/duckdb:1.4.1

| Gói | Phiên bản | Giấy phép | Ngoài OSI |
|---|---|---|---|
| `base-files` | 12.4+deb12u12 | GPL-2.0-or-later |  |
| `gcc-12-base` | 12.2.0-14+deb12u1 | Artistic-2.0, GFDL-1.2-only, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, LGPL-2.0-or-later | GFDL-1.2-only |
| `libc6` | 2.36-9+deb12u13 | GPL-2.0-only, LGPL-2.1-only |  |
| `libgcc-s1` | 12.2.0-14+deb12u1 | (not recorded) |  |
| `libgomp1` | 12.2.0-14+deb12u1 | (not recorded) |  |
| `libssl3` | 3.0.17-1~deb12u3 | Apache-2.0, Artistic-2.0, GPL-1.0-only, GPL-1.0-or-later | GPL-1.0-only, GPL-1.0-or-later |
| `libstdc++6` | 12.2.0-14+deb12u1 | (not recorded) |  |
| `media-types` | 10.0.0 | ad-hoc | ad-hoc |
| `netbase` | 6.4 | GPL-2.0-only |  |
| `tzdata` | 2025b-0+deb12u2 | public-domain | public-domain |

## eclipse-mosquitto:2.0.23

| Gói | Phiên bản | Giấy phép | Ngoài OSI |
|---|---|---|---|
| `alpine-baselayout` | 3.7.2-r1 | GPL-2.0-only |  |
| `alpine-baselayout-data` | 3.7.2-r1 | GPL-2.0-only |  |
| `alpine-keys` | 2.6-r0 | MIT |  |
| `alpine-release` | 3.24.4-r0 | MIT |  |
| `apk-tools` | 3.0.9-r0 | GPL-2.0-only |  |
| `busybox` | 1.37.0-r31 | GPL-2.0-only |  |
| `busybox-binsh` | 1.37.0-r31 | GPL-2.0-only |  |
| `ca-certificates` | 20261020-r0 | MIT, MPL-2.0 |  |
| `ca-certificates-bundle` | 20261020-r0 | MIT, MPL-2.0 |  |
| `cjson` | 1.7.19-r1 | MIT |  |
| `libapk` | 3.0.9-r0 | GPL-2.0-only |  |
| `libcrypto3` | 3.5.9-r0 | Apache-2.0 |  |
| `libssl3` | 3.5.9-r0 | Apache-2.0 |  |
| `libwebsockets` | 4.3.5-r2 | MIT |  |
| `musl` | 1.2.6-r3 | MIT |  |
| `musl-utils` | 1.2.6-r3 | BSD-2-Clause, GPL-2.0-or-later, MIT |  |
| `scanelf` | 1.3.9-r1 | GPL-2.0-only |  |
| `ssl_client` | 1.37.0-r31 | GPL-2.0-only |  |
| `tzdata` | 2026f-r0 | Public-Domain | Public-Domain |
| `zlib` | 1.3.2-r1 | Zlib |  |

## eclipse-temurin:21-jdk-alpine

| Gói | Phiên bản | Giấy phép | Ngoài OSI |
|---|---|---|---|
| `acl-libs` | 2.3.2-r1 | GPL-2.0-or-later, LGPL-2.1-or-later |  |
| `alpine-baselayout` | 3.7.2-r1 | GPL-2.0-only |  |
| `alpine-baselayout-data` | 3.7.2-r1 | GPL-2.0-only |  |
| `alpine-keys` | 2.6-r0 | MIT |  |
| `alpine-release` | 3.24.4-r0 | MIT |  |
| `apk-tools` | 3.0.9-r0 | GPL-2.0-only |  |
| `binutils` | 2.45.1-r1 | BSD-3-Clause, GPL-2.0-or-later, LGPL-2.1-or-later |  |
| `brotli-libs` | 1.2.1-r0 | MIT |  |
| `busybox` | 1.37.0-r31 | GPL-2.0-only |  |
| `busybox-binsh` | 1.37.0-r31 | GPL-2.0-only |  |
| `ca-certificates` | 20261020-r0 | MIT, MPL-2.0 |  |
| `ca-certificates-bundle` | 20261020-r0 | MIT, MPL-2.0 |  |
| `coreutils` | 9.11-r0 | GPL-3.0-or-later |  |
| `coreutils-env` | 9.11-r0 | GPL-3.0-or-later |  |
| `coreutils-fmt` | 9.11-r0 | GPL-3.0-or-later |  |
| `coreutils-sha512sum` | 9.11-r0 | GPL-3.0-or-later |  |
| `encodings` | 1.1.0-r0 | Domain, Public | Domain, Public |
| `font-dejavu` | 2.37-r6 | Bitstream-Vera | Bitstream-Vera |
| `fontconfig` | 2.17.1-r1 | MIT |  |
| `freetype` | 2.14.3-r0 | FTL, GPL-2.0-or-later | FTL |
| `gdbm` | 1.26-r0 | GPL-3.0-or-later |  |
| `gmp` | 6.3.0-r4 | GPL-2.0-or-later, LGPL-3.0-or-later |  |
| `gnupg` | 2.4.9-r1 | GPL-3.0-or-later |  |
| `gnupg-dirmngr` | 2.4.9-r1 | GPL-3.0-or-later |  |
| `gnupg-gpgconf` | 2.4.9-r1 | GPL-3.0-or-later |  |
| `gnupg-keyboxd` | 2.4.9-r1 | GPL-3.0-or-later |  |
| `gnupg-utils` | 2.4.9-r1 | GPL-3.0-or-later |  |
| `gnupg-wks-client` | 2.4.9-r1 | GPL-3.0-or-later |  |
| `gnutls` | 3.8.13-r0 | LGPL-2.1-or-later |  |
| `gpg` | 2.4.9-r1 | GPL-3.0-or-later |  |
| `gpg-agent` | 2.4.9-r1 | GPL-3.0-or-later |  |
| `gpg-wks-server` | 2.4.9-r1 | GPL-3.0-or-later |  |
| `gpgsm` | 2.4.9-r1 | GPL-3.0-or-later |  |
| `gpgv` | 2.4.9-r1 | GPL-3.0-or-later |  |
| `jansson` | 2.15.0-r0 | MIT |  |
| `libapk` | 3.0.9-r0 | GPL-2.0-only |  |
| `libassuan` | 3.0.2-r0 | LGPL-2.1-or-later |  |
| `libattr` | 2.5.2-r2 | LGPL-2.1-or-later |  |
| `libbz2` | 1.0.8-r6 | bzip-2-1.0.6 | bzip-2-1.0.6 |
| `libcrypto3` | 3.5.9-r0 | Apache-2.0 |  |
| `libexpat` | 2.10.0-r0 | MIT |  |
| `libffi` | 3.5.2-r1 | MIT |  |
| `libfontenc` | 1.1.9-r0 | MIT |  |
| `libgcc` | 15.2.0-r5 | GPL-2.0-or-later, LGPL-2.1-or-later |  |
| `libgcrypt` | 1.12.2-r0 | GPL-2.0-or-later, LGPL-2.1-or-later |  |
| `libgpg-error` | 1.61-r0 | GPL-2.0-or-later, LGPL-2.1-or-later |  |
| `libidn2` | 2.3.8-r0 | GPL-2.0-or-later, LGPL-3.0-or-later |  |
| `libintl` | 1.0-r0 | LGPL-2.1-or-later |  |
| `libksba` | 1.7.0-r0 | GPL-2.0-only, GPL-3.0-only, LGPL-3.0-only |  |
| `libldap` | 2.6.15-r0 | OLDAP-2.8 |  |
| `libncursesw` | 6.6_p20260516-r0 | X-11 | X-11 |
| `libpng` | 1.6.59-r0 | Libpng | Libpng |
| `libsasl` | 2.1.28-r9 | BSD-3-Clause-Attribution, BSD-4-Clause | BSD-3-Clause-Attribution, BSD-4-Clause |
| `libssl3` | 3.5.9-r0 | Apache-2.0 |  |
| `libstdc++` | 15.2.0-r5 | GPL-2.0-or-later, LGPL-2.1-or-later |  |
| `libtasn1` | 4.21.0-r0 | LGPL-2.1-or-later |  |
| `libunistring` | 1.4.2-r0 | GPL-2.0-or-later, LGPL-3.0-or-later |  |
| `mkfontscale` | 1.2.4-r0 | MIT |  |
| `musl` | 1.2.6-r3 | MIT |  |
| `musl-locales` | 0.1.0-r1 | LGPL-3.0-only |  |
| `musl-locales-lang` | 0.1.0-r1 | MIT |  |
| `musl-utils` | 1.2.6-r3 | BSD-2-Clause, GPL-2.0-or-later, MIT |  |
| `ncurses-terminfo-base` | 6.6_p20260516-r0 | X-11 | X-11 |
| `nettle` | 3.10.2-r0 | GPL-2.0-or-later, LGPL-3.0-or-later |  |
| `npth` | 1.8-r0 | LGPL-2.0-or-later |  |
| `openssl` | 3.5.9-r0 | Apache-2.0 |  |
| `p11-kit` | 0.26.2-r0 | BSD-3-Clause |  |
| `p11-kit-trust` | 0.26.2-r0 | BSD-3-Clause |  |
| `pinentry` | 1.3.2-r0 | GPL-2.0-or-later |  |
| `scanelf` | 1.3.9-r1 | GPL-2.0-only |  |
| `skalibs-libs` | 2.15.0.0-r0 | ISC |  |
| `sqlite-libs` | 3.53.4-r0 | blessing | blessing |
| `ssl_client` | 1.37.0-r31 | GPL-2.0-only |  |
| `tzdata` | 2026f-r0 | Public-Domain | Public-Domain |
| `utmps-libs` | 0.1.3.3-r0 | ISC |  |
| `zlib` | 1.3.2-r1 | Zlib |  |
| `zstd-libs` | 1.5.7-r2 | BSD-3-Clause, GPL-2.0-or-later |  |

## eclipse-temurin:21-jre-alpine

| Gói | Phiên bản | Giấy phép | Ngoài OSI |
|---|---|---|---|
| `acl-libs` | 2.3.2-r1 | GPL-2.0-or-later, LGPL-2.1-or-later |  |
| `alpine-baselayout` | 3.7.2-r1 | GPL-2.0-only |  |
| `alpine-baselayout-data` | 3.7.2-r1 | GPL-2.0-only |  |
| `alpine-keys` | 2.6-r0 | MIT |  |
| `alpine-release` | 3.24.4-r0 | MIT |  |
| `apk-tools` | 3.0.9-r0 | GPL-2.0-only |  |
| `brotli-libs` | 1.2.1-r0 | MIT |  |
| `busybox` | 1.37.0-r31 | GPL-2.0-only |  |
| `busybox-binsh` | 1.37.0-r31 | GPL-2.0-only |  |
| `ca-certificates` | 20261020-r0 | MIT, MPL-2.0 |  |
| `ca-certificates-bundle` | 20261020-r0 | MIT, MPL-2.0 |  |
| `coreutils` | 9.11-r0 | GPL-3.0-or-later |  |
| `coreutils-env` | 9.11-r0 | GPL-3.0-or-later |  |
| `coreutils-fmt` | 9.11-r0 | GPL-3.0-or-later |  |
| `coreutils-sha512sum` | 9.11-r0 | GPL-3.0-or-later |  |
| `encodings` | 1.1.0-r0 | Domain, Public | Domain, Public |
| `font-dejavu` | 2.37-r6 | Bitstream-Vera | Bitstream-Vera |
| `fontconfig` | 2.17.1-r1 | MIT |  |
| `freetype` | 2.14.3-r0 | FTL, GPL-2.0-or-later | FTL |
| `gdbm` | 1.26-r0 | GPL-3.0-or-later |  |
| `gmp` | 6.3.0-r4 | GPL-2.0-or-later, LGPL-3.0-or-later |  |
| `gnupg` | 2.4.9-r1 | GPL-3.0-or-later |  |
| `gnupg-dirmngr` | 2.4.9-r1 | GPL-3.0-or-later |  |
| `gnupg-gpgconf` | 2.4.9-r1 | GPL-3.0-or-later |  |
| `gnupg-keyboxd` | 2.4.9-r1 | GPL-3.0-or-later |  |
| `gnupg-utils` | 2.4.9-r1 | GPL-3.0-or-later |  |
| `gnupg-wks-client` | 2.4.9-r1 | GPL-3.0-or-later |  |
| `gnutls` | 3.8.13-r0 | LGPL-2.1-or-later |  |
| `gpg` | 2.4.9-r1 | GPL-3.0-or-later |  |
| `gpg-agent` | 2.4.9-r1 | GPL-3.0-or-later |  |
| `gpg-wks-server` | 2.4.9-r1 | GPL-3.0-or-later |  |
| `gpgsm` | 2.4.9-r1 | GPL-3.0-or-later |  |
| `gpgv` | 2.4.9-r1 | GPL-3.0-or-later |  |
| `libapk` | 3.0.9-r0 | GPL-2.0-only |  |
| `libassuan` | 3.0.2-r0 | LGPL-2.1-or-later |  |
| `libattr` | 2.5.2-r2 | LGPL-2.1-or-later |  |
| `libbz2` | 1.0.8-r6 | bzip-2-1.0.6 | bzip-2-1.0.6 |
| `libcrypto3` | 3.5.9-r0 | Apache-2.0 |  |
| `libexpat` | 2.10.0-r0 | MIT |  |
| `libffi` | 3.5.2-r1 | MIT |  |
| `libfontenc` | 1.1.9-r0 | MIT |  |
| `libgcrypt` | 1.12.2-r0 | GPL-2.0-or-later, LGPL-2.1-or-later |  |
| `libgpg-error` | 1.61-r0 | GPL-2.0-or-later, LGPL-2.1-or-later |  |
| `libidn2` | 2.3.8-r0 | GPL-2.0-or-later, LGPL-3.0-or-later |  |
| `libintl` | 1.0-r0 | LGPL-2.1-or-later |  |
| `libksba` | 1.7.0-r0 | GPL-2.0-only, GPL-3.0-only, LGPL-3.0-only |  |
| `libldap` | 2.6.15-r0 | OLDAP-2.8 |  |
| `libncursesw` | 6.6_p20260516-r0 | X-11 | X-11 |
| `libpng` | 1.6.59-r0 | Libpng | Libpng |
| `libsasl` | 2.1.28-r9 | BSD-3-Clause-Attribution, BSD-4-Clause | BSD-3-Clause-Attribution, BSD-4-Clause |
| `libssl3` | 3.5.9-r0 | Apache-2.0 |  |
| `libtasn1` | 4.21.0-r0 | LGPL-2.1-or-later |  |
| `libunistring` | 1.4.2-r0 | GPL-2.0-or-later, LGPL-3.0-or-later |  |
| `mkfontscale` | 1.2.4-r0 | MIT |  |
| `musl` | 1.2.6-r3 | MIT |  |
| `musl-locales` | 0.1.0-r1 | LGPL-3.0-only |  |
| `musl-locales-lang` | 0.1.0-r1 | MIT |  |
| `musl-utils` | 1.2.6-r3 | BSD-2-Clause, GPL-2.0-or-later, MIT |  |
| `ncurses-terminfo-base` | 6.6_p20260516-r0 | X-11 | X-11 |
| `nettle` | 3.10.2-r0 | GPL-2.0-or-later, LGPL-3.0-or-later |  |
| `npth` | 1.8-r0 | LGPL-2.0-or-later |  |
| `openssl` | 3.5.9-r0 | Apache-2.0 |  |
| `p11-kit` | 0.26.2-r0 | BSD-3-Clause |  |
| `p11-kit-trust` | 0.26.2-r0 | BSD-3-Clause |  |
| `pinentry` | 1.3.2-r0 | GPL-2.0-or-later |  |
| `scanelf` | 1.3.9-r1 | GPL-2.0-only |  |
| `skalibs-libs` | 2.15.0.0-r0 | ISC |  |
| `sqlite-libs` | 3.53.4-r0 | blessing | blessing |
| `ssl_client` | 1.37.0-r31 | GPL-2.0-only |  |
| `tzdata` | 2026f-r0 | Public-Domain | Public-Domain |
| `utmps-libs` | 0.1.3.3-r0 | ISC |  |
| `zlib` | 1.3.2-r1 | Zlib |  |
| `zstd-libs` | 1.5.7-r2 | BSD-3-Clause, GPL-2.0-or-later |  |

## eclipse-temurin:21-jre-jammy

| Gói | Phiên bản | Giấy phép | Ngoài OSI |
|---|---|---|---|
| `adduser` | 3.118ubuntu5 | GPL-2.0-only |  |
| `apt` | 2.4.15ubuntu0.1 | GPL-2.0-only, GPL-2.0-or-later |  |
| `base-files` | 12ubuntu4.7 | GPL-2.0-or-later |  |
| `base-passwd` | 3.5.52build1 | GPL-2.0-only, public-domain | public-domain |
| `bash` | 5.1-6ubuntu1.1 | GPL-3.0-only |  |
| `bsdutils` | 1:2.37.2-4ubuntu3.6 | BSD-2-Clause, BSD-3-Clause, BSD-4-Clause, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.0-only, LGPL-2.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, public-domain | BSD-4-Clause, public-domain |
| `ca-certificates` | 20260816ubuntu0.22.04.2 | GPL-2.0-only, GPL-2.0-or-later, MPL-2.0 |  |
| `coreutils` | 8.32-4.1ubuntu1.4 | GPL-3.0-only |  |
| `curl` | 7.81.0-1ubuntu1.30 | BSD-3-Clause, BSD-4-Clause, ISC, curl, other, public-domain | BSD-4-Clause, other, public-domain |
| `dash` | 0.5.11+git20210903+057cd650a4ed-3build1 | BSD-3-Clause, FSFUL, FSFULLR, GPL-2.0-only, GPL-2.0-or-later, MIT, public-domain | FSFUL, FSFULLR, public-domain |
| `debconf` | 1.5.79ubuntu1 | BSD-2-Clause |  |
| `debianutils` | 5.5-1ubuntu2 | GPL-2.0-only |  |
| `diffutils` | 1:3.8-0ubuntu2.1 | GFDL-1.3-or-later, GPL-2.0-or-later | GFDL-1.3-or-later |
| `dirmngr` | 2.2.27-3ubuntu2.5 | BSD-3-Clause, CC0-1.0, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, RFC-Reference, TinySCHEME, permissive | CC0-1.0, RFC-Reference, TinySCHEME, permissive |
| `dpkg` | 1.21.1ubuntu2.6 | BSD-2-Clause, GPL-2.0-only, GPL-2.0-or-later, public-domain-md5, public-domain-s-s-d | public-domain-md5, public-domain-s-s-d |
| `e2fsprogs` | 1.46.5-2ubuntu1.2 | GPL-2.0-only, LGPL-2.0-only |  |
| `findutils` | 4.8.0-1ubuntu3 | GFDL-1.3-only, GPL-3.0-only | GFDL-1.3-only |
| `fontconfig` | 2.13.1-4.2ubuntu5 | (not recorded) |  |
| `fontconfig-config` | 2.13.1-4.2ubuntu5 | (not recorded) |  |
| `fonts-dejavu-core` | 2.37-2build1 | GPL-2.0-only, GPL-2.0-or-later, bitstream-vera | bitstream-vera |
| `gcc-12-base` | 12.3.0-1ubuntu1~22.04.3 | Artistic-2.0, GFDL-1.2-only, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, LGPL-2.0-or-later | GFDL-1.2-only |
| `gnupg` | 2.2.27-3ubuntu2.5 | BSD-3-Clause, CC0-1.0, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, RFC-Reference, TinySCHEME, permissive | CC0-1.0, RFC-Reference, TinySCHEME, permissive |
| `gnupg-l10n` | 2.2.27-3ubuntu2.5 | BSD-3-Clause, CC0-1.0, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, RFC-Reference, TinySCHEME, permissive | CC0-1.0, RFC-Reference, TinySCHEME, permissive |
| `gnupg-utils` | 2.2.27-3ubuntu2.5 | BSD-3-Clause, CC0-1.0, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, RFC-Reference, TinySCHEME, permissive | CC0-1.0, RFC-Reference, TinySCHEME, permissive |
| `gpg` | 2.2.27-3ubuntu2.5 | BSD-3-Clause, CC0-1.0, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, RFC-Reference, TinySCHEME, permissive | CC0-1.0, RFC-Reference, TinySCHEME, permissive |
| `gpg-agent` | 2.2.27-3ubuntu2.5 | BSD-3-Clause, CC0-1.0, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, RFC-Reference, TinySCHEME, permissive | CC0-1.0, RFC-Reference, TinySCHEME, permissive |
| `gpg-wks-client` | 2.2.27-3ubuntu2.5 | BSD-3-Clause, CC0-1.0, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, RFC-Reference, TinySCHEME, permissive | CC0-1.0, RFC-Reference, TinySCHEME, permissive |
| `gpg-wks-server` | 2.2.27-3ubuntu2.5 | BSD-3-Clause, CC0-1.0, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, RFC-Reference, TinySCHEME, permissive | CC0-1.0, RFC-Reference, TinySCHEME, permissive |
| `gpgconf` | 2.2.27-3ubuntu2.5 | BSD-3-Clause, CC0-1.0, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, RFC-Reference, TinySCHEME, permissive | CC0-1.0, RFC-Reference, TinySCHEME, permissive |
| `gpgsm` | 2.2.27-3ubuntu2.5 | BSD-3-Clause, CC0-1.0, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, RFC-Reference, TinySCHEME, permissive | CC0-1.0, RFC-Reference, TinySCHEME, permissive |
| `gpgv` | 2.2.27-3ubuntu2.5 | BSD-3-Clause, CC0-1.0, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, RFC-Reference, TinySCHEME, permissive | CC0-1.0, RFC-Reference, TinySCHEME, permissive |
| `grep` | 3.7-1build1 | GPL-3.0-only, GPL-3.0-or-later |  |
| `gzip` | 1.10-4ubuntu4.2 | FSF-manpages, GFDL-1.3+-no-invariant, GFDL-3, GPL-3.0-only, GPL-3.0-or-later | FSF-manpages, GFDL-1.3+-no-invariant, GFDL-3 |
| `hostname` | 3.23ubuntu2 | GPL-2.0-only |  |
| `init-system-helpers` | 1.62 | BSD-3-Clause, GPL-2.0-only, GPL-2.0-or-later |  |
| `libacl1` | 2.3.1-1 | GPL-2.0-only, GPL-2.0-or-later, LGPL-2.0-or-later, LGPL-2.1-only |  |
| `libapt-pkg6.0` | 2.4.15ubuntu0.1 | GPL-2.0-only, GPL-2.0-or-later |  |
| `libassuan0` | 2.5.5-1build1 | GAP, GAP~FSF, GPL-2+ with libtool exception, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later | GAP, GAP~FSF, GPL-2+ with libtool exception |
| `libattr1` | 1:2.5.1-1ubuntu0.1 | GPL-2.0-only, GPL-2.0-or-later, LGPL-2.0-or-later, LGPL-2.1-only |  |
| `libaudit-common` | 1:3.0.7-1ubuntu0.1 | GPL-1.0-only, GPL-2.0-only, LGPL-2.1-only | GPL-1.0-only |
| `libaudit1` | 1:3.0.7-1ubuntu0.1 | GPL-1.0-only, GPL-2.0-only, LGPL-2.1-only | GPL-1.0-only |
| `libblkid1` | 2.37.2-4ubuntu3.6 | BSD-2-Clause, BSD-3-Clause, BSD-4-Clause, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.0-only, LGPL-2.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, public-domain | BSD-4-Clause, public-domain |
| `libbrotli1` | 1.0.9-2ubuntu0.1 | MIT |  |
| `libbz2-1.0` | 1.0.8-5ubuntu0.1 | BSD-3-Clause, GPL-2.0-only |  |
| `libc-bin` | 2.35-0ubuntu3.15 | GFDL-1.3-only, GPL-2.0-only, LGPL-2.1-only | GFDL-1.3-only |
| `libc6` | 2.35-0ubuntu3.15 | GFDL-1.3-only, GPL-2.0-only, LGPL-2.1-only | GFDL-1.3-only |
| `libcap-ng0` | 0.7.9-2.2build3 | GPL-2.0-only, GPL-3.0-only, LGPL-2.1-only |  |
| `libcap2` | 1:2.44-1ubuntu0.22.04.3 | BSD-3-Clause, GPL-2.0-only, GPL-2.0-or-later |  |
| `libcom-err2` | 1.46.5-2ubuntu1.2 | (not recorded) |  |
| `libcrypt1` | 1:4.4.27-1 | (not recorded) |  |
| `libcurl4` | 7.81.0-1ubuntu1.30 | BSD-3-Clause, BSD-4-Clause, ISC, curl, other, public-domain | BSD-4-Clause, other, public-domain |
| `libdb5.3` | 5.3.28+dfsg1-0.8ubuntu3 | (not recorded) |  |
| `libdebconfclient0` | 0.261ubuntu1 | (not recorded) |  |
| `libexpat1` | 2.4.7-1ubuntu0.9 | MIT |  |
| `libext2fs2` | 1.46.5-2ubuntu1.2 | GPL-2.0-only, LGPL-2.0-only |  |
| `libffi8` | 3.4.2-4 | GPL-2.0-or-later |  |
| `libfontconfig1` | 2.13.1-4.2ubuntu5 | (not recorded) |  |
| `libfreetype6` | 2.11.1+dfsg-1ubuntu0.4 | BSD-3-Clause, BSL-1.0, FSFAP, FTL, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, GPL-3.0-or-later, MIT, OpenGroup-BSD-like, Public-Domain, Zlib | FSFAP, FTL, OpenGroup-BSD-like, Public-Domain |
| `libgcc-s1` | 12.3.0-1ubuntu1~22.04.3 | (not recorded) |  |
| `libgcrypt20` | 1.9.4-3ubuntu3.3 | GPL-2.0-only, LGPL-2.0-or-later |  |
| `libgmp10` | 2:6.2.1+dfsg-3ubuntu1 | GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, LGPL-3.0-only |  |
| `libgnutls30` | 3.7.3-4ubuntu1.9 | Apache-2.0, BSD-3-Clause, CC0-1.0, GFDL-1.3-only, GPL-2.0-or-later, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT | CC0-1.0, GFDL-1.3-only |
| `libgpg-error0` | 1.43-3 | BSD-3-Clause, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, g10-permissive | g10-permissive |
| `libgssapi-krb5-2` | 1.19.2-2ubuntu0.10 | GPL-2.0-only |  |
| `libhogweed6` | 3.7.3-1build2 | GAP, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-or-later, GPL-3.0-with-autoconf-exception+, LGPL-2.0-only, LGPL-2.0-or-later, LGPL-3.0-or-later, MIT, public-domain | GAP, GPL-3.0-with-autoconf-exception+, public-domain |
| `libidn2-0` | 2.3.2-2build1 | GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, GPL-3.0-or-later, LGPL-3.0-only, LGPL-3.0-or-later, Unicode | Unicode |
| `libk5crypto3` | 1.19.2-2ubuntu0.10 | GPL-2.0-only |  |
| `libkeyutils1` | 1.6.1-2ubuntu3 | GPL-2.0-only, GPL-2.0-or-later, LGPL-2.0-only, LGPL-2.0-or-later |  |
| `libkrb5-3` | 1.19.2-2ubuntu0.10 | GPL-2.0-only |  |
| `libkrb5support0` | 1.19.2-2ubuntu0.10 | GPL-2.0-only |  |
| `libksba8` | 1.6.0-2ubuntu0.2 | FSFUL, GPL-3.0-only, LGPL-2.1-or-later | FSFUL |
| `libldap-2.5-0` | 2.5.20+dfsg-0ubuntu0.22.04.1 | (not recorded) |  |
| `liblz4-1` | 1.9.3-2build2 | BSD-2-Clause, GPL-2.0-only, GPL-2.0-or-later |  |
| `liblzma5` | 5.2.5-2ubuntu1.2 | Autoconf, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, LGPL-2.0-only, LGPL-2.1-only, LGPL-2.1-or-later, PD, PD-debian, config-h, noderivs, none, permissive-fsf, permissive-nowarranty, probably-PD | Autoconf, PD, PD-debian, config-h, noderivs, none, permissive-fsf, permissive-nowarranty, probably-PD |
| `libmount1` | 2.37.2-4ubuntu3.6 | BSD-2-Clause, BSD-3-Clause, BSD-4-Clause, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.0-only, LGPL-2.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, public-domain | BSD-4-Clause, public-domain |
| `libncurses6` | 6.3-2ubuntu0.3 | (not recorded) |  |
| `libncursesw6` | 6.3-2ubuntu0.3 | (not recorded) |  |
| `libnettle8` | 3.7.3-1build2 | GAP, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-or-later, GPL-3.0-with-autoconf-exception+, LGPL-2.0-only, LGPL-2.0-or-later, LGPL-3.0-or-later, MIT, public-domain | GAP, GPL-3.0-with-autoconf-exception+, public-domain |
| `libnghttp2-14` | 1.43.0-1ubuntu0.4 | BSD-2-Clause, GPL-3.0-only, GPL-3.0-with-autoconf-exception+, MIT, SIL-OFL-1.1, all-permissive | GPL-3.0-with-autoconf-exception+, SIL-OFL-1.1, all-permissive |
| `libnpth0` | 1.6-3build2 | LGPL-2.1-only, LGPL-2.1-or-later |  |
| `libnsl2` | 1.3.0-2build2 | BSD-3-Clause, GPL-2+-autoconf-exception, GPL-2+-libtool-exception, GPL-2.0-only, GPL-3+-autoconf-exception, GPL-3.0-only, LGPL-2.1-only, LGPL-2.1-or-later, MIT, permissive-autoconf-m4, permissive-autoconf-m4-no-warranty, permissive-configure, permissive-fsf, permissive-makefile-in | GPL-2+-autoconf-exception, GPL-2+-libtool-exception, GPL-3+-autoconf-exception, permissive-autoconf-m4, permissive-autoconf-m4-no-warranty, permissive-configure, permissive-fsf, permissive-makefile-in |
| `libp11-kit0` | 0.24.0-6ubuntu0.1 | Apache-2.0, BSD-3-Clause, ISC, ISC+IBM, LGPL-2.1-only, LGPL-2.1-or-later, permissive-like-automake-output, same-as-rest-of-p11kit | ISC+IBM, permissive-like-automake-output, same-as-rest-of-p11kit |
| `libpam-modules` | 1.4.0-11ubuntu2.8 | GPL-2.0-or-later |  |
| `libpam-modules-bin` | 1.4.0-11ubuntu2.8 | GPL-2.0-or-later |  |
| `libpam-runtime` | 1.4.0-11ubuntu2.8 | GPL-2.0-or-later |  |
| `libpam0g` | 1.4.0-11ubuntu2.8 | GPL-2.0-or-later |  |
| `libpcre2-8-0` | 10.39-3ubuntu0.2 | (not recorded) |  |
| `libpcre3` | 2:8.39-13ubuntu0.22.04.1 | (not recorded) |  |
| `libpng16-16` | 1.6.37-3ubuntu0.7 | Apache-2.0, BSD-3-Clause, BSD-like-with-advertising-clause, GPL-2.0-only, GPL-2.0-or-later, Libpng, MIT, libpng OR Apache-2.0 OR BSD-3-clause | BSD-like-with-advertising-clause, Libpng |
| `libprocps8` | 2:3.3.17-6ubuntu2.1 | GPL-2.0-only, GPL-2.0-or-later, LGPL-2.0-only, LGPL-2.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later |  |
| `libpsl5` | 0.21.0-1.2build2 | Chromium, MIT | Chromium |
| `libreadline8` | 8.1.2-1 | GFDL-1.3-or-later, GPL-3.0-only | GFDL-1.3-or-later |
| `librtmp1` | 2.4+20151223.gitfa8646d.1-2build4 | GPL-2.0-only, LGPL-2.1-only |  |
| `libsasl2-2` | 2.1.27+dfsg2-3ubuntu1.2 | BSD-2-Clause, BSD-2.2-clause, BSD-3-Clause, BSD-3-clause-JANET, BSD-3-clause-PADL, BSD-4-Clause, BSD-4-Clause-UC, FSFULLR, GPL-3.0-only, GPL-3.0-or-later, MIT-CMU, MIT-Export, MIT-OpenVision, OpenLDAP, OpenSSL, RSA-MD, SSLeay, text://BSD-4-clause and IBM-as-is, text://IBM-as-is | BSD-2.2-clause, BSD-3-clause-JANET, BSD-3-clause-PADL, BSD-4-Clause, BSD-4-Clause-UC, FSFULLR, MIT-CMU, MIT-Export, MIT-OpenVision, OpenLDAP, OpenSSL, RSA-MD, SSLeay, text://BSD-4-clause and IBM-as-is, text://IBM-as-is |
| `libsasl2-modules-db` | 2.1.27+dfsg2-3ubuntu1.2 | BSD-2-Clause, BSD-2.2-clause, BSD-3-Clause, BSD-3-clause-JANET, BSD-3-clause-PADL, BSD-4-Clause, BSD-4-Clause-UC, FSFULLR, GPL-3.0-only, GPL-3.0-or-later, MIT-CMU, MIT-Export, MIT-OpenVision, OpenLDAP, OpenSSL, RSA-MD, SSLeay, text://BSD-4-clause and IBM-as-is, text://IBM-as-is | BSD-2.2-clause, BSD-3-clause-JANET, BSD-3-clause-PADL, BSD-4-Clause, BSD-4-Clause-UC, FSFULLR, MIT-CMU, MIT-Export, MIT-OpenVision, OpenLDAP, OpenSSL, RSA-MD, SSLeay, text://BSD-4-clause and IBM-as-is, text://IBM-as-is |
| `libseccomp2` | 2.5.3-2ubuntu3~22.04.1 | LGPL-2.1-only |  |
| `libselinux1` | 3.3-1build2 | GPL-2.0-only, LGPL-2.1-only |  |
| `libsemanage-common` | 3.3-1build2 | GPL-2.0-or-later, LGPL-2.0-or-later |  |
| `libsemanage2` | 3.3-1build2 | GPL-2.0-or-later, LGPL-2.0-or-later |  |
| `libsepol2` | 3.3-1build1 | GPL-2.0-or-later, LGPL-2.0-or-later |  |
| `libsmartcols1` | 2.37.2-4ubuntu3.6 | BSD-2-Clause, BSD-3-Clause, BSD-4-Clause, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.0-only, LGPL-2.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, public-domain | BSD-4-Clause, public-domain |
| `libsqlite3-0` | 3.37.2-2ubuntu0.8 | GPL-2.0-only, GPL-2.0-or-later, public-domain | public-domain |
| `libss2` | 1.46.5-2ubuntu1.2 | (not recorded) |  |
| `libssh-4` | 0.9.6-2ubuntu0.22.04.8 | BSD-2-Clause, BSD-3-Clause, LGPL-2.1+~OpenSSL, LGPL-2.1-only, public-domain | LGPL-2.1+~OpenSSL, public-domain |
| `libssl3` | 3.0.2-0ubuntu1.30 | Apache-2.0, Artistic-2.0, GPL-1.0-only, GPL-1.0-or-later | GPL-1.0-only, GPL-1.0-or-later |
| `libstdc++6` | 12.3.0-1ubuntu1~22.04.3 | (not recorded) |  |
| `libsystemd0` | 249.11-0ubuntu3.23 | CC0-1.0, GPL-2 with Linux-syscall-note exception, GPL-2.0-only, GPL-2.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, MIT, public-domain | CC0-1.0, GPL-2 with Linux-syscall-note exception, public-domain |
| `libtasn1-6` | 4.18.0-4ubuntu0.2 | GFDL-1.3-only, GPL-3.0-only, LGPL-2.0-or-later, LGPL-2.1-only | GFDL-1.3-only |
| `libtinfo6` | 6.3-2ubuntu0.3 | BSD-3-Clause, MIT/X11, X11 | MIT/X11, X11 |
| `libtirpc-common` | 1.3.2-2ubuntu0.1 | BSD-2-Clause, BSD-3-Clause, BSD-4-Clause, GPL-2.0-only, LGPL-2.1-only, LGPL-2.1-or-later, PERMISSIVE, __AUTO_PERMISSIVE__ | BSD-4-Clause, PERMISSIVE, __AUTO_PERMISSIVE__ |
| `libtirpc3` | 1.3.2-2ubuntu0.1 | BSD-2-Clause, BSD-3-Clause, BSD-4-Clause, GPL-2.0-only, LGPL-2.1-only, LGPL-2.1-or-later, PERMISSIVE, __AUTO_PERMISSIVE__ | BSD-4-Clause, PERMISSIVE, __AUTO_PERMISSIVE__ |
| `libudev1` | 249.11-0ubuntu3.23 | CC0-1.0, GPL-2 with Linux-syscall-note exception, GPL-2.0-only, GPL-2.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, MIT, public-domain | CC0-1.0, GPL-2 with Linux-syscall-note exception, public-domain |
| `libunistring2` | 1.0-1 | FreeSoftware, GFDL-1.2-only, GFDL-1.2-or-later, GPL-2+ with distribution exception, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, GPL-3.0-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT | FreeSoftware, GFDL-1.2-only, GFDL-1.2-or-later, GPL-2+ with distribution exception |
| `libuuid1` | 2.37.2-4ubuntu3.6 | BSD-2-Clause, BSD-3-Clause, BSD-4-Clause, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.0-only, LGPL-2.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, public-domain | BSD-4-Clause, public-domain |
| `libxxhash0` | 0.8.1-1 | BSD-2-Clause, GPL-2.0-only |  |
| `libzstd1` | 1.4.8+dfsg-3build1 | BSD-3-Clause, GPL-2.0-only, MIT, Zlib |  |
| `locales` | 2.35-0ubuntu3.15 | GFDL-1.3-only, GPL-2.0-only, LGPL-2.1-only | GFDL-1.3-only |
| `login` | 1:4.8.1-2ubuntu2.2 | GPL-2.0-only |  |
| `logsave` | 1.46.5-2ubuntu1.2 | GPL-2.0-only, LGPL-2.0-only |  |
| `lsb-base` | 11.1.0ubuntu4 | BSD-3-Clause, GPL-2.0-only |  |
| `mawk` | 1.3.4.20200120-3 | GPL-2.0-only |  |
| `mount` | 2.37.2-4ubuntu3.6 | BSD-2-Clause, BSD-3-Clause, BSD-4-Clause, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.0-only, LGPL-2.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, public-domain | BSD-4-Clause, public-domain |
| `ncurses-base` | 6.3-2ubuntu0.3 | BSD-3-Clause, MIT/X11, X11 | MIT/X11, X11 |
| `ncurses-bin` | 6.3-2ubuntu0.3 | BSD-3-Clause, MIT/X11, X11 | MIT/X11, X11 |
| `openssl` | 3.0.2-0ubuntu1.30 | (not recorded) |  |
| `p11-kit` | 0.24.0-6ubuntu0.1 | Apache-2.0, BSD-3-Clause, ISC, ISC+IBM, LGPL-2.1-only, LGPL-2.1-or-later, permissive-like-automake-output, same-as-rest-of-p11kit | ISC+IBM, permissive-like-automake-output, same-as-rest-of-p11kit |
| `p11-kit-modules` | 0.24.0-6ubuntu0.1 | (not recorded) |  |
| `passwd` | 1:4.8.1-2ubuntu2.2 | GPL-2.0-only |  |
| `perl-base` | 5.34.0-3ubuntu1.10 | Artistic-2, Artistic-2.0, Artistic-dist, BSD-3-Clause, BSD-3-clause-GENERIC, BSD-3-clause-with-weird-numbering, BSD-4-clause-POWERDOG, BZIP, CC0-1.0, DONT-CHANGE-THE-GPL, GPL-1.0-only, GPL-1.0-or-later, GPL-2.0-only, GPL-2.0-or-later, GPL-2.0-with-bison-exception+, HSIEH-BSD, HSIEH-DERIVATIVE, LGPL-2.1-only, MIT, REGCOMP, RRA-KEEP-THIS-NOTICE, SDBM-PUBLIC-DOMAIN, TEXT-TABS, Unicode, Zlib | Artistic-2, Artistic-dist, BSD-3-clause-GENERIC, BSD-3-clause-with-weird-numbering, BSD-4-clause-POWERDOG, BZIP, CC0-1.0, DONT-CHANGE-THE-GPL, GPL-1.0-only, GPL-1.0-or-later, GPL-2.0-with-bison-exception+, HSIEH-BSD, HSIEH-DERIVATIVE, REGCOMP, RRA-KEEP-THIS-NOTICE, SDBM-PUBLIC-DOMAIN, TEXT-TABS, Unicode |
| `pinentry-curses` | 1.1.1-1build2 | GPL-2.0-only, GPL-2.0-or-later, LGPL-3.0-only, LGPL-3.0-or-later, X11 | X11 |
| `procps` | 2:3.3.17-6ubuntu2.1 | GPL-2.0-only, GPL-2.0-or-later, LGPL-2.0-only, LGPL-2.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later |  |
| `readline-common` | 8.1.2-1 | GFDL-1.3-or-later, GPL-3.0-only | GFDL-1.3-or-later |
| `sed` | 4.8-1ubuntu2.1 | GPL-3.0-only |  |
| `sensible-utils` | 0.0.17 | All-permissive, GPL-2.0-only, GPL-2.0-or-later, configure, installsh | All-permissive, configure, installsh |
| `sysvinit-utils` | 3.01-1ubuntu1 | GPL-2.0-only, GPL-2.0-or-later |  |
| `tar` | 1.34+dfsg-1ubuntu0.1.22.04.6 | GPL-2.0-only, GPL-3.0-only |  |
| `tzdata` | 2026e-0ubuntu0.22.04.1 | ICU |  |
| `ubuntu-keyring` | 2021.03.26 | GPL-2.0-or-later |  |
| `ucf` | 3.0043 | GPL-2.0-only |  |
| `usrmerge` | 25ubuntu2 | GPL-2.0-or-later |  |
| `util-linux` | 2.37.2-4ubuntu3.6 | BSD-2-Clause, BSD-3-Clause, BSD-4-Clause, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.0-only, LGPL-2.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, public-domain | BSD-4-Clause, public-domain |
| `wget` | 1.21.2-2ubuntu1.5 | GFDL-1.2-only, GPL-3.0-only | GFDL-1.2-only |
| `zlib1g` | 1:1.2.11.dfsg-2ubuntu9.2 | Zlib |  |

## grafana/grafana:12.4.15

| Gói | Phiên bản | Giấy phép | Ngoài OSI |
|---|---|---|---|
| `./apps/live` |  | (not recorded) |  |
| `alpine-baselayout` | 3.7.2-r1 | GPL-2.0-only |  |
| `alpine-baselayout-data` | 3.7.2-r1 | GPL-2.0-only |  |
| `alpine-keys` | 2.6-r0 | MIT |  |
| `alpine-release` | 3.24.3-r0 | MIT |  |
| `apk-tools` | 3.0.8-r0 | GPL-2.0-only |  |
| `bash` | 5.3.9-r1 | GPL-3.0-or-later |  |
| `brotli-libs` | 1.2.1-r0 | MIT |  |
| `bubblewrap` | 0.12.0-r0 | LGPL-2.1-or-later |  |
| `buf.build/gen/go/parca-dev/parca/connectrpc/go` | v1.18.1-20250703125925-3f0fcf4bff96.1 | Apache-2.0 |  |
| `buf.build/gen/go/parca-dev/parca/protocolbuffers/go` | v1.36.2-20250703125925-3f0fcf4bff96.1 | Apache-2.0 |  |
| `busybox` | 1.37.0-r31 | GPL-2.0-only |  |
| `busybox-binsh` | 1.37.0-r31 | GPL-2.0-only |  |
| `c-ares` | 1.34.9-r0 | MIT |  |
| `ca-certificates` | 20261020-r0 | MIT, MPL-2.0 |  |
| `ca-certificates-bundle` | 20261020-r0 | MIT, MPL-2.0 |  |
| `cel.dev/expr` | v0.25.2 | Apache-2.0 |  |
| `cloud.google.com/go` | v0.123.0 | Apache-2.0, BSD-3-Clause |  |
| `cloud.google.com/go/auth` | v0.20.0 | Apache-2.0 |  |
| `cloud.google.com/go/auth/oauth2adapt` | v0.2.8 | Apache-2.0 |  |
| `cloud.google.com/go/compute/metadata` | v0.9.0 | Apache-2.0 |  |
| `cloud.google.com/go/iam` | v1.7.0 | Apache-2.0 |  |
| `cloud.google.com/go/monitoring` | v1.24.3 | Apache-2.0 |  |
| `cloud.google.com/go/storage` | v1.62.1 | Apache-2.0 |  |
| `connectrpc.com/connect` | v1.19.1 | Apache-2.0 |  |
| `curl` | 8.23.0-r0 | curl |  |
| `dario.cat/mergo` | v1.0.2 | BSD-3-Clause |  |
| `elasticsearch` | 12.4.14 | (not recorded) |  |
| `filippo.io/age` | v1.2.1 | BSD-3-Clause |  |
| `github.com/1NCE-GmbH/grpc-go-pool` | v0.0.0-20231117122434-2a5bb974daa2 | MIT |  |
| `github.com/Azure/azure-pipeline-go` | v0.2.3 | MIT |  |
| `github.com/Azure/azure-sdk-for-go/sdk/azcore` | v1.22.0 | MIT |  |
| `github.com/Azure/azure-sdk-for-go/sdk/azidentity` | v1.14.0 | MIT |  |
| `github.com/Azure/azure-sdk-for-go/sdk/internal` | v1.12.0 | MIT |  |
| `github.com/Azure/azure-sdk-for-go/sdk/storage/azblob` | v1.6.3 | MIT |  |
| `github.com/Azure/azure-storage-blob-go` | v0.15.0 | MIT |  |
| `github.com/Azure/go-autorest/autorest/to` | v0.4.1 | Apache-2.0 |  |
| `github.com/Azure/go-ntlmssp` | v0.1.1 | MIT |  |
| `github.com/AzureAD/microsoft-authentication-library-for-go` | v1.7.2 | MIT |  |
| `github.com/BurntSushi/toml` | v1.5.0 | MIT |  |
| `github.com/FZambia/eagle` | v0.2.0 | MIT |  |
| `github.com/GoogleCloudPlatform/opentelemetry-operations-go/detectors/gcp` | v1.33.0 | Apache-2.0 |  |
| `github.com/GoogleCloudPlatform/opentelemetry-operations-go/exporter/metric` | v0.55.0 | Apache-2.0 |  |
| `github.com/GoogleCloudPlatform/opentelemetry-operations-go/internal/resourcemapping` | v0.55.0 | Apache-2.0 |  |
| `github.com/IBM/pgxpoolprometheus` | v1.1.3 | Apache-2.0 |  |
| `github.com/Machiel/slugify` | v1.0.1 | MIT |  |
| `github.com/Masterminds/goutils` | v1.1.1 | Apache-2.0 |  |
| `github.com/Masterminds/semver` | v1.5.0 | MIT |  |
| `github.com/Masterminds/semver/v3` | v3.4.0 | MIT |  |
| `github.com/Masterminds/sprig/v3` | v3.3.0 | MIT |  |
| `github.com/Masterminds/squirrel` | v1.5.4 | MIT |  |
| `github.com/NYTimes/gziphandler` | v1.1.1 | Apache-2.0 |  |
| `github.com/ProtonMail/go-crypto` | v1.3.0 | BSD-3-Clause |  |
| `github.com/RoaringBitmap/roaring/v2` | v2.4.5 | Apache-2.0, BSD-3-Clause |  |
| `github.com/VividCortex/mysqlerr` | v0.0.0-20170204212430-6c6b55f8796f | MIT |  |
| `github.com/Yiling-J/theine-go` | v0.6.2 | MIT |  |
| `github.com/agext/levenshtein` | v1.2.1 | Apache-2.0 |  |
| `github.com/alecthomas/units` | v0.0.0-20240927000941-0f3dac36c52b | MIT |  |
| `github.com/andybalholm/brotli` | v1.2.7 | MIT, BSD-3-Clause |  |
| `github.com/antlr4-go/antlr/v4` | v4.13.1 | BSD-3-Clause |  |
| `github.com/apache/arrow-go/v18` | v18.5.2 | Apache-2.0, BSD-3-Clause |  |
| `github.com/apache/thrift` | v0.24.0 | Apache-2.0, BSD-3-Clause |  |
| `github.com/apapsch/go-jsonmerge/v2` | v2.0.0 | MIT |  |
| `github.com/apparentlymart/go-textseg/v15` | v15.0.0 | MIT, Apache-2.0, Unicode-DFS-2016 |  |
| `github.com/armon/go-metrics` | v0.4.1 | MIT |  |
| `github.com/armon/go-radix` | v1.0.0 | MIT |  |
| `github.com/at-wat/mqtt-go` | v0.19.6 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go` | v1.55.8 | Apache-2.0, BSD-3-Clause |  |
| `github.com/aws/aws-sdk-go-v2` | v1.42.0 | Apache-2.0, BSD-3-Clause |  |
| `github.com/aws/aws-sdk-go-v2/aws/protocol/eventstream` | v1.7.10 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go-v2/config` | v1.32.25 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go-v2/credentials` | v1.19.24 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go-v2/feature/ec2/imds` | v1.18.29 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go-v2/feature/s3/manager` | v1.17.84 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go-v2/internal/configsources` | v1.4.29 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go-v2/internal/endpoints/v2` | v2.7.29 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go-v2/internal/v4a` | v1.4.30 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go-v2/service/cloudwatch` | v1.45.3 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go-v2/service/cloudwatchlogs` | v1.73.0 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go-v2/service/ec2` | v1.307.0 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go-v2/service/internal/accept-encoding` | v1.13.12 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go-v2/service/internal/checksum` | v1.9.15 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go-v2/service/internal/presigned-url` | v1.13.29 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go-v2/service/internal/s3shared` | v1.19.23 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go-v2/service/oam` | v1.18.3 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go-v2/service/resourcegroupstaggingapi` | v1.26.6 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go-v2/service/s3` | v1.101.0 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go-v2/service/signin` | v1.2.0 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go-v2/service/sso` | v1.31.3 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go-v2/service/ssooidc` | v1.36.6 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go-v2/service/sts` | v1.43.3 | Apache-2.0 |  |
| `github.com/aws/smithy-go` | v1.27.2 | Apache-2.0, BSD-3-Clause |  |
| `github.com/bahlo/generic-list-go` | v0.2.0 | BSD-3-Clause |  |
| `github.com/barkimedes/go-deepcopy` | v0.0.0-20220514131651-17c30cfc62df | MIT |  |
| `github.com/benbjohnson/clock` | v1.3.5 | MIT |  |
| `github.com/beorn7/perks` | v1.0.1 | MIT |  |
| `github.com/bits-and-blooms/bitset` | v1.24.4 | BSD-3-Clause |  |
| `github.com/blang/semver` | v3.5.1+incompatible | MIT |  |
| `github.com/blang/semver/v4` | v4.0.0 | MIT |  |
| `github.com/blevesearch/bleve/v2` | v2.5.7 | Apache-2.0 |  |
| `github.com/blevesearch/bleve_index_api` | v1.3.0 | Apache-2.0 |  |
| `github.com/blevesearch/geo` | v0.2.4 | Apache-2.0 |  |
| `github.com/blevesearch/go-porterstemmer` | v1.0.3 | MIT |  |
| `github.com/blevesearch/gtreap` | v0.1.1 | MIT |  |
| `github.com/blevesearch/mmap-go` | v1.0.4 | BSD-3-Clause |  |
| `github.com/blevesearch/scorch_segment_api/v2` | v2.3.13 | Apache-2.0 |  |
| `github.com/blevesearch/segment` | v0.9.1 | Apache-2.0 |  |
| `github.com/blevesearch/snowballstem` | v0.9.0 | BSD-3-Clause |  |
| `github.com/blevesearch/upsidedown_store_api` | v1.0.2 | Apache-2.0 |  |
| `github.com/blevesearch/vellum` | v1.1.0 | Apache-2.0 |  |
| `github.com/blevesearch/zapx/v11` | v11.4.2 | Apache-2.0 |  |
| `github.com/blevesearch/zapx/v12` | v12.4.2 | Apache-2.0 |  |
| `github.com/blevesearch/zapx/v13` | v13.4.2 | Apache-2.0 |  |
| `github.com/blevesearch/zapx/v14` | v14.4.2 | Apache-2.0 |  |
| `github.com/blevesearch/zapx/v15` | v15.4.2 | Apache-2.0 |  |
| `github.com/blevesearch/zapx/v16` | v16.2.8 | Apache-2.0 |  |
| `github.com/bluele/gcache` | v0.0.2 | MIT |  |
| `github.com/bradfitz/gomemcache` | v0.0.0-20250403215159-8d39553ac7cf | Apache-2.0 |  |
| `github.com/bufbuild/protocompile` | v0.14.1 | Apache-2.0 |  |
| `github.com/buger/jsonparser` | v1.1.2 | MIT |  |
| `github.com/bwmarrin/snowflake` | v0.3.0 | BSD-2-Clause |  |
| `github.com/c2h5oh/datasize` | v0.0.0-20231215233829-aa82cc1e6500 | MIT |  |
| `github.com/cenkalti/backoff/v4` | v4.3.0 | MIT |  |
| `github.com/cenkalti/backoff/v5` | v5.0.3 | MIT |  |
| `github.com/centrifugal/centrifuge` | v0.38.0 | MIT, BSD-2-Clause |  |
| `github.com/centrifugal/protocol` | v0.17.0 | MIT |  |
| `github.com/cespare/xxhash/v2` | v2.3.0 | MIT |  |
| `github.com/cheekybits/genny` | v1.0.0 | MIT |  |
| `github.com/chromedp/cdproto` | v0.0.0-20250803210736-d308e07a266d | MIT |  |
| `github.com/clipperhouse/uax29/v2` | v2.7.0 | MIT |  |
| `github.com/cloudflare/circl` | v1.6.3 | BSD-3-Clause |  |
| `github.com/cncf/xds/go` | v0.0.0-20260202195803-dba9d589def2 | Apache-2.0 |  |
| `github.com/cockroachdb/apd/v2` | v2.0.2 | Apache-2.0 |  |
| `github.com/containerd/errdefs` | v1.0.0 | Apache-2.0 |  |
| `github.com/containerd/errdefs/pkg` | v0.3.0 | Apache-2.0 |  |
| `github.com/coreos/go-semver` | v0.3.1 | Apache-2.0 |  |
| `github.com/coreos/go-systemd/v22` | v22.7.0 | Apache-2.0 |  |
| `github.com/cpuguy83/go-md2man/v2` | v2.0.7 | MIT |  |
| `github.com/davecgh/go-spew` | v1.1.2-0.20180830191138-d8f796af33cc | ISC |  |
| `github.com/dennwc/varint` | v1.0.0 | MIT |  |
| `github.com/dgraph-io/badger/v4` | v4.7.0 | Apache-2.0 |  |
| `github.com/dgraph-io/ristretto/v2` | v2.2.0 | Apache-2.0, MIT |  |
| `github.com/dgryski/go-rendezvous` | v0.0.0-20200823014737-9f7001d12a5f | MIT |  |
| `github.com/diegoholiveira/jsonlogic/v3` | v3.7.4 | MIT |  |
| `github.com/distribution/reference` | v0.6.0 | Apache-2.0 |  |
| `github.com/dlmiddlecote/sqlstats` | v1.0.2 | MIT |  |
| `github.com/docker/go-connections` | v0.7.0 | Apache-2.0 |  |
| `github.com/docker/go-units` | v0.5.0 | Apache-2.0 |  |
| `github.com/dolthub/flatbuffers/v23` | v23.3.3-dh.2 | Apache-2.0 |  |
| `github.com/dolthub/jsonpath` | v0.0.2-0.20240227200619-19675ab05c71 | MIT |  |
| `github.com/dolthub/maphash` | v0.1.0 | Apache-2.0 |  |
| `github.com/dolthub/vitess` | v0.0.0-20260225173707-20566e4abe9e | Apache-2.0, BSD-2-Clause |  |
| `github.com/dustin/go-humanize` | v1.0.1 | MIT |  |
| `github.com/edsrzf/mmap-go` | v1.2.1-0.20241212181136-fad1cd13edbd | BSD-3-Clause |  |
| `github.com/elazarl/goproxy` | v1.8.0 | BSD-3-Clause |  |
| `github.com/emicklei/go-restful/v3` | v3.13.0 | MIT |  |
| `github.com/emirpasic/gods` | v1.18.1 | BSD-2-Clause, ISC |  |
| `github.com/envoyproxy/go-control-plane/envoy` | v1.37.0 | Apache-2.0 |  |
| `github.com/envoyproxy/protoc-gen-validate` | v1.3.3 | Apache-2.0 |  |
| `github.com/facette/natsort` | v0.0.0-20181210072756-2cd4dd1e2dcb | BSD-3-Clause |  |
| `github.com/fatih/color` | v1.19.0 | MIT |  |
| `github.com/felixge/httpsnoop` | v1.1.0 | MIT |  |
| `github.com/fsnotify/fsnotify` | v1.10.1 | BSD-3-Clause |  |
| `github.com/fullstorydev/grpchan` | v1.1.1 | MIT |  |
| `github.com/fxamacker/cbor/v2` | v2.9.0 | MIT |  |
| `github.com/gammazero/deque` | v0.2.1 | MIT |  |
| `github.com/gchaincl/sqlhooks` | v1.3.0 | MIT |  |
| `github.com/getkin/kin-openapi` | v0.151.0 | MIT |  |
| `github.com/go-asn1-ber/asn1-ber` | v1.5.4 | MIT |  |
| `github.com/go-jose/go-jose/v4` | v4.1.6 | Apache-2.0, BSD-3-Clause |  |
| `github.com/go-kit/log` | v0.2.1 | MIT |  |
| `github.com/go-ldap/ldap/v3` | v3.4.4 | MIT |  |
| `github.com/go-logfmt/logfmt` | v0.6.1 | MIT |  |
| `github.com/go-logr/logr` | v1.4.4 | Apache-2.0 |  |
| `github.com/go-logr/stdr` | v1.2.2 | Apache-2.0 |  |
| `github.com/go-openapi/analysis` | v0.25.5 | Apache-2.0 |  |
| `github.com/go-openapi/errors` | v0.22.8 | Apache-2.0 |  |
| `github.com/go-openapi/jsonpointer` | v1.0.0 | Apache-2.0 |  |
| `github.com/go-openapi/jsonreference` | v1.0.0 | Apache-2.0 |  |
| `github.com/go-openapi/loads` | v0.25.0 | Apache-2.0 |  |
| `github.com/go-openapi/runtime` | v0.33.0 | Apache-2.0, MIT |  |
| `github.com/go-openapi/runtime/server-middleware` | v0.30.0 | Apache-2.0 |  |
| `github.com/go-openapi/spec` | v0.22.9 | Apache-2.0 |  |
| `github.com/go-openapi/strfmt` | v0.27.0 | Apache-2.0 |  |
| `github.com/go-openapi/swag` | v0.28.0 | Apache-2.0 |  |
| `github.com/go-openapi/swag/cmdutils` | v0.28.0 | Apache-2.0 |  |
| `github.com/go-openapi/swag/conv` | v0.28.0 | Apache-2.0 |  |
| `github.com/go-openapi/swag/fileutils` | v0.28.0 | Apache-2.0 |  |
| `github.com/go-openapi/swag/jsonutils` | v0.28.0 | Apache-2.0 |  |
| `github.com/go-openapi/swag/loading` | v0.28.0 | Apache-2.0 |  |
| `github.com/go-openapi/swag/mangling` | v0.28.0 | Apache-2.0 |  |
| `github.com/go-openapi/swag/netutils` | v0.28.0 | Apache-2.0 |  |
| `github.com/go-openapi/swag/pools` | v0.28.0 | Apache-2.0 |  |
| `github.com/go-openapi/swag/stringutils` | v0.28.0 | Apache-2.0 |  |
| `github.com/go-openapi/swag/typeutils` | v0.28.0 | Apache-2.0 |  |
| `github.com/go-openapi/swag/yamlutils` | v0.28.0 | Apache-2.0 |  |
| `github.com/go-openapi/validate` | v0.26.1 | Apache-2.0 |  |
| `github.com/go-sourcemap/sourcemap` | v2.1.4+incompatible | BSD-2-Clause |  |
| `github.com/go-sql-driver/mysql` | v1.7.1 | MPL-2.0 |  |
| `github.com/go-stack/stack` | v1.8.1 | MIT |  |
| `github.com/go-viper/mapstructure/v2` | v2.5.0 | MIT |  |
| `github.com/gobwas/glob` | v0.2.3 | MIT |  |
| `github.com/goccy/go-json` | v0.10.6 | MIT |  |
| `github.com/gofrs/uuid` | v4.4.0+incompatible | MIT |  |
| `github.com/gogo/googleapis` | v1.4.1 | Apache-2.0 |  |
| `github.com/gogo/protobuf` | v1.3.2 | BSD-3-Clause |  |
| `github.com/gogo/status` | v1.1.1 | Apache-2.0 |  |
| `github.com/golang-jwt/jwt/v4` | v4.5.2 | MIT |  |
| `github.com/golang-jwt/jwt/v5` | v5.3.1 | MIT |  |
| `github.com/golang-migrate/migrate/v4` | v4.7.0 | MIT |  |
| `github.com/golang-sql/civil` | v0.0.0-20220223132316-b832511892a9 | Apache-2.0 |  |
| `github.com/golang-sql/sqlexp` | v0.1.0 | BSD-3-Clause |  |
| `github.com/golang/mock` | v1.7.0-rc.1 | Apache-2.0 |  |
| `github.com/golang/protobuf` | v1.5.4 | BSD-3-Clause |  |
| `github.com/golang/snappy` | v1.0.0 | BSD-3-Clause |  |
| `github.com/google/btree` | v1.1.3 | Apache-2.0 |  |
| `github.com/google/cel-go` | v0.31.0 | Apache-2.0, BSD-3-Clause |  |
| `github.com/google/flatbuffers` | v25.12.19+incompatible | Apache-2.0 |  |
| `github.com/google/gnostic` | v0.7.1 | Apache-2.0 |  |
| `github.com/google/gnostic-models` | v0.7.1 | Apache-2.0 |  |
| `github.com/google/go-cmp` | v0.7.0 | BSD-3-Clause |  |
| `github.com/google/go-github/v82` | v82.0.0 | BSD-3-Clause |  |
| `github.com/google/go-querystring` | v1.2.0 | BSD-3-Clause |  |
| `github.com/google/s2a-go` | v0.1.9 | Apache-2.0 |  |
| `github.com/google/uuid` | v1.6.0 | BSD-3-Clause |  |
| `github.com/google/wire` | v0.7.0 | Apache-2.0 |  |
| `github.com/googleapis/enterprise-certificate-proxy` | v0.3.15 | Apache-2.0 |  |
| `github.com/googleapis/gax-go/v2` | v2.22.0 | BSD-3-Clause |  |
| `github.com/gorilla/mux` | v1.8.1 | BSD-3-Clause |  |
| `github.com/gorilla/websocket` | v1.5.4-0.20250319132907-e064f32e3674 | BSD-2-Clause |  |
| `github.com/grafana/alerting` | v0.0.0-20261006175226-b4dbe97caa73 | AGPL-3.0 |  |
| `github.com/grafana/authlib` | v0.0.0-20260203153107-16a114a99f67 | Apache-2.0 |  |
| `github.com/grafana/authlib/types` | v0.0.0-20260203131350-b83e80394acc | Apache-2.0 |  |
| `github.com/grafana/cue` | v0.0.0-20230926092038-971951014e3f | (not recorded) |  |
| `github.com/grafana/dataplane/sdata` | v0.0.9 | Apache-2.0 |  |
| `github.com/grafana/dskit` | v0.0.0-20260209132809-8d1c6d34bb5a | Apache-2.0 |  |
| `github.com/grafana/go-mysql-server` | v0.20.2-grafana | (not recorded) |  |
| `github.com/grafana/gomemcache` | v0.0.0-20251127154401-74f93547077b | Apache-2.0 |  |
| `github.com/grafana/grafana` |  | (not recorded) |  |
| `github.com/grafana/grafana-app-sdk` | v0.50.2 | Apache-2.0 |  |
| `github.com/grafana/grafana-app-sdk/logging` | v0.50.2 | Apache-2.0 |  |
| `github.com/grafana/grafana-aws-sdk` | v1.4.3 | Apache-2.0 |  |
| `github.com/grafana/grafana-azure-sdk-go/v2` | v2.3.1 | Apache-2.0 |  |
| `github.com/grafana/grafana-cloud-migration-snapshot` | v1.9.0 | BSD-2-Clause |  |
| `github.com/grafana/grafana-google-sdk-go` | v0.4.2 | Apache-2.0 |  |
| `github.com/grafana/grafana-plugin-sdk-go` | v0.287.0 | Apache-2.0 |  |
| `github.com/grafana/grafana/apps/advisor` |  | (not recorded) |  |
| `github.com/grafana/grafana/apps/alerting/historian` |  | (not recorded) |  |
| `github.com/grafana/grafana/apps/alerting/notifications` |  | (not recorded) |  |
| `github.com/grafana/grafana/apps/alerting/rules` |  | (not recorded) |  |
| `github.com/grafana/grafana/apps/annotation` |  | (not recorded) |  |
| `github.com/grafana/grafana/apps/collections` |  | (not recorded) |  |
| `github.com/grafana/grafana/apps/correlations` |  | (not recorded) |  |
| `github.com/grafana/grafana/apps/dashboard` |  | (not recorded) |  |
| `github.com/grafana/grafana/apps/dashvalidator` |  | (not recorded) |  |
| `github.com/grafana/grafana/apps/example` |  | (not recorded) |  |
| `github.com/grafana/grafana/apps/folder` |  | (not recorded) |  |
| `github.com/grafana/grafana/apps/iam` |  | (not recorded) |  |
| `github.com/grafana/grafana/apps/logsdrilldown` |  | (not recorded) |  |
| `github.com/grafana/grafana/apps/playlist` |  | (not recorded) |  |
| `github.com/grafana/grafana/apps/plugins` |  | (not recorded) |  |
| `github.com/grafana/grafana/apps/preferences` |  | (not recorded) |  |
| `github.com/grafana/grafana/apps/provisioning` |  | (not recorded) |  |
| `github.com/grafana/grafana/apps/quotas` |  | (not recorded) |  |
| `github.com/grafana/grafana/apps/scope` |  | (not recorded) |  |
| `github.com/grafana/grafana/apps/secret` |  | (not recorded) |  |
| `github.com/grafana/grafana/apps/shorturl` |  | (not recorded) |  |
| `github.com/grafana/grafana/pkg/aggregator` |  | (not recorded) |  |
| `github.com/grafana/grafana/pkg/apimachinery` |  | (not recorded) |  |
| `github.com/grafana/grafana/pkg/apiserver` |  | (not recorded) |  |
| `github.com/grafana/grafana/pkg/plugins` |  | (not recorded) |  |
| `github.com/grafana/grafana/pkg/promlib` |  | (not recorded) |  |
| `github.com/grafana/grafana/pkg/semconv` |  | (not recorded) |  |
| `github.com/grafana/grafana/pkg/storage/unified/resource/kv` |  | (not recorded) |  |
| `github.com/grafana/jsonparser` | v0.0.0-20241004153430-023329977675 | MIT |  |
| `github.com/grafana/loki/pkg/push` | v0.0.0-20250823105456-332df2b20000 | Apache-2.0 |  |
| `github.com/grafana/loki/v3` | v3.7.1 | AGPL-3.0, Apache-2.0, MIT |  |
| `github.com/grafana/memberlist` | v0.3.1-0.20251126142931-6f9f62ab6f86 | (not recorded) |  |
| `github.com/grafana/nanogit` | v0.3.0 | Apache-2.0 |  |
| `github.com/grafana/otel-profiling-go` | v0.5.1 | Apache-2.0 |  |
| `github.com/grafana/prometheus-alertmanager` | v0.25.1-0.20260112162805-d29cc9cf7f0f | (not recorded) |  |
| `github.com/grafana/pyroscope-go/godeltaprof` | v0.1.9 | Apache-2.0 |  |
| `github.com/grafana/pyroscope/api` | v1.2.1-0.20251118081820-ace37f973a0f | Apache-2.0 |  |
| `github.com/grafana/regexp` | v0.0.0-20250905093917-f7b3be9d1853 | BSD-3-Clause |  |
| `github.com/grafana/sqlds/v5` | v5.0.4 | Apache-2.0 |  |
| `github.com/grafana/tempo` | v1.5.1-0.20260427112133-525d1bab07e0 | AGPL-3.0 |  |
| `github.com/grpc-ecosystem/go-grpc-middleware` | v1.4.0 | Apache-2.0 |  |
| `github.com/grpc-ecosystem/go-grpc-middleware/providers/prometheus` | v1.1.0 | Apache-2.0 |  |
| `github.com/grpc-ecosystem/go-grpc-middleware/v2` | v2.3.3 | Apache-2.0 |  |
| `github.com/grpc-ecosystem/go-grpc-prometheus` | v1.2.1-0.20191002090509-6af20e3a5340 | Apache-2.0 |  |
| `github.com/grpc-ecosystem/grpc-gateway/v2` | v2.30.0 | BSD-3-Clause |  |
| `github.com/hashicorp/consul/api` | v1.33.4 | MPL-2.0 |  |
| `github.com/hashicorp/errwrap` | v1.1.0 | MPL-2.0 |  |
| `github.com/hashicorp/go-cleanhttp` | v0.5.2 | MPL-2.0 |  |
| `github.com/hashicorp/go-hclog` | v1.6.3 | MIT |  |
| `github.com/hashicorp/go-immutable-radix` | v1.3.1 | MPL-2.0 |  |
| `github.com/hashicorp/go-metrics` | v0.5.4 | MIT |  |
| `github.com/hashicorp/go-msgpack/v2` | v2.1.5 | MIT |  |
| `github.com/hashicorp/go-multierror` | v1.1.1 | MPL-2.0 |  |
| `github.com/hashicorp/go-plugin` | v1.7.0 | MPL-2.0 |  |
| `github.com/hashicorp/go-rootcerts` | v1.0.2 | MPL-2.0 |  |
| `github.com/hashicorp/go-secure-stdlib/plugincontainer` | v0.5.0 | MPL-2.0 |  |
| `github.com/hashicorp/go-sockaddr` | v1.0.7 | MPL-2.0 |  |
| `github.com/hashicorp/go-uuid` | v1.0.3 | MPL-2.0 |  |
| `github.com/hashicorp/go-version` | v1.9.0 | MPL-2.0 |  |
| `github.com/hashicorp/golang-lru` | v1.0.2 | MPL-2.0 |  |
| `github.com/hashicorp/golang-lru/v2` | v2.0.7 | MPL-2.0 |  |
| `github.com/hashicorp/hcl/v2` | v2.24.0 | MPL-2.0 |  |
| `github.com/hashicorp/serf` | v0.10.2 | MPL-2.0 |  |
| `github.com/hashicorp/yamux` | v0.1.2 | MPL-2.0 |  |
| `github.com/huandu/xstrings` | v1.5.0 | MIT |  |
| `github.com/influxdata/influxdb-client-go/v2` | v2.13.0 | MIT |  |
| `github.com/influxdata/influxql` | v1.4.0 | MIT |  |
| `github.com/influxdata/line-protocol` | v0.0.0-20210922203350-b1ad95c89adf | MIT |  |
| `github.com/invopop/jsonschema` | v0.14.0 | MIT |  |
| `github.com/jackc/pgpassfile` | v1.0.0 | MIT |  |
| `github.com/jackc/pgservicefile` | v0.0.0-20240606120523-5a60cdf6a761 | MIT |  |
| `github.com/jackc/pgx/v5` | v5.10.0 | MIT |  |
| `github.com/jackc/puddle/v2` | v2.2.2 | MIT |  |
| `github.com/jaegertracing/jaeger-idl` | v0.6.0 | Apache-2.0 |  |
| `github.com/jcmturner/aescts/v2` | v2.0.0 | Apache-2.0 |  |
| `github.com/jcmturner/dnsutils/v2` | v2.0.0 | Apache-2.0 |  |
| `github.com/jcmturner/gofork` | v1.7.6 | BSD-3-Clause |  |
| `github.com/jcmturner/goidentity/v6` | v6.0.1 | Apache-2.0 |  |
| `github.com/jcmturner/gokrb5/v8` | v8.4.4 | Apache-2.0 |  |
| `github.com/jcmturner/rpc/v2` | v2.0.3 | Apache-2.0 |  |
| `github.com/jessevdk/go-flags` | v1.6.1 | BSD-3-Clause |  |
| `github.com/jhump/protoreflect` | v1.17.0 | Apache-2.0 |  |
| `github.com/jmespath-community/go-jmespath` | v1.1.1 | Apache-2.0 |  |
| `github.com/jmespath/go-jmespath` | v0.4.0 | Apache-2.0 |  |
| `github.com/jmoiron/sqlx` | v1.4.0 | MIT |  |
| `github.com/josharian/intern` | v1.0.0 | MIT |  |
| `github.com/joshlf/go-acl` | v0.0.0-20200411065538-eae00ae38531 | Apache-2.0, MIT |  |
| `github.com/jpillora/backoff` | v1.0.0 | MIT |  |
| `github.com/json-iterator/go` | v1.1.12 | MIT |  |
| `github.com/jszwedko/go-datemath` | v0.1.1-0.20230526204004-640a500621d6 | ISC |  |
| `github.com/klauspost/compress` | v1.20.3 | BSD-3-Clause, Apache-2.0, MIT |  |
| `github.com/klauspost/cpuid/v2` | v2.3.0 | MIT |  |
| `github.com/kylelemons/godebug` | v1.1.0 | Apache-2.0 |  |
| `github.com/lann/builder` | v0.0.0-20180802200727-47ae307949d0 | MIT |  |
| `github.com/lann/ps` | v0.0.0-20150810152359-62de8c46ede0 | MIT |  |
| `github.com/lestrrat-go/strftime` | v1.0.4 | MIT |  |
| `github.com/lib/pq` | v1.12.3 | MIT |  |
| `github.com/m3db/prometheus_remote_client_golang` | v0.4.4 | Apache-2.0 |  |
| `github.com/mailru/easyjson` | v0.9.1 | MIT |  |
| `github.com/mattbaird/jsonpatch` | v0.0.0-20240118010651-0ba75a80ca38 | Apache-2.0 |  |
| `github.com/mattetti/filebuffer` | v1.0.1 | MIT |  |
| `github.com/mattn/go-colorable` | v0.1.14 | MIT |  |
| `github.com/mattn/go-ieproxy` | v0.0.12 | MIT |  |
| `github.com/mattn/go-isatty` | v0.0.23 | MIT |  |
| `github.com/mattn/go-runewidth` | v0.0.20 | MIT |  |
| `github.com/matttproud/golang_protobuf_extensions` | v1.0.4 | Apache-2.0 |  |
| `github.com/maypok86/otter` | v1.2.4 | Apache-2.0 |  |
| `github.com/mdlayher/socket` | v0.5.1 | MIT |  |
| `github.com/mdlayher/vsock` | v1.2.1 | MIT |  |
| `github.com/mfridman/interpolate` | v0.0.2 | MIT |  |
| `github.com/microsoft/go-mssqldb` | v1.10.0 | BSD-3-Clause, MIT |  |
| `github.com/miekg/dns` | v1.1.72 | BSD-3-Clause |  |
| `github.com/mitchellh/copystructure` | v1.2.0 | MIT |  |
| `github.com/mitchellh/go-homedir` | v1.1.0 | MIT |  |
| `github.com/mitchellh/go-wordwrap` | v1.0.1 | MIT |  |
| `github.com/mitchellh/mapstructure` | v1.5.1-0.20231216201459-8508981c8b6c | MIT |  |
| `github.com/mitchellh/reflectwalk` | v1.0.2 | MIT |  |
| `github.com/mithrandie/csvq` | v1.18.1 | MIT, BSD-3-Clause |  |
| `github.com/mithrandie/csvq-driver` | v1.7.0 | MIT |  |
| `github.com/mithrandie/go-file/v2` | v2.1.0 | MIT |  |
| `github.com/mithrandie/go-text` | v1.6.0 | MIT |  |
| `github.com/mithrandie/ternary` | v1.1.1 | MIT |  |
| `github.com/moby/docker-image-spec` | v1.3.1 | Apache-2.0 |  |
| `github.com/moby/moby/api` | v1.55.0 | Apache-2.0 |  |
| `github.com/moby/moby/client` | v0.5.0 | Apache-2.0 |  |
| `github.com/modern-go/concurrent` | v0.0.0-20180306012644-bacd9c7ef1dd | Apache-2.0 |  |
| `github.com/modern-go/reflect2` | v1.0.3-0.20250322232337-35a7c28c31ee | Apache-2.0 |  |
| `github.com/mpvl/unique` | v0.0.0-20150818121801-cbe035fff7de | MIT |  |
| `github.com/munnerz/goautoneg` | v0.0.0-20191010083416-a7dc8b61c822 | BSD-3-Clause |  |
| `github.com/mwitkow/go-conntrack` | v0.0.0-20190716064945-2f068394615f | Apache-2.0 |  |
| `github.com/natefinch/wrap` | v0.2.0 | MIT |  |
| `github.com/nikunjy/rules` | v1.5.0 | MIT |  |
| `github.com/oapi-codegen/runtime` | v1.6.0 | Apache-2.0 |  |
| `github.com/oasdiff/yaml` | v0.1.1 | MIT, BSD-3-Clause |  |
| `github.com/oasdiff/yaml3` | v0.0.14 | MIT, Apache-2.0 |  |
| `github.com/oklog/run` | v1.2.0 | Apache-2.0 |  |
| `github.com/oklog/ulid` | v1.3.1 | Apache-2.0 |  |
| `github.com/oklog/ulid/v2` | v2.1.2 | Apache-2.0 |  |
| `github.com/olekukonko/tablewriter` | v0.0.5 | MIT |  |
| `github.com/open-feature/go-sdk` | v1.17.0 | Apache-2.0 |  |
| `github.com/open-feature/go-sdk-contrib/providers/go-feature-flag` | v0.2.6 | Apache-2.0 |  |
| `github.com/open-feature/go-sdk-contrib/providers/ofrep` | v0.1.7 | Apache-2.0 |  |
| `github.com/opencontainers/go-digest` | v1.0.0 | Apache-2.0, CC-BY-SA-4.0 | CC-BY-SA-4.0 |
| `github.com/opencontainers/image-spec` | v1.1.1 | Apache-2.0 |  |
| `github.com/openfga/api/proto` | v0.0.0-20260319214821-f153694bfc20 | Apache-2.0 |  |
| `github.com/openfga/language/pkg/go` | v0.3.2-0.20260730144454-83fedf8a4e70 | Apache-2.0 |  |
| `github.com/openfga/openfga` | v1.18.3 | Apache-2.0 |  |
| `github.com/opentracing-contrib/go-grpc` | v0.1.2 | Apache-2.0 |  |
| `github.com/opentracing-contrib/go-stdlib` | v1.1.1 | Apache-2.0 |  |
| `github.com/opentracing/opentracing-go` | v1.2.1-0.20220228012449-10b1cf09e00b | Apache-2.0 |  |
| `github.com/openzipkin/zipkin-go` | v0.4.3 | Apache-2.0 |  |
| `github.com/patrickmn/go-cache` | v2.1.0+incompatible | MIT |  |
| `github.com/pb33f/ordered-map/v2` | v2.3.1 | Apache-2.0 |  |
| `github.com/pelletier/go-toml/v2` | v2.2.4 | MIT |  |
| `github.com/pierrec/lz4/v4` | v4.1.27 | BSD-3-Clause |  |
| `github.com/pires/go-proxyproto` | v0.8.1 | Apache-2.0 |  |
| `github.com/pkg/browser` | v0.0.0-20240102092130-5ac0b6a4141c | BSD-2-Clause |  |
| `github.com/pkg/errors` | v0.9.1 | BSD-2-Clause |  |
| `github.com/planetscale/vtprotobuf` | v0.6.1-0.20240319094008-0393e58bdf10 | BSD-3-Clause, MIT |  |
| `github.com/pmezard/go-difflib` | v1.0.1-0.20181226105442-5d4384ee4fb2 | BSD-3-Clause |  |
| `github.com/pressly/goose/v3` | v3.27.3 | MIT |  |
| `github.com/prometheus/client_golang` | v1.24.0 | Apache-2.0, BSD-3-Clause |  |
| `github.com/prometheus/client_golang/exp` | v0.0.0-20260602051030-3537b20ac86b | Apache-2.0 |  |
| `github.com/prometheus/client_model` | v0.6.2 | Apache-2.0 |  |
| `github.com/prometheus/common` | v0.70.0 | Apache-2.0 |  |
| `github.com/prometheus/common/sigv4` | v0.1.0 | Apache-2.0 |  |
| `github.com/prometheus/exporter-toolkit` | v0.16.0 | Apache-2.0 |  |
| `github.com/prometheus/otlptranslator` | v1.0.0 | Apache-2.0 |  |
| `github.com/prometheus/procfs` | v0.21.1 | Apache-2.0 |  |
| `github.com/prometheus/prometheus` | v0.313.0 | Apache-2.0 |  |
| `github.com/prometheus/sigv4` | v0.4.1 | Apache-2.0 |  |
| `github.com/puzpuzpuz/xsync/v2` | v2.5.1 | MIT |  |
| `github.com/quagmt/udecimal` | v1.9.0 | BSD-3-Clause |  |
| `github.com/redis/go-redis/v9` | v9.18.0 | BSD-2-Clause |  |
| `github.com/redis/rueidis` | v1.0.68 | Apache-2.0 |  |
| `github.com/remyoudompheng/bigfft` | v0.0.0-20230129092748-24d4a6f8daec | BSD-3-Clause |  |
| `github.com/rs/cors` | v1.11.1 | MIT |  |
| `github.com/russross/blackfriday/v2` | v2.1.0 | BSD-2-Clause |  |
| `github.com/sagikazarmark/locafero` | v0.11.0 | MIT |  |
| `github.com/santhosh-tekuri/jsonschema/v6` | v6.0.3 | Apache-2.0 |  |
| `github.com/sean-/seed` | v0.0.0-20170313163322-e2103e2c3529 | MIT, BSD-3-Clause |  |
| `github.com/segmentio/asm` | v1.2.1 | MIT-0 |  |
| `github.com/segmentio/encoding` | v0.5.3 | MIT, Apache-2.0 |  |
| `github.com/sercand/kuberesolver/v6` | v6.0.1 | Apache-2.0 |  |
| `github.com/sethvargo/go-retry` | v0.4.0 | Apache-2.0 |  |
| `github.com/shadowspore/fossil-delta` | v0.0.0-20241213113458-1d797d70cbe3 | BSD-2-Clause |  |
| `github.com/shopspring/decimal` | v1.4.0 | MIT |  |
| `github.com/shurcooL/httpfs` | v0.0.0-20230704072500-f1e31cf0ba5c | MIT |  |
| `github.com/shurcooL/vfsgen` | v0.0.0-20230704071429-0000e147ea92 | MIT |  |
| `github.com/sirupsen/logrus` | v1.9.4 | MIT |  |
| `github.com/sony/gobreaker/v2` | v2.4.0 | MIT |  |
| `github.com/sourcegraph/conc` | v0.3.1-0.20240121214520-5f936abd7ae8 | MIT |  |
| `github.com/spf13/afero` | v1.15.0 | Apache-2.0 |  |
| `github.com/spf13/cast` | v1.10.0 | MIT |  |
| `github.com/spf13/cobra` | v1.10.2 | Apache-2.0 |  |
| `github.com/spf13/pflag` | v1.0.10 | BSD-3-Clause |  |
| `github.com/spf13/viper` | v1.21.0 | MIT |  |
| `github.com/spiffe/go-spiffe/v2` | v2.7.0 | Apache-2.0 |  |
| `github.com/stretchr/objx` | v0.5.3 | MIT |  |
| `github.com/stretchr/testify` | v1.12.1 | MIT, BSD-3-Clause, ISC |  |
| `github.com/subosito/gotenv` | v1.6.0 | MIT |  |
| `github.com/thomaspoignant/go-feature-flag` | v1.42.0 | MIT |  |
| `github.com/tjhop/slog-gokit` | v0.1.6 | Apache-2.0 |  |
| `github.com/ua-parser/uap-go` | v0.0.0-20251207011819-db9adb27a0b8 | Apache-2.0, MIT |  |
| `github.com/uber/jaeger-client-go` | v2.30.0+incompatible | Apache-2.0 |  |
| `github.com/uber/jaeger-lib` | v2.4.1+incompatible | Apache-2.0 |  |
| `github.com/urfave/cli/v2` | v2.27.7 | MIT |  |
| `github.com/valyala/bytebufferpool` | v1.0.0 | MIT |  |
| `github.com/wk8/go-ordered-map` | v1.0.0 | Apache-2.0 |  |
| `github.com/x448/float16` | v0.8.4 | MIT |  |
| `github.com/xlab/treeprint` | v1.2.0 | MIT |  |
| `github.com/xrash/smetrics` | v0.0.0-20240521201337-686a1a2994c1 | MIT |  |
| `github.com/youmark/pkcs8` | v0.0.0-20240726163527-a2c0da244d78 | MIT |  |
| `github.com/zclconf/go-cty` | v1.16.3 | MIT |  |
| `github.com/zeebo/xxh3` | v1.1.0 | BSD-2-Clause |  |
| `go.etcd.io/bbolt` | v1.4.3 | MIT |  |
| `go.etcd.io/etcd/api/v3` | v3.6.7 | Apache-2.0 |  |
| `go.etcd.io/etcd/client/pkg/v3` | v3.6.14 | Apache-2.0 |  |
| `go.etcd.io/etcd/client/v3` | v3.6.7 | Apache-2.0 |  |
| `go.opentelemetry.io/auto/sdk` | v1.2.1 | Apache-2.0 |  |
| `go.opentelemetry.io/collector/featuregate` | v1.60.0 | Apache-2.0 |  |
| `go.opentelemetry.io/collector/pdata` | v1.60.0 | Apache-2.0 |  |
| `go.opentelemetry.io/contrib/bridges/prometheus` | v0.68.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/contrib/detectors/gcp` | v1.44.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/contrib/exporters/autoexport` | v0.68.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/contrib/instrumentation/google.golang.org/grpc/otelgrpc` | v0.70.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/contrib/instrumentation/net/http/httptrace/otelhttptrace` | v0.69.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp` | v0.70.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/contrib/propagators/jaeger` | v1.43.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/contrib/samplers/jaegerremote` | v0.34.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/otel` | v1.46.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/otel/exporters/jaeger` | v1.17.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/otel/exporters/otlp/otlplog/otlploggrpc` | v0.21.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/otel/exporters/otlp/otlplog/otlploghttp` | v0.21.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/otel/exporters/otlp/otlpmetric/otlpmetricgrpc` | v1.46.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/otel/exporters/otlp/otlpmetric/otlpmetrichttp` | v1.46.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/otel/exporters/otlp/otlptrace` | v1.46.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc` | v1.46.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp` | v1.46.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/otel/exporters/prometheus` | v0.65.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/otel/exporters/stdout/stdoutlog` | v0.21.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/otel/exporters/stdout/stdoutmetric` | v1.46.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/otel/exporters/stdout/stdouttrace` | v1.46.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/otel/log` | v0.21.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/otel/metric` | v1.46.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/otel/sdk` | v1.46.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/otel/sdk/log` | v0.21.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/otel/sdk/metric` | v1.46.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/otel/trace` | v1.46.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/proto/otlp` | v1.11.0 | Apache-2.0 |  |
| `go.uber.org/atomic` | v1.11.0 | MIT |  |
| `go.uber.org/mock` | v0.6.0 | Apache-2.0 |  |
| `go.uber.org/multierr` | v1.11.0 | MIT |  |
| `go.uber.org/zap` | v1.28.0 | MIT |  |
| `go.yaml.in/yaml/v2` | v2.4.4 | Apache-2.0 |  |
| `go.yaml.in/yaml/v3` | v3.0.5 | MIT, Apache-2.0 |  |
| `go.yaml.in/yaml/v4` | v4.0.0-rc.5 | Apache-2.0 |  |
| `go4.org/netipx` | v0.0.0-20230125063823-8449b0a6169f | BSD-3-Clause |  |
| `gocloud.dev` | v0.43.0 | Apache-2.0 |  |
| `golang.org/x/crypto` | v0.58.0 | BSD-3-Clause |  |
| `golang.org/x/exp` | v0.0.0-20260718201538-764159d718ef | BSD-3-Clause |  |
| `golang.org/x/net` | v0.62.0 | BSD-3-Clause |  |
| `golang.org/x/oauth2` | v0.36.0 | BSD-3-Clause |  |
| `golang.org/x/sync` | v0.24.0 | BSD-3-Clause |  |
| `golang.org/x/sys` | v0.49.0 | BSD-3-Clause |  |
| `golang.org/x/term` | v0.47.0 | BSD-3-Clause |  |
| `golang.org/x/text` | v0.43.0 | BSD-3-Clause |  |
| `golang.org/x/time` | v0.15.0 | BSD-3-Clause |  |
| `golang.org/x/tools` | v0.51.0 | BSD-3-Clause |  |
| `golang.org/x/xerrors` | v0.0.0-20240903120638-7835f813f4da | BSD-3-Clause |  |
| `gomodules.xyz/jsonpatch/v2` | v2.5.0 | Apache-2.0 |  |
| `gonum.org/v1/gonum` | v0.17.0 | BSD-3-Clause |  |
| `google.golang.org/api` | v0.278.0 | BSD-3-Clause |  |
| `google.golang.org/genproto` | v0.0.0-20260319201613-d00831a3d3e7 | Apache-2.0 |  |
| `google.golang.org/genproto/googleapis/api` | v0.0.0-20260819154853-08b0e4226688 | Apache-2.0 |  |
| `google.golang.org/genproto/googleapis/rpc` | v0.0.0-20260819154853-08b0e4226688 | Apache-2.0 |  |
| `google.golang.org/grpc` | v1.83.2 | Apache-2.0 |  |
| `google.golang.org/protobuf` | v1.36.12 | BSD-3-Clause |  |
| `gopkg.in/evanphx/json-patch.v4` | v4.13.0 | BSD-3-Clause |  |
| `gopkg.in/inf.v0` | v0.9.1 | BSD-3-Clause |  |
| `gopkg.in/ini.v1` | v1.67.2 | Apache-2.0 |  |
| `gopkg.in/mail.v2` | v2.3.1 | MIT |  |
| `gopkg.in/natefinch/lumberjack.v2` | v2.2.1 | MIT |  |
| `gopkg.in/src-d/go-errors.v1` | v1.0.0 | Apache-2.0 |  |
| `gopkg.in/telebot.v3` | v3.3.8 | MIT |  |
| `gopkg.in/yaml.v2` | v2.4.0 | Apache-2.0 |  |
| `gopkg.in/yaml.v3` | v3.0.1 | MIT, Apache-2.0 |  |
| `grafana-azure-monitor-datasource` | 12.4.14 | (not recorded) |  |
| `grafana-postgresql-datasource` | 12.4.14 | (not recorded) |  |
| `grafana-pyroscope-datasource` | 12.4.14 | (not recorded) |  |
| `grafana-testdata-datasource` | 12.4.14 | (not recorded) |  |
| `graphite` | 12.4.14 | (not recorded) |  |
| `jaeger` | 12.4.14 | (not recorded) |  |
| `k8s.io/api` | v0.35.0 | Apache-2.0 |  |
| `k8s.io/apiextensions-apiserver` | v0.35.0 | Apache-2.0 |  |
| `k8s.io/apimachinery` | v0.35.0 | Apache-2.0, BSD-3-Clause |  |
| `k8s.io/apiserver` | v0.35.0 | Apache-2.0 |  |
| `k8s.io/client-go` | v0.35.0 | Apache-2.0, BSD-3-Clause |  |
| `k8s.io/component-base` | v0.35.0 | Apache-2.0 |  |
| `k8s.io/klog/v2` | v2.130.1 | Apache-2.0 |  |
| `k8s.io/kms` | v0.35.0 | Apache-2.0 |  |
| `k8s.io/kube-aggregator` | v0.35.0 | Apache-2.0 |  |
| `k8s.io/kube-openapi` | v0.0.0-20260127142750-a19766b6e2d4 | Apache-2.0, BSD-3-Clause, MIT |  |
| `k8s.io/utils` | v0.0.0-20251002143259-bc988d571ff4 | Apache-2.0, BSD-3-Clause |  |
| `libapk` | 3.0.8-r0 | GPL-2.0-only |  |
| `libcap2` | 2.78-r0 | BSD-3-Clause, GPL-2.0-only |  |
| `libcrypto3` | 3.5.9-r0 | Apache-2.0 |  |
| `libcurl` | 8.23.0-r0 | curl |  |
| `libidn2` | 2.3.8-r0 | GPL-2.0-or-later, LGPL-3.0-or-later |  |
| `libncursesw` | 6.6_p20260516-r0 | X-11 | X-11 |
| `libpsl` | 0.21.5-r3 | MIT |  |
| `libssl3` | 3.5.9-r0 | Apache-2.0 |  |
| `libunistring` | 1.4.2-r0 | GPL-2.0-or-later, LGPL-3.0-or-later |  |
| `loki` | 12.4.14 | (not recorded) |  |
| `modernc.org/libc` | v1.74.3 | BSD-3-Clause |  |
| `modernc.org/mathutil` | v1.7.1 | BSD-3-Clause |  |
| `modernc.org/memory` | v1.11.0 | BSD-3-Clause |  |
| `modernc.org/sqlite` | v1.54.0 | BSD-3-Clause |  |
| `mssql` | 12.4.14 | (not recorded) |  |
| `musl` | 1.2.6-r3 | MIT |  |
| `musl-utils` | 1.2.6-r3 | BSD-2-Clause, GPL-2.0-or-later, MIT |  |
| `mysql` | 12.4.14 | (not recorded) |  |
| `ncurses-terminfo-base` | 6.6_p20260516-r0 | X-11 | X-11 |
| `nghttp2-libs` | 1.70.0-r0 | MIT |  |
| `opentsdb` | 12.4.14 | (not recorded) |  |
| `parca` | 12.4.14 | (not recorded) |  |
| `readline` | 8.3.3-r1 | GPL-3.0-or-later |  |
| `scanelf` | 1.3.9-r1 | GPL-2.0-only |  |
| `sigs.k8s.io/apiserver-network-proxy/konnectivity-client` | v0.34.0 | Apache-2.0 |  |
| `sigs.k8s.io/json` | v0.0.0-20250730193827-2d320260d730 | Apache-2.0, BSD-3-Clause |  |
| `sigs.k8s.io/randfill` | v1.0.0 | Apache-2.0 |  |
| `sigs.k8s.io/structured-merge-diff/v6` | v6.3.2-0.20260122202528-d9cc6641c482 | Apache-2.0 |  |
| `sigs.k8s.io/yaml` | v1.6.0 | MIT, BSD-3-Clause, Apache-2.0 |  |
| `ssl_client` | 1.37.0-r31 | GPL-2.0-only |  |
| `stackdriver` | 12.4.14 | (not recorded) |  |
| `stdlib` | v1.26.10 | (not recorded) |  |
| `tempo` | 12.4.14 | (not recorded) |  |
| `tzdata` | 2026f-r0 | Public-Domain | Public-Domain |
| `xorm.io/builder` | v0.3.13 | BSD-3-Clause |  |
| `zipkin` | 12.4.14 | (not recorded) |  |
| `zlib` | 1.3.2-r1 | Zlib |  |
| `zstd-libs` | 1.5.7-r2 | BSD-3-Clause, GPL-2.0-or-later |  |

## nginx:1.30-alpine-slim

| Gói | Phiên bản | Giấy phép | Ngoài OSI |
|---|---|---|---|
| `alpine-baselayout` | 3.7.2-r1 | GPL-2.0-only |  |
| `alpine-baselayout-data` | 3.7.2-r1 | GPL-2.0-only |  |
| `alpine-keys` | 2.6-r0 | MIT |  |
| `alpine-release` | 3.24.4-r0 | MIT |  |
| `apk-tools` | 3.0.9-r0 | GPL-2.0-only |  |
| `busybox` | 1.37.0-r31 | GPL-2.0-only |  |
| `busybox-binsh` | 1.37.0-r31 | GPL-2.0-only |  |
| `ca-certificates-bundle` | 20261020-r0 | MIT, MPL-2.0 |  |
| `gettext-envsubst` | 1.0-r0 | GPL-3.0-or-later, LGPL-2.1-or-later, MIT |  |
| `libapk` | 3.0.9-r0 | GPL-2.0-only |  |
| `libcrypto3` | 3.5.9-r1 | Apache-2.0 |  |
| `libintl` | 1.0-r0 | LGPL-2.1-or-later |  |
| `libssl3` | 3.5.9-r1 | Apache-2.0 |  |
| `musl` | 1.2.6-r3 | MIT |  |
| `musl-utils` | 1.2.6-r3 | BSD-2-Clause, GPL-2.0-or-later, MIT |  |
| `nginx` | 1.30.8-r1 | 2-clause, BSD-3-Clause, license | 2-clause, license |
| `pcre2` | 10.49-r0 | BSD-3-Clause |  |
| `scanelf` | 1.3.9-r1 | GPL-2.0-only |  |
| `ssl_client` | 1.37.0-r31 | GPL-2.0-only |  |
| `tzdata` | 2026f-r0 | Public-Domain | Public-Domain |
| `zlib` | 1.3.2-r1 | Zlib |  |

## node:22-alpine

| Gói | Phiên bản | Giấy phép | Ngoài OSI |
|---|---|---|---|
| `abbrev` | 3.0.1 | ISC |  |
| `agent` | 3.0.0 | ISC |  |
| `agent-base` | 7.1.4 | MIT |  |
| `alpine-baselayout` | 3.7.2-r1 | GPL-2.0-only |  |
| `alpine-baselayout-data` | 3.7.2-r1 | GPL-2.0-only |  |
| `alpine-keys` | 2.6-r0 | MIT |  |
| `alpine-release` | 3.24.4-r0 | MIT |  |
| `ansi-regex` | 5.0.1 | MIT |  |
| `ansi-regex` | 6.2.2 | MIT |  |
| `ansi-styles` | 4.3.0 | MIT |  |
| `ansi-styles` | 6.2.3 | MIT |  |
| `apk-tools` | 3.0.9-r0 | GPL-2.0-only |  |
| `aproba` | 2.1.0 | ISC |  |
| `arborist` | 8.0.5 | ISC |  |
| `archy` | 1.0.0 | MIT |  |
| `balanced-match` | 1.0.2 | MIT |  |
| `bin-links` | 5.0.0 | ISC |  |
| `binary-extensions` | 2.3.0 | MIT |  |
| `brace-expansion` | 2.0.2 | MIT |  |
| `bundle` | 3.1.0 | Apache-2.0 |  |
| `busybox` | 1.37.0-r31 | GPL-2.0-only |  |
| `busybox-binsh` | 1.37.0-r31 | GPL-2.0-only |  |
| `ca-certificates-bundle` | 20261020-r0 | MIT, MPL-2.0 |  |
| `cacache` | 19.0.1 | ISC |  |
| `canonical-json` | 2.0.0 | MIT |  |
| `chalk` | 5.6.2 | MIT |  |
| `chownr` | 3.0.0 | BlueOak-1.0.0 |  |
| `ci-info` | 4.4.0 | MIT |  |
| `cidr-regex` | 4.1.3 | BSD-2-Clause |  |
| `cli-columns` | 4.0.0 | MIT |  |
| `cliui` | 8.0.2 | ISC |  |
| `cmd-shim` | 7.0.0 | ISC |  |
| `color-convert` | 2.0.1 | MIT |  |
| `color-name` | 1.1.4 | MIT |  |
| `common-ancestor-path` | 1.0.1 | ISC |  |
| `config` | 9.0.0 | ISC |  |
| `core` | 2.0.0 | Apache-2.0 |  |
| `corepack` | 0.36.0 | MIT |  |
| `cross-spawn` | 7.0.6 | MIT |  |
| `cssesc` | 3.0.0 | MIT |  |
| `debug` | 4.4.3 | MIT |  |
| `diff` | 5.2.2 | BSD-3-Clause |  |
| `eastasianwidth` | 0.2.0 | MIT |  |
| `emoji-regex` | 8.0.0 | MIT |  |
| `emoji-regex` | 9.2.2 | MIT |  |
| `encoding` | 0.1.13 | MIT |  |
| `env-paths` | 2.2.1 | MIT |  |
| `err-code` | 2.0.3 | MIT |  |
| `exponential-backoff` | 3.1.3 | Apache-2.0 |  |
| `fastest-levenshtein` | 1.0.16 | MIT |  |
| `fdir` | 6.5.0 | MIT |  |
| `foreground-child` | 3.3.1 | ISC |  |
| `fs` | 4.0.0 | ISC |  |
| `fs-minipass` | 3.0.3 | ISC |  |
| `fs-minipass` | 4.0.1 | ISC |  |
| `git` | 6.0.3 | ISC |  |
| `glob` | 10.5.0 | ISC |  |
| `graceful-fs` | 4.2.11 | ISC |  |
| `hosted-git-info` | 8.1.0 | ISC |  |
| `http-cache-semantics` | 4.2.0 | BSD-2-Clause |  |
| `http-proxy-agent` | 7.0.2 | MIT |  |
| `https-proxy-agent` | 7.0.6 | MIT |  |
| `iconv-lite` | 0.6.3 | MIT |  |
| `ignore-walk` | 7.0.0 | ISC |  |
| `imurmurhash` | 0.1.4 | MIT |  |
| `ini` | 5.0.0 | ISC |  |
| `init-package-json` | 7.0.2 | ISC |  |
| `installed-package-contents` | 3.0.0 | ISC |  |
| `ip-address` | 10.1.0 | MIT |  |
| `ip-regex` | 5.0.0 | MIT |  |
| `is-cidr` | 5.1.1 | BSD-2-Clause |  |
| `is-fullwidth-code-point` | 3.0.0 | MIT |  |
| `isexe` | 2.0.0 | ISC |  |
| `isexe` | 3.1.5 | BlueOak-1.0.0 |  |
| `jackspeak` | 3.4.3 | BlueOak-1.0.0 |  |
| `json-parse-even-better-errors` | 4.0.0 | MIT |  |
| `json-stringify-nice` | 1.1.4 | ISC |  |
| `jsonparse` | 1.3.1 | MIT |  |
| `just-diff` | 6.0.2 | MIT |  |
| `just-diff-apply` | 5.5.0 | MIT |  |
| `libapk` | 3.0.9-r0 | GPL-2.0-only |  |
| `libatomic` | 15.2.0-r5 | GPL-2.0-or-later, LGPL-2.1-or-later |  |
| `libcrypto3` | 3.5.9-r0 | Apache-2.0 |  |
| `libgcc` | 15.2.0-r5 | GPL-2.0-or-later, LGPL-2.1-or-later |  |
| `libnpmaccess` | 9.0.0 | ISC |  |
| `libnpmdiff` | 7.0.5 | ISC |  |
| `libnpmexec` | 9.0.5 | ISC |  |
| `libnpmfund` | 6.0.5 | ISC |  |
| `libnpmhook` | 11.0.0 | ISC |  |
| `libnpmorg` | 7.0.0 | ISC |  |
| `libnpmpack` | 8.0.5 | ISC |  |
| `libnpmpublish` | 10.0.2 | ISC |  |
| `libnpmsearch` | 8.0.0 | ISC |  |
| `libnpmteam` | 7.0.0 | ISC |  |
| `libnpmversion` | 7.0.0 | ISC |  |
| `libssl3` | 3.5.9-r0 | Apache-2.0 |  |
| `libstdc++` | 15.2.0-r5 | GPL-2.0-or-later, LGPL-2.1-or-later |  |
| `lru-cache` | 10.4.3 | ISC |  |
| `make-fetch-happen` | 14.0.3 | ISC |  |
| `map-workspaces` | 4.0.2 | ISC |  |
| `metavuln-calculator` | 8.0.1 | ISC |  |
| `minimatch` | 9.0.9 | ISC |  |
| `minipass` | 3.3.6 | ISC |  |
| `minipass` | 7.1.3 | BlueOak-1.0.0 |  |
| `minipass-collect` | 2.0.1 | ISC |  |
| `minipass-fetch` | 4.0.1 | MIT |  |
| `minipass-flush` | 1.0.5 | ISC |  |
| `minipass-pipeline` | 1.2.4 | ISC |  |
| `minipass-sized` | 1.0.3 | ISC |  |
| `minizlib` | 3.1.0 | MIT |  |
| `models` | 3.0.1 | MIT |  |
| `ms` | 2.1.3 | MIT |  |
| `musl` | 1.2.6-r3 | MIT |  |
| `musl-utils` | 1.2.6-r3 | BSD-2-Clause, GPL-2.0-or-later, MIT |  |
| `mute-stream` | 2.0.0 | ISC |  |
| `name-from-folder` | 3.0.0 | ISC |  |
| `negotiator` | 1.0.0 | MIT |  |
| `node-gyp` | 11.5.0 | MIT |  |
| `node-gyp` | 4.0.0 | ISC |  |
| `nopt` | 8.1.0 | ISC |  |
| `normalize-package-data` | 7.0.1 | BSD-2-Clause |  |
| `npm` | 10.9.9 | Artistic-2.0 |  |
| `npm-audit-report` | 6.0.0 | ISC |  |
| `npm-bundled` | 4.0.0 | ISC |  |
| `npm-install-checks` | 7.1.2 | BSD-2-Clause |  |
| `npm-normalize-package-bin` | 4.0.0 | ISC |  |
| `npm-package-arg` | 12.0.2 | ISC |  |
| `npm-packlist` | 9.0.0 | ISC |  |
| `npm-pick-manifest` | 10.0.0 | ISC |  |
| `npm-profile` | 11.0.1 | ISC |  |
| `npm-registry-fetch` | 18.0.2 | ISC |  |
| `npm-user-validate` | 3.0.0 | BSD-2-Clause |  |
| `p-map` | 7.0.4 | MIT |  |
| `package-json` | 6.2.0 | ISC |  |
| `package-json-from-dist` | 1.0.1 | BlueOak-1.0.0 |  |
| `pacote` | 19.0.2 | ISC |  |
| `pacote` | 20.0.1 | ISC |  |
| `parse-conflict-json` | 4.0.0 | ISC |  |
| `parseargs` | 0.11.0 | MIT |  |
| `path-key` | 3.1.1 | MIT |  |
| `path-scurry` | 1.11.1 | BlueOak-1.0.0 |  |
| `picomatch` | 4.0.3 | MIT |  |
| `postcss-selector-parser` | 7.1.1 | MIT |  |
| `proc-log` | 5.0.0 | ISC |  |
| `proggy` | 3.0.0 | ISC |  |
| `promise-all-reject-late` | 1.0.1 | ISC |  |
| `promise-call-limit` | 3.0.2 | ISC |  |
| `promise-retry` | 2.0.1 | MIT |  |
| `promise-spawn` | 8.0.3 | ISC |  |
| `promzard` | 2.0.0 | ISC |  |
| `protobuf-specs` | 0.4.3 | Apache-2.0 |  |
| `qrcode-terminal` | 0.12.0 | (not recorded) |  |
| `query` | 4.0.1 | ISC |  |
| `read` | 4.1.0 | ISC |  |
| `read-cmd-shim` | 5.0.0 | ISC |  |
| `read-package-json-fast` | 4.0.0 | ISC |  |
| `redact` | 3.2.2 | ISC |  |
| `retry` | 0.12.0 | MIT |  |
| `run-script` | 9.1.0 | ISC |  |
| `safer-buffer` | 2.1.2 | MIT |  |
| `scanelf` | 1.3.9-r1 | GPL-2.0-only |  |
| `semver` | 7.7.4 | ISC |  |
| `shebang-command` | 2.0.0 | MIT |  |
| `shebang-regex` | 3.0.0 | MIT |  |
| `sign` | 3.1.0 | Apache-2.0 |  |
| `signal-exit` | 4.1.0 | ISC |  |
| `sigstore` | 3.1.0 | Apache-2.0 |  |
| `smart-buffer` | 4.2.0 | MIT |  |
| `socks` | 2.8.7 | MIT |  |
| `socks-proxy-agent` | 8.0.5 | MIT |  |
| `spdx-correct` | 3.2.0 | Apache-2.0 |  |
| `spdx-exceptions` | 2.5.0 | CC-BY-3.0 | CC-BY-3.0 |
| `spdx-expression-parse` | 3.0.1 | MIT |  |
| `spdx-expression-parse` | 4.0.0 | MIT |  |
| `spdx-license-ids` | 3.0.23 | CC0-1.0 | CC0-1.0 |
| `ssl_client` | 1.37.0-r31 | GPL-2.0-only |  |
| `ssri` | 12.0.0 | ISC |  |
| `string-locale-compare` | 1.1.0 | ISC |  |
| `string-width` | 4.2.3 | MIT |  |
| `string-width` | 5.1.2 | MIT |  |
| `strip-ansi` | 6.0.1 | MIT |  |
| `strip-ansi` | 7.2.0 | MIT |  |
| `supports-color` | 9.4.0 | MIT |  |
| `tar` | 7.5.22 | BlueOak-1.0.0 |  |
| `text-table` | 0.2.0 | MIT |  |
| `tiny-relative-date` | 1.3.0 | MIT |  |
| `tinyglobby` | 0.2.15 | MIT |  |
| `treeverse` | 3.0.0 | ISC |  |
| `tuf` | 3.1.1 | Apache-2.0 |  |
| `tuf-js` | 3.1.0 | MIT |  |
| `unique-filename` | 4.0.0 | ISC |  |
| `unique-slug` | 5.0.0 | ISC |  |
| `util-deprecate` | 1.0.2 | MIT |  |
| `validate-npm-package-license` | 3.0.4 | Apache-2.0 |  |
| `validate-npm-package-name` | 6.0.2 | ISC |  |
| `verify` | 2.1.1 | Apache-2.0 |  |
| `walk-up-path` | 3.0.1 | ISC |  |
| `which` | 2.0.2 | ISC |  |
| `which` | 5.0.0 | ISC |  |
| `wrap-ansi` | 7.0.0 | MIT |  |
| `wrap-ansi` | 8.1.0 | MIT |  |
| `write-file-atomic` | 6.0.0 | ISC |  |
| `yallist` | 4.0.0 | ISC |  |
| `yallist` | 5.0.0 | BlueOak-1.0.0 |  |
| `yarn` | 1.22.22 | BSD-2-Clause |  |
| `zlib` | 1.3.2-r1 | Zlib |  |

## node:24.16.0-alpine

| Gói | Phiên bản | Giấy phép | Ngoài OSI |
|---|---|---|---|
| `abbrev` | 4.0.0 | ISC |  |
| `agent` | 4.0.0 | ISC |  |
| `agent-base` | 7.1.4 | MIT |  |
| `alpine-baselayout` | 3.7.2-r1 | GPL-2.0-only |  |
| `alpine-baselayout-data` | 3.7.2-r1 | GPL-2.0-only |  |
| `alpine-keys` | 2.6-r0 | MIT |  |
| `alpine-release` | 3.24.1-r0 | MIT |  |
| `apk-tools` | 3.0.6-r0 | GPL-2.0-only |  |
| `aproba` | 2.1.0 | ISC |  |
| `arborist` | 9.4.3 | ISC |  |
| `archy` | 1.0.0 | MIT |  |
| `balanced-match` | 4.0.4 | MIT |  |
| `bin-links` | 6.0.0 | ISC |  |
| `binary-extensions` | 3.1.0 | MIT |  |
| `brace-expansion` | 5.0.5 | MIT |  |
| `bundle` | 4.0.0 | Apache-2.0 |  |
| `busybox` | 1.37.0-r31 | GPL-2.0-only |  |
| `busybox-binsh` | 1.37.0-r31 | GPL-2.0-only |  |
| `ca-certificates-bundle` | 20260611-r0 | MIT, MPL-2.0 |  |
| `cacache` | 20.0.4 | ISC |  |
| `canonical-json` | 2.0.0 | MIT |  |
| `chalk` | 5.6.2 | MIT |  |
| `chownr` | 3.0.0 | BlueOak-1.0.0 |  |
| `ci-info` | 4.4.0 | MIT |  |
| `cidr-regex` | 5.0.4 | BSD-2-Clause |  |
| `cmd-shim` | 8.0.0 | ISC |  |
| `common-ancestor-path` | 2.0.0 | BlueOak-1.0.0 |  |
| `config` | 10.8.1 | ISC |  |
| `core` | 3.2.0 | Apache-2.0 |  |
| `corepack` | 0.35.0 | MIT |  |
| `cssesc` | 3.0.0 | MIT |  |
| `debug` | 4.4.3 | MIT |  |
| `diff` | 8.0.4 | BSD-3-Clause |  |
| `env-paths` | 2.2.1 | MIT |  |
| `exponential-backoff` | 3.1.3 | Apache-2.0 |  |
| `fastest-levenshtein` | 1.0.16 | MIT |  |
| `fdir` | 6.5.0 | MIT |  |
| `fs` | 5.0.0 | ISC |  |
| `fs-minipass` | 3.0.3 | ISC |  |
| `fs-minipass` | 4.0.1 | ISC |  |
| `git` | 7.0.2 | ISC |  |
| `glob` | 13.0.6 | BlueOak-1.0.0 |  |
| `graceful-fs` | 4.2.11 | ISC |  |
| `hosted-git-info` | 9.0.2 | ISC |  |
| `http-cache-semantics` | 4.2.0 | BSD-2-Clause |  |
| `http-proxy-agent` | 7.0.2 | MIT |  |
| `https-proxy-agent` | 7.0.6 | MIT |  |
| `iconv-lite` | 0.7.2 | MIT |  |
| `ignore-walk` | 8.0.0 | ISC |  |
| `ini` | 6.0.0 | ISC |  |
| `init-package-json` | 8.2.5 | ISC |  |
| `installed-package-contents` | 4.0.0 | ISC |  |
| `ip-address` | 10.1.0 | MIT |  |
| `is-cidr` | 6.0.4 | BSD-2-Clause |  |
| `isexe` | 4.0.0 | BlueOak-1.0.0 |  |
| `json-parse-even-better-errors` | 5.0.0 | MIT |  |
| `json-stringify-nice` | 1.1.4 | ISC |  |
| `jsonparse` | 1.3.1 | MIT |  |
| `just-diff` | 6.0.2 | MIT |  |
| `just-diff-apply` | 5.5.0 | MIT |  |
| `libapk` | 3.0.6-r0 | GPL-2.0-only |  |
| `libcrypto3` | 3.5.7-r0 | Apache-2.0 |  |
| `libgcc` | 15.2.0-r5 | GPL-2.0-or-later, LGPL-2.1-or-later |  |
| `libnpmaccess` | 10.0.3 | ISC |  |
| `libnpmdiff` | 8.1.6 | ISC |  |
| `libnpmexec` | 10.2.6 | ISC |  |
| `libnpmfund` | 7.0.20 | ISC |  |
| `libnpmorg` | 8.0.1 | ISC |  |
| `libnpmpack` | 9.1.6 | ISC |  |
| `libnpmpublish` | 11.1.3 | ISC |  |
| `libnpmsearch` | 9.0.1 | ISC |  |
| `libnpmteam` | 8.0.2 | ISC |  |
| `libnpmversion` | 8.0.3 | ISC |  |
| `libssl3` | 3.5.7-r0 | Apache-2.0 |  |
| `libstdc++` | 15.2.0-r5 | GPL-2.0-or-later, LGPL-2.1-or-later |  |
| `lru-cache` | 11.3.5 | BlueOak-1.0.0 |  |
| `make-fetch-happen` | 15.0.5 | ISC |  |
| `map-workspaces` | 5.0.3 | ISC |  |
| `metavuln-calculator` | 9.0.3 | ISC |  |
| `minimatch` | 10.2.5 | BlueOak-1.0.0 |  |
| `minipass` | 3.3.6 | ISC |  |
| `minipass` | 7.1.3 | BlueOak-1.0.0 |  |
| `minipass-collect` | 2.0.1 | ISC |  |
| `minipass-fetch` | 5.0.2 | MIT |  |
| `minipass-flush` | 1.0.6 | BlueOak-1.0.0 |  |
| `minipass-pipeline` | 1.2.4 | ISC |  |
| `minipass-sized` | 2.0.0 | ISC |  |
| `minizlib` | 3.1.0 | MIT |  |
| `models` | 4.1.0 | MIT |  |
| `ms` | 2.1.3 | MIT |  |
| `musl` | 1.2.6-r2 | MIT |  |
| `musl-utils` | 1.2.6-r2 | BSD-2-Clause, GPL-2.0-or-later, MIT |  |
| `mute-stream` | 3.0.0 | ISC |  |
| `name-from-folder` | 4.0.0 | ISC |  |
| `negotiator` | 1.0.0 | MIT |  |
| `node-gyp` | 12.3.0 | MIT |  |
| `node-gyp` | 5.0.0 | ISC |  |
| `nopt` | 9.0.0 | ISC |  |
| `npm` | 11.13.0 | Artistic-2.0 |  |
| `npm-audit-report` | 7.0.0 | ISC |  |
| `npm-bundled` | 5.0.0 | ISC |  |
| `npm-install-checks` | 8.0.0 | BSD-2-Clause |  |
| `npm-normalize-package-bin` | 5.0.0 | ISC |  |
| `npm-package-arg` | 13.0.2 | ISC |  |
| `npm-packlist` | 10.0.4 | ISC |  |
| `npm-pick-manifest` | 11.0.3 | ISC |  |
| `npm-profile` | 12.0.1 | ISC |  |
| `npm-registry-fetch` | 19.1.1 | ISC |  |
| `npm-user-validate` | 4.0.0 | BSD-2-Clause |  |
| `p-map` | 7.0.4 | MIT |  |
| `package-json` | 7.0.5 | ISC |  |
| `pacote` | 21.5.0 | ISC |  |
| `parse-conflict-json` | 5.0.1 | ISC |  |
| `path-scurry` | 2.0.2 | BlueOak-1.0.0 |  |
| `picomatch` | 4.0.4 | MIT |  |
| `postcss-selector-parser` | 7.1.1 | MIT |  |
| `proc-log` | 6.1.0 | ISC |  |
| `proggy` | 4.0.0 | ISC |  |
| `promise-all-reject-late` | 1.0.1 | ISC |  |
| `promise-call-limit` | 3.0.2 | ISC |  |
| `promise-retry` | 1.0.3 | MIT |  |
| `promise-spawn` | 9.0.1 | ISC |  |
| `promzard` | 3.0.1 | ISC |  |
| `protobuf-specs` | 0.5.1 | Apache-2.0 |  |
| `qrcode-terminal` | 0.12.0 | (not recorded) |  |
| `query` | 5.0.0 | ISC |  |
| `read` | 5.0.1 | ISC |  |
| `read-cmd-shim` | 6.0.0 | ISC |  |
| `redact` | 4.0.0 | ISC |  |
| `run-script` | 10.0.4 | ISC |  |
| `safer-buffer` | 2.1.2 | MIT |  |
| `scanelf` | 1.3.9-r1 | GPL-2.0-only |  |
| `semver` | 7.7.4 | ISC |  |
| `sign` | 4.1.1 | Apache-2.0 |  |
| `signal-exit` | 4.1.0 | ISC |  |
| `sigstore` | 4.1.0 | Apache-2.0 |  |
| `smart-buffer` | 4.2.0 | MIT |  |
| `socks` | 2.8.7 | MIT |  |
| `socks-proxy-agent` | 8.0.5 | MIT |  |
| `spdx-exceptions` | 2.5.0 | CC-BY-3.0 | CC-BY-3.0 |
| `spdx-expression-parse` | 4.0.0 | MIT |  |
| `spdx-license-ids` | 3.0.23 | CC0-1.0 | CC0-1.0 |
| `ssl_client` | 1.37.0-r31 | GPL-2.0-only |  |
| `ssri` | 13.0.1 | ISC |  |
| `string-locale-compare` | 1.1.0 | ISC |  |
| `supports-color` | 10.2.2 | MIT |  |
| `tar` | 7.5.13 | BlueOak-1.0.0 |  |
| `text-table` | 0.2.0 | MIT |  |
| `tiny-relative-date` | 2.0.2 | MIT |  |
| `tinyglobby` | 0.2.16 | MIT |  |
| `treeverse` | 3.0.0 | ISC |  |
| `tuf` | 4.0.2 | Apache-2.0 |  |
| `tuf-js` | 4.1.0 | MIT |  |
| `undici` | 6.25.0 | MIT |  |
| `util-deprecate` | 1.0.2 | MIT |  |
| `validate-npm-package-name` | 7.0.2 | ISC |  |
| `verify` | 3.1.0 | Apache-2.0 |  |
| `walk-up-path` | 4.0.0 | ISC |  |
| `which` | 6.0.1 | ISC |  |
| `write-file-atomic` | 7.0.1 | ISC |  |
| `yallist` | 4.0.0 | ISC |  |
| `yallist` | 5.0.0 | BlueOak-1.0.0 |  |
| `yarn` | 1.22.22 | BSD-2-Clause |  |
| `zlib` | 1.3.2-r0 | Zlib |  |

## pgplex/pgschema:1.13.4

| Gói | Phiên bản | Giấy phép | Ngoài OSI |
|---|---|---|---|
| `alpine-baselayout` | 3.6.5-r0 | GPL-2.0-only |  |
| `alpine-baselayout-data` | 3.6.5-r0 | GPL-2.0-only |  |
| `alpine-keys` | 2.4-r1 | MIT |  |
| `apk-tools` | 2.14.4-r1 | GPL-2.0-only |  |
| `busybox` | 1.36.1-r31 | GPL-2.0-only |  |
| `busybox-binsh` | 1.36.1-r31 | GPL-2.0-only |  |
| `ca-certificates` | 20261020-r0 | MIT, MPL-2.0 |  |
| `ca-certificates-bundle` | 20260413-r0 | MIT, MPL-2.0 |  |
| `github.com/BurntSushi/toml` | v1.5.0 | MIT |  |
| `github.com/fergusstrange/embedded-postgres` | v1.34.0 | MIT |  |
| `github.com/jackc/pgpassfile` | v1.0.0 | MIT |  |
| `github.com/jackc/pgservicefile` | v0.0.0-20240606120523-5a60cdf6a761 | MIT |  |
| `github.com/jackc/pgx/v5` | v5.7.5 | MIT |  |
| `github.com/jackc/puddle/v2` | v2.2.2 | MIT |  |
| `github.com/joho/godotenv` | v1.5.1 | MIT |  |
| `github.com/lib/pq` | v1.10.9 | MIT |  |
| `github.com/pgplex/pgschema` |  | (not recorded) |  |
| `github.com/spf13/cobra` | v1.9.1 | Apache-2.0 |  |
| `github.com/spf13/pflag` | v1.0.6 | BSD-3-Clause |  |
| `github.com/xi2/xz` | v0.0.0-20171230120015-48954b6210f8 | (not recorded) |  |
| `golang.org/x/crypto` | v0.37.0 | BSD-3-Clause |  |
| `golang.org/x/sync` | v0.17.0 | BSD-3-Clause |  |
| `golang.org/x/text` | v0.24.0 | BSD-3-Clause |  |
| `libcrypto3` | 3.3.7-r0 | Apache-2.0 |  |
| `libssl3` | 3.3.7-r0 | Apache-2.0 |  |
| `musl` | 1.2.5-r3 | MIT |  |
| `musl-utils` | 1.2.5-r3 | BSD-2-Clause, GPL-2.0-or-later, MIT |  |
| `scanelf` | 1.3.7-r2 | GPL-2.0-only |  |
| `ssl_client` | 1.36.1-r31 | GPL-2.0-only |  |
| `stdlib` | v1.24.13 | (not recorded) |  |
| `tzdata` | 2026b-r0 | Public-Domain | Public-Domain |
| `zlib` | 1.3.2-r0 | Zlib |  |

## postgres:16-bookworm

| Gói | Phiên bản | Giấy phép | Ngoài OSI |
|---|---|---|---|
| `adduser` | 3.134 | GPL-2.0-only, GPL-2.0-or-later |  |
| `apt` | 2.6.2 | BSD-3-Clause, GPL-2.0-only, GPL-2.0-or-later, MIT |  |
| `base-files` | 12.4+deb12u15 | GPL-2.0-or-later |  |
| `base-passwd` | 3.6.1 | GPL-2.0-only, public-domain | public-domain |
| `bash` | 5.2.15-2+b13 | BSD-4-Clause-UC, GFDL-1.3-no-invariants-only, GFDL-1.3-only, GPL-2.0-only, GPL-2.0-or-later, GPL-3+ with Bison exception, GPL-3.0-only, GPL-3.0-or-later, Latex2e, MIT, permissive | BSD-4-Clause-UC, GFDL-1.3-no-invariants-only, GFDL-1.3-only, GPL-3+ with Bison exception, Latex2e, permissive |
| `bsdutils` | 1:2.38.1-5+deb12u3 | BSD-3-Clause, BSD-4-Clause, BSLA, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.0-only, LGPL-2.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, public-domain | BSD-4-Clause, BSLA, public-domain |
| `coreutils` | 9.1-1 | BSD-4-Clause-UC, FSFULLR, GFDL-1.3-no-invariants-only, GFDL-1.3-only, GPL-3.0-only, GPL-3.0-or-later, ISC | BSD-4-Clause-UC, FSFULLR, GFDL-1.3-no-invariants-only, GFDL-1.3-only |
| `dash` | 0.5.12-2 | BSD-3-Clause, GPL-2.0-only, GPL-2.0-or-later, public-domain | public-domain |
| `debconf` | 1.5.82 | BSD-2-Clause |  |
| `debian-archive-keyring` | 2023.3+deb12u2 | GPL-2.0-or-later |  |
| `debianutils` | 5.7-0.5~deb12u1 | GPL-2.0-only, GPL-2.0-or-later, SMAIL-GPL, public-domain | SMAIL-GPL, public-domain |
| `diffutils` | 1:3.8-4 | FSFAP, FSFULLR, GFDL-1.3-no-invariants-only, GFDL-1.3-only, GPL-2.0-only, GPL-2.0-or-later, GPL-3+ with texinfo exception, GPL-3.0-only, GPL-3.0-or-later, GPL-3.0-with-autoconf-exception+, LGPL-2.0-only, LGPL-2.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, X11, public-domain | FSFAP, FSFULLR, GFDL-1.3-no-invariants-only, GFDL-1.3-only, GPL-3+ with texinfo exception, GPL-3.0-with-autoconf-exception+, X11, public-domain |
| `dirmngr` | 2.2.40-1.1+deb12u2 | BSD-3-Clause, CC0-1.0, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, RFC-Reference, TinySCHEME, permissive | CC0-1.0, RFC-Reference, TinySCHEME, permissive |
| `dpkg` | 1.21.23 | BSD-2-Clause, GPL-2.0-only, GPL-2.0-or-later, public-domain-s-s-d | public-domain-s-s-d |
| `e2fsprogs` | 1.47.0-2+b2 | Apache-2.0, BSD-3-Clause, GPL-2+ with Texinfo exception, GPL-2.0-only, GPL-2.0-or-later, ISC, Kazlib, LGPL-2.0-only, Latex2e, MIT-US-export | GPL-2+ with Texinfo exception, Kazlib, Latex2e, MIT-US-export |
| `findutils` | 4.9.0-4 | BSD-3-Clause, BSD-3-clause and/or GPL-3+, FSFAP, FSFULLR, GFDL-1.3-no-invariants-or-later, GFDL-1.3-only, GPL with automake exception, GPL-2+ with Autoconf-data exception, GPL-2.0-only, GPL-2.0-or-later, GPL-3+ with Autoconf-data exception, GPL-3+ with Bison-2.2 exception, GPL-3.0-only, GPL-3.0-or-later, ISC, ISC and/or LGPL-2.1+, LGPL-2.0-only, LGPL-2.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, X11, public-domain | BSD-3-clause and/or GPL-3+, FSFAP, FSFULLR, GFDL-1.3-no-invariants-or-later, GFDL-1.3-only, GPL with automake exception, GPL-2+ with Autoconf-data exception, GPL-3+ with Autoconf-data exception, GPL-3+ with Bison-2.2 exception, ISC and/or LGPL-2.1+, X11, public-domain |
| `gcc-12-base` | 12.2.0-14+deb12u1 | Artistic-2.0, GFDL-1.2-only, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, LGPL-2.0-or-later | GFDL-1.2-only |
| `github.com/moby/sys/user` | v0.1.0 | Apache-2.0 |  |
| `github.com/tianon/gosu` | v1.19.0 | (not recorded) |  |
| `gnupg` | 2.2.40-1.1+deb12u2 | BSD-3-Clause, CC0-1.0, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, RFC-Reference, TinySCHEME, permissive | CC0-1.0, RFC-Reference, TinySCHEME, permissive |
| `gnupg-l10n` | 2.2.40-1.1+deb12u2 | BSD-3-Clause, CC0-1.0, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, RFC-Reference, TinySCHEME, permissive | CC0-1.0, RFC-Reference, TinySCHEME, permissive |
| `gnupg-utils` | 2.2.40-1.1+deb12u2 | BSD-3-Clause, CC0-1.0, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, RFC-Reference, TinySCHEME, permissive | CC0-1.0, RFC-Reference, TinySCHEME, permissive |
| `golang.org/x/sys` | v0.1.0 | BSD-3-Clause |  |
| `gpg` | 2.2.40-1.1+deb12u2 | BSD-3-Clause, CC0-1.0, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, RFC-Reference, TinySCHEME, permissive | CC0-1.0, RFC-Reference, TinySCHEME, permissive |
| `gpg-agent` | 2.2.40-1.1+deb12u2 | BSD-3-Clause, CC0-1.0, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, RFC-Reference, TinySCHEME, permissive | CC0-1.0, RFC-Reference, TinySCHEME, permissive |
| `gpg-wks-client` | 2.2.40-1.1+deb12u2 | BSD-3-Clause, CC0-1.0, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, RFC-Reference, TinySCHEME, permissive | CC0-1.0, RFC-Reference, TinySCHEME, permissive |
| `gpg-wks-server` | 2.2.40-1.1+deb12u2 | BSD-3-Clause, CC0-1.0, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, RFC-Reference, TinySCHEME, permissive | CC0-1.0, RFC-Reference, TinySCHEME, permissive |
| `gpgconf` | 2.2.40-1.1+deb12u2 | BSD-3-Clause, CC0-1.0, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, RFC-Reference, TinySCHEME, permissive | CC0-1.0, RFC-Reference, TinySCHEME, permissive |
| `gpgsm` | 2.2.40-1.1+deb12u2 | BSD-3-Clause, CC0-1.0, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, RFC-Reference, TinySCHEME, permissive | CC0-1.0, RFC-Reference, TinySCHEME, permissive |
| `gpgv` | 2.2.40-1.1+deb12u2 | BSD-3-Clause, CC0-1.0, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, RFC-Reference, TinySCHEME, permissive | CC0-1.0, RFC-Reference, TinySCHEME, permissive |
| `grep` | 3.8-5 | GPL-3.0-only, GPL-3.0-or-later |  |
| `gzip` | 1.12-1 | FSF-manpages, GFDL-1.3+-no-invariant, GFDL-3, GPL-3.0-only, GPL-3.0-or-later | FSF-manpages, GFDL-1.3+-no-invariant, GFDL-3 |
| `hostname` | 3.23+nmu1 | GPL-2.0-only |  |
| `init-system-helpers` | 1.65.2+deb12u1 | BSD-3-Clause, GPL-2.0-only, GPL-2.0-or-later |  |
| `less` | 590-2.1~deb12u2 | GPL-3.0-only, GPL-3.0-or-later, Less, Spencer-86, X11, public-domain | Less, Spencer-86, X11, public-domain |
| `libacl1` | 2.3.1-3 | GPL-2.0-only, GPL-2.0-or-later, LGPL-2.0-or-later, LGPL-2.1-only |  |
| `libapt-pkg6.0` | 2.6.2 | BSD-3-Clause, GPL-2.0-only, GPL-2.0-or-later, MIT |  |
| `libassuan0` | 2.5.5-5 | GAP, GAP~FSF, GPL-2+ with libtool exception, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later | GAP, GAP~FSF, GPL-2+ with libtool exception |
| `libattr1` | 1:2.5.1-4 | GPL-2.0-only, GPL-2.0-or-later, LGPL-2.0-or-later, LGPL-2.1-only |  |
| `libaudit-common` | 1:3.0.9-1 | GPL-1.0-only, GPL-2.0-only, LGPL-2.1-only | GPL-1.0-only |
| `libaudit1` | 1:3.0.9-1 | GPL-1.0-only, GPL-2.0-only, LGPL-2.1-only | GPL-1.0-only |
| `libblkid1` | 2.38.1-5+deb12u3 | BSD-3-Clause, BSD-4-Clause, BSLA, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.0-only, LGPL-2.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, public-domain | BSD-4-Clause, BSLA, public-domain |
| `libbsd0` | 0.11.7-2 | BSD-2-Clause, BSD-2-Clause-NetBSD, BSD-2-clause-author, BSD-2-clause-verbatim, BSD-3-Clause, BSD-3-clause-John-Birrell, BSD-3-clause-Regents, BSD-3-clause-author, BSD-4-clause-Niels-Provos, Beerware, ISC, ISC-Original, MIT, libutil-David-Nugent, public-domain | BSD-2-Clause-NetBSD, BSD-2-clause-author, BSD-2-clause-verbatim, BSD-3-clause-John-Birrell, BSD-3-clause-Regents, BSD-3-clause-author, BSD-4-clause-Niels-Provos, Beerware, ISC-Original, libutil-David-Nugent, public-domain |
| `libbz2-1.0` | 1.0.8-5+b1 | BSD-3-Clause, GPL-2.0-only |  |
| `libc-bin` | 2.36-9+deb12u14 | GPL-2.0-only, LGPL-2.1-only |  |
| `libc-l10n` | 2.36-9+deb12u14 | GPL-2.0-only, LGPL-2.1-only |  |
| `libc6` | 2.36-9+deb12u14 | GPL-2.0-only, LGPL-2.1-only |  |
| `libcap-ng0` | 0.8.3-1+b3 | GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, LGPL-2.1-only, LGPL-2.1-or-later |  |
| `libcap2` | 1:2.66-4+deb12u3+b1 | BSD-3-Clause, GPL-2.0-only, GPL-2.0-or-later |  |
| `libcom-err2` | 1.47.0-2+b2 | Apache-2.0, BSD-3-Clause, GPL-2+ with Texinfo exception, GPL-2.0-only, GPL-2.0-or-later, ISC, Kazlib, LGPL-2.0-only, Latex2e, MIT-US-export | GPL-2+ with Texinfo exception, Kazlib, Latex2e, MIT-US-export |
| `libcrypt1` | 1:4.4.33-2 | (not recorded) |  |
| `libdb5.3` | 5.3.28+dfsg2-1 | Artistic-2.0, BSD-3-Clause, BSD-3-clause-fjord, GPL-2.0-or-later, GPL-3.0-only, MIT-old, MS-PL, Sleepycat, TCL-like, X11, Zlib | BSD-3-clause-fjord, MIT-old, TCL-like, X11 |
| `libdebconfclient0` | 0.270 | BSD-2-Clause, GPL-2.0-only, GPL-2.0-or-later |  |
| `libedit2` | 3.1-20221030-2 | BSD-3-Clause |  |
| `libext2fs2` | 1.47.0-2+b2 | Apache-2.0, BSD-3-Clause, GPL-2+ with Texinfo exception, GPL-2.0-only, GPL-2.0-or-later, ISC, Kazlib, LGPL-2.0-only, Latex2e, MIT-US-export | GPL-2+ with Texinfo exception, Kazlib, Latex2e, MIT-US-export |
| `libffi8` | 3.4.4-1 | GPL-2.0-or-later, GPL-3.0-or-later, LGPL-2.1-or-later, MIT, MPL-1.1, X11, public-domain | X11, public-domain |
| `libgcc-s1` | 12.2.0-14+deb12u1 | (not recorded) |  |
| `libgcrypt20` | 1.10.1-3+deb12u1 | GPL-2.0-only, LGPL-2.0-or-later |  |
| `libgdbm-compat4` | 1.23-3 | GFDL-1.3-no-invariants-or-later, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, GPL-3.0-or-later | GFDL-1.3-no-invariants-or-later |
| `libgdbm6` | 1.23-3 | GFDL-1.3-no-invariants-or-later, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, GPL-3.0-or-later | GFDL-1.3-no-invariants-or-later |
| `libgmp10` | 2:6.2.1+dfsg1-1.1 | GPL-2.0-only, GPL-2.0-or-later, GPL-3+ with Bison exception, GPL-3.0-only, GPL-3.0-or-later, LGPL-3.0-only, LGPL-3.0-or-later | GPL-3+ with Bison exception |
| `libgnutls30` | 3.7.9-2+deb12u7 | Apache-2.0, BSD-3-Clause, CC0-1.0, GFDL-1.3-only, GPL-2.0-or-later, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT | CC0-1.0, GFDL-1.3-only |
| `libgpg-error0` | 1.46-1 | BSD-3-Clause, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, g10-permissive | g10-permissive |
| `libgssapi-krb5-2` | 1.20.1-2+deb12u5 | GPL-2.0-only |  |
| `libhogweed6` | 3.8.1-2 | GAP, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-or-later, GPL-3.0-with-autoconf-exception+, LGPL-2.0-only, LGPL-2.0-or-later, LGPL-3.0-or-later, MIT, public-domain | GAP, GPL-3.0-with-autoconf-exception+, public-domain |
| `libicu72` | 72.1-3+deb12u1 | GPL-3.0-only, MIT |  |
| `libidn2-0` | 2.3.3-1+b1 | GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, GPL-3.0-or-later, LGPL-3.0-only, LGPL-3.0-or-later, Unicode | Unicode |
| `libjson-perl` | 4.10000-1 | Artistic-2.0, GPL-1.0-only, GPL-1.0-or-later | GPL-1.0-only, GPL-1.0-or-later |
| `libk5crypto3` | 1.20.1-2+deb12u5 | GPL-2.0-only |  |
| `libkeyutils1` | 1.6.3-2 | GPL-2.0-only, GPL-2.0-or-later, LGPL-2.0-only, LGPL-2.0-or-later |  |
| `libkrb5-3` | 1.20.1-2+deb12u5 | GPL-2.0-only |  |
| `libkrb5support0` | 1.20.1-2+deb12u5 | GPL-2.0-only |  |
| `libksba8` | 1.6.3-2 | FSFUL, GPL-3.0-only, LGPL-2.1-or-later | FSFUL |
| `libldap-2.5-0` | 2.5.13+dfsg-5 | BSD-3-Clause, BSD-3-clause-California, BSD-3-clause-variant, BSD-4-clause-California, Beerware, Expat-ISC, Expat-UNM, F5, FSF-unlimited, GPL-2+ with Libtool exception, GPL-2.0-only, GPL-2.0-or-later, GPL-2.0-with-autoconf-exception+, GPL-3+ with Libtool exception, GPL-3.0-only, GPL-3.0-or-later, GPL-3.0-with-autoconf-exception+, JCG, MIT, MIT-XC, NeoSoft-permissive, OpenLDAP-2.8, UMich, public-domain | BSD-3-clause-California, BSD-3-clause-variant, BSD-4-clause-California, Beerware, Expat-ISC, Expat-UNM, F5, FSF-unlimited, GPL-2+ with Libtool exception, GPL-2.0-with-autoconf-exception+, GPL-3+ with Libtool exception, GPL-3.0-with-autoconf-exception+, JCG, MIT-XC, NeoSoft-permissive, OpenLDAP-2.8, UMich, public-domain |
| `libllvm19` | 1:19.1.7-3~deb12u1 | APACHE-2-LLVM-EXCEPTIONS, Apache-2.0, BSD-3-Clause, MIT, Python-2.0, solar-public-domain | APACHE-2-LLVM-EXCEPTIONS, solar-public-domain |
| `liblz4-1` | 1.9.4-1 | BSD-2-Clause, GPL-2.0-only, GPL-2.0-or-later |  |
| `liblzma5` | 5.4.1-1+deb12u2 | Autoconf, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, LGPL-2.0-only, LGPL-2.1-only, LGPL-2.1-or-later, PD, PD-debian, config-h, noderivs, none, permissive-fsf, permissive-nowarranty, probably-PD | Autoconf, PD, PD-debian, config-h, noderivs, none, permissive-fsf, permissive-nowarranty, probably-PD |
| `libmd0` | 1.0.4-2 | BSD-2-Clause, BSD-2-Clause-NetBSD, BSD-3-Clause, BSD-3-clause-Aaron-D-Gifford, Beerware, ISC, public-domain-md4, public-domain-md5, public-domain-sha1 | BSD-2-Clause-NetBSD, BSD-3-clause-Aaron-D-Gifford, Beerware, public-domain-md4, public-domain-md5, public-domain-sha1 |
| `libmount1` | 2.38.1-5+deb12u3 | BSD-3-Clause, BSD-4-Clause, BSLA, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.0-only, LGPL-2.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, public-domain | BSD-4-Clause, BSLA, public-domain |
| `libncursesw6` | 6.4-4 | (not recorded) |  |
| `libnettle8` | 3.8.1-2 | GAP, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-or-later, GPL-3.0-with-autoconf-exception+, LGPL-2.0-only, LGPL-2.0-or-later, LGPL-3.0-or-later, MIT, public-domain | GAP, GPL-3.0-with-autoconf-exception+, public-domain |
| `libnpth0` | 1.6-3 | LGPL-2.1-only, LGPL-2.1-or-later |  |
| `libnss-wrapper` | 1.1.12-1 | BSD-3-clauses | BSD-3-clauses |
| `libp11-kit0` | 0.24.1-2 | Apache-2.0, BSD-3-Clause, ISC, ISC+IBM, LGPL-2.1-only, LGPL-2.1-or-later, permissive-like-automake-output, same-as-rest-of-p11kit | ISC+IBM, permissive-like-automake-output, same-as-rest-of-p11kit |
| `libpam-modules` | 1.5.2-6+deb12u2 | BSD-3-Clause, BSD-tcp_wrappers, Beerware, GPL-2.0-only, GPL-2.0-or-later, GPL-3+ with Bison exception, GPL-3.0-only, LGPL-2.0-only, LGPL-2.0-or-later, public-domain | BSD-tcp_wrappers, Beerware, GPL-3+ with Bison exception, public-domain |
| `libpam-modules-bin` | 1.5.2-6+deb12u2 | BSD-3-Clause, BSD-tcp_wrappers, Beerware, GPL-2.0-only, GPL-2.0-or-later, GPL-3+ with Bison exception, GPL-3.0-only, LGPL-2.0-only, LGPL-2.0-or-later, public-domain | BSD-tcp_wrappers, Beerware, GPL-3+ with Bison exception, public-domain |
| `libpam-runtime` | 1.5.2-6+deb12u2 | BSD-3-Clause, BSD-tcp_wrappers, Beerware, GPL-2.0-only, GPL-2.0-or-later, GPL-3+ with Bison exception, GPL-3.0-only, LGPL-2.0-only, LGPL-2.0-or-later, public-domain | BSD-tcp_wrappers, Beerware, GPL-3+ with Bison exception, public-domain |
| `libpam0g` | 1.5.2-6+deb12u2 | BSD-3-Clause, BSD-tcp_wrappers, Beerware, GPL-2.0-only, GPL-2.0-or-later, GPL-3+ with Bison exception, GPL-3.0-only, LGPL-2.0-only, LGPL-2.0-or-later, public-domain | BSD-tcp_wrappers, Beerware, GPL-3+ with Bison exception, public-domain |
| `libpcre2-8-0` | 10.42-1+deb12u2 | BSD-2-Clause, BSD-3-Clause, BSD-3-clause-Cambridge with BINARY LIBRARY-LIKE PACKAGES exception, X11, public-domain | BSD-3-clause-Cambridge with BINARY LIBRARY-LIKE PACKAGES exception, X11, public-domain |
| `libperl5.36` | 5.36.0-7+deb12u4 | Artistic-2, Artistic-2.0, Artistic-dist, BSD-3-Clause, BSD-3-clause-GENERIC, BSD-3-clause-with-weird-numbering, BSD-4-clause-POWERDOG, BZIP, CC0-1.0, DONT-CHANGE-THE-GPL, GPL-1.0-only, GPL-1.0-or-later, GPL-2.0-only, GPL-2.0-or-later, GPL-2.0-with-bison-exception+, HSIEH-BSD, HSIEH-DERIVATIVE, LGPL-2.1-only, MIT, REGCOMP, RRA-KEEP-THIS-NOTICE, SDBM-PUBLIC-DOMAIN, TEXT-TABS, Unicode, Zlib | Artistic-2, Artistic-dist, BSD-3-clause-GENERIC, BSD-3-clause-with-weird-numbering, BSD-4-clause-POWERDOG, BZIP, CC0-1.0, DONT-CHANGE-THE-GPL, GPL-1.0-only, GPL-1.0-or-later, GPL-2.0-with-bison-exception+, HSIEH-BSD, HSIEH-DERIVATIVE, REGCOMP, RRA-KEEP-THIS-NOTICE, SDBM-PUBLIC-DOMAIN, TEXT-TABS, Unicode |
| `libpq5` | 18.6-1.pgdg12+2 | Artistic-2.0, BSD-2-Clause, BSD-3-Clause, Custom-Unicode, Custom-pg_dump, Custom-regex, GPL-1.0-only, PostgreSQL, Tcl, double-metaphone, nagaysau-ishii | Custom-Unicode, Custom-pg_dump, Custom-regex, GPL-1.0-only, Tcl, double-metaphone, nagaysau-ishii |
| `libreadline8` | 8.2-1.3 | GFDL-1.3-no-invariants-or-later, GFDL-1.3-or-later, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, GPL-3.0-or-later, ISC-no-attribution | GFDL-1.3-no-invariants-or-later, GFDL-1.3-or-later, ISC-no-attribution |
| `libsasl2-2` | 2.1.28+dfsg-10+deb12u1 | BSD-2-Clause, BSD-2.2-clause, BSD-3-Clause, BSD-3-clause-JANET, BSD-3-clause-PADL, BSD-4-Clause, BSD-4-Clause-UC, BSD-4-clause-KTH, FSFULLR, GPL-3.0-only, GPL-3.0-or-later, MIT-CMU, MIT-Export, MIT-OpenVision, OpenLDAP, OpenSSL, RSA-MD, SSLeay, text://BSD-4-clause and IBM-as-is, text://IBM-as-is | BSD-2.2-clause, BSD-3-clause-JANET, BSD-3-clause-PADL, BSD-4-Clause, BSD-4-Clause-UC, BSD-4-clause-KTH, FSFULLR, MIT-CMU, MIT-Export, MIT-OpenVision, OpenLDAP, OpenSSL, RSA-MD, SSLeay, text://BSD-4-clause and IBM-as-is, text://IBM-as-is |
| `libsasl2-modules-db` | 2.1.28+dfsg-10+deb12u1 | BSD-2-Clause, BSD-2.2-clause, BSD-3-Clause, BSD-3-clause-JANET, BSD-3-clause-PADL, BSD-4-Clause, BSD-4-Clause-UC, BSD-4-clause-KTH, FSFULLR, GPL-3.0-only, GPL-3.0-or-later, MIT-CMU, MIT-Export, MIT-OpenVision, OpenLDAP, OpenSSL, RSA-MD, SSLeay, text://BSD-4-clause and IBM-as-is, text://IBM-as-is | BSD-2.2-clause, BSD-3-clause-JANET, BSD-3-clause-PADL, BSD-4-Clause, BSD-4-Clause-UC, BSD-4-clause-KTH, FSFULLR, MIT-CMU, MIT-Export, MIT-OpenVision, OpenLDAP, OpenSSL, RSA-MD, SSLeay, text://BSD-4-clause and IBM-as-is, text://IBM-as-is |
| `libseccomp2` | 2.5.4-1+deb12u1 | LGPL-2.1-only |  |
| `libselinux1` | 3.4-1+b6 | GPL-2.0-only, LGPL-2.1-only |  |
| `libsemanage-common` | 3.4-1 | GPL-2.0-or-later, LGPL-2.0-or-later |  |
| `libsemanage2` | 3.4-1+b5 | GPL-2.0-or-later, LGPL-2.0-or-later |  |
| `libsepol2` | 3.4-2.1 | GPL-2.0-only, GPL-2.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, Zlib |  |
| `libsmartcols1` | 2.38.1-5+deb12u3 | BSD-3-Clause, BSD-4-Clause, BSLA, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.0-only, LGPL-2.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, public-domain | BSD-4-Clause, BSLA, public-domain |
| `libsqlite3-0` | 3.40.1-2+deb12u2 | GPL-2.0-only, GPL-2.0-or-later, public-domain | public-domain |
| `libss2` | 1.47.0-2+b2 | Apache-2.0, BSD-3-Clause, GPL-2+ with Texinfo exception, GPL-2.0-only, GPL-2.0-or-later, ISC, Kazlib, LGPL-2.0-only, Latex2e, MIT-US-export | GPL-2+ with Texinfo exception, Kazlib, Latex2e, MIT-US-export |
| `libssl3` | 3.0.22-1~deb12u2 | Apache-2.0, Artistic-2.0, GPL-1.0-only, GPL-1.0-or-later | GPL-1.0-only, GPL-1.0-or-later |
| `libstdc++6` | 12.2.0-14+deb12u1 | (not recorded) |  |
| `libsystemd0` | 252.39-1~deb12u2 | CC0-1.0, GPL-2 with Linux-syscall-note exception, GPL-2.0-only, GPL-2.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, MIT, public-domain | CC0-1.0, GPL-2 with Linux-syscall-note exception, public-domain |
| `libtasn1-6` | 4.19.0-2+deb12u1 | GFDL-1.3-only, GPL-3.0-only, LGPL-2.0-or-later, LGPL-2.1-only | GFDL-1.3-only |
| `libtinfo6` | 6.4-4 | BSD-3-Clause, MIT/X11, X11 | MIT/X11, X11 |
| `libudev1` | 252.39-1~deb12u2 | CC0-1.0, GPL-2 with Linux-syscall-note exception, GPL-2.0-only, GPL-2.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, MIT, public-domain | CC0-1.0, GPL-2 with Linux-syscall-note exception, public-domain |
| `libunistring2` | 1.0-2 | FreeSoftware, GFDL-1.2-only, GFDL-1.2-or-later, GPL-2+ with distribution exception, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, GPL-3.0-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT | FreeSoftware, GFDL-1.2-only, GFDL-1.2-or-later, GPL-2+ with distribution exception |
| `libuuid1` | 2.38.1-5+deb12u3 | BSD-3-Clause, BSD-4-Clause, BSLA, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.0-only, LGPL-2.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, public-domain | BSD-4-Clause, BSLA, public-domain |
| `libxml2` | 2.9.14+dfsg-1.3~deb12u6 | ISC, MIT-1 | MIT-1 |
| `libxslt1.1` | 1.1.35-1+deb12u4 | (not recorded) |  |
| `libxxhash0` | 0.8.1-1 | BSD-2-Clause, GPL-2.0-only |  |
| `libz3-4` | 4.8.12-3.1 | GPL-2.0-only, GPL-2.0-or-later, MIT |  |
| `libzstd1` | 1.5.4+dfsg2-5 | BSD-3-Clause, GPL-2.0-only, MIT, Zlib |  |
| `locales` | 2.36-9+deb12u14 | GPL-2.0-only, LGPL-2.1-only |  |
| `login` | 1:4.13+dfsg1-1+deb12u2 | BSD-3-Clause, GPL-1.0-only, GPL-2.0-only, GPL-2.0-or-later, public-domain | GPL-1.0-only, public-domain |
| `logsave` | 1.47.0-2+b2 | Apache-2.0, BSD-3-Clause, GPL-2+ with Texinfo exception, GPL-2.0-only, GPL-2.0-or-later, ISC, Kazlib, LGPL-2.0-only, Latex2e, MIT-US-export | GPL-2+ with Texinfo exception, Kazlib, Latex2e, MIT-US-export |
| `mawk` | 1.3.4.20200120-3.1 | CC-BY-3.0, GPL-2.0-only, X11 | CC-BY-3.0, X11 |
| `mount` | 2.38.1-5+deb12u3 | BSD-3-Clause, BSD-4-Clause, BSLA, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.0-only, LGPL-2.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, public-domain | BSD-4-Clause, BSLA, public-domain |
| `ncurses-base` | 6.4-4 | BSD-3-Clause, MIT/X11, X11 | MIT/X11, X11 |
| `ncurses-bin` | 6.4-4 | BSD-3-Clause, MIT/X11, X11 | MIT/X11, X11 |
| `netbase` | 6.4 | GPL-2.0-only |  |
| `openssl` | 3.0.22-1~deb12u2 | Apache-2.0, Artistic-2.0, GPL-1.0-only, GPL-1.0-or-later | GPL-1.0-only, GPL-1.0-or-later |
| `passwd` | 1:4.13+dfsg1-1+deb12u2 | BSD-3-Clause, GPL-1.0-only, GPL-2.0-only, GPL-2.0-or-later, public-domain | GPL-1.0-only, public-domain |
| `perl` | 5.36.0-7+deb12u4 | Artistic-2, Artistic-2.0, Artistic-dist, BSD-3-Clause, BSD-3-clause-GENERIC, BSD-3-clause-with-weird-numbering, BSD-4-clause-POWERDOG, BZIP, CC0-1.0, DONT-CHANGE-THE-GPL, GPL-1.0-only, GPL-1.0-or-later, GPL-2.0-only, GPL-2.0-or-later, GPL-2.0-with-bison-exception+, HSIEH-BSD, HSIEH-DERIVATIVE, LGPL-2.1-only, MIT, REGCOMP, RRA-KEEP-THIS-NOTICE, SDBM-PUBLIC-DOMAIN, TEXT-TABS, Unicode, Zlib | Artistic-2, Artistic-dist, BSD-3-clause-GENERIC, BSD-3-clause-with-weird-numbering, BSD-4-clause-POWERDOG, BZIP, CC0-1.0, DONT-CHANGE-THE-GPL, GPL-1.0-only, GPL-1.0-or-later, GPL-2.0-with-bison-exception+, HSIEH-BSD, HSIEH-DERIVATIVE, REGCOMP, RRA-KEEP-THIS-NOTICE, SDBM-PUBLIC-DOMAIN, TEXT-TABS, Unicode |
| `perl-base` | 5.36.0-7+deb12u4 | Artistic-2, Artistic-2.0, Artistic-dist, BSD-3-Clause, BSD-3-clause-GENERIC, BSD-3-clause-with-weird-numbering, BSD-4-clause-POWERDOG, BZIP, CC0-1.0, DONT-CHANGE-THE-GPL, GPL-1.0-only, GPL-1.0-or-later, GPL-2.0-only, GPL-2.0-or-later, GPL-2.0-with-bison-exception+, HSIEH-BSD, HSIEH-DERIVATIVE, LGPL-2.1-only, MIT, REGCOMP, RRA-KEEP-THIS-NOTICE, SDBM-PUBLIC-DOMAIN, TEXT-TABS, Unicode, Zlib | Artistic-2, Artistic-dist, BSD-3-clause-GENERIC, BSD-3-clause-with-weird-numbering, BSD-4-clause-POWERDOG, BZIP, CC0-1.0, DONT-CHANGE-THE-GPL, GPL-1.0-only, GPL-1.0-or-later, GPL-2.0-with-bison-exception+, HSIEH-BSD, HSIEH-DERIVATIVE, REGCOMP, RRA-KEEP-THIS-NOTICE, SDBM-PUBLIC-DOMAIN, TEXT-TABS, Unicode |
| `perl-modules-5.36` | 5.36.0-7+deb12u4 | Artistic-2, Artistic-2.0, Artistic-dist, BSD-3-Clause, BSD-3-clause-GENERIC, BSD-3-clause-with-weird-numbering, BSD-4-clause-POWERDOG, BZIP, CC0-1.0, DONT-CHANGE-THE-GPL, GPL-1.0-only, GPL-1.0-or-later, GPL-2.0-only, GPL-2.0-or-later, GPL-2.0-with-bison-exception+, HSIEH-BSD, HSIEH-DERIVATIVE, LGPL-2.1-only, MIT, REGCOMP, RRA-KEEP-THIS-NOTICE, SDBM-PUBLIC-DOMAIN, TEXT-TABS, Unicode, Zlib | Artistic-2, Artistic-dist, BSD-3-clause-GENERIC, BSD-3-clause-with-weird-numbering, BSD-4-clause-POWERDOG, BZIP, CC0-1.0, DONT-CHANGE-THE-GPL, GPL-1.0-only, GPL-1.0-or-later, GPL-2.0-with-bison-exception+, HSIEH-BSD, HSIEH-DERIVATIVE, REGCOMP, RRA-KEEP-THIS-NOTICE, SDBM-PUBLIC-DOMAIN, TEXT-TABS, Unicode |
| `pinentry-curses` | 1.2.1-1 | GPL-2.0-only, GPL-2.0-or-later, LGPL-3.0-only, LGPL-3.0-or-later, X11 | X11 |
| `postgresql-16` | 16.15-1.pgdg12+2 | Artistic-2.0, BSD-2-Clause, BSD-3-Clause, Custom-Unicode, Custom-pg_dump, Custom-regex, GPL-1.0-only, PostgreSQL, Tcl, double-metaphone, nagaysau-ishii | Custom-Unicode, Custom-pg_dump, Custom-regex, GPL-1.0-only, Tcl, double-metaphone, nagaysau-ishii |
| `postgresql-client-16` | 16.15-1.pgdg12+2 | Artistic-2.0, BSD-2-Clause, BSD-3-Clause, Custom-Unicode, Custom-pg_dump, Custom-regex, GPL-1.0-only, PostgreSQL, Tcl, double-metaphone, nagaysau-ishii | Custom-Unicode, Custom-pg_dump, Custom-regex, GPL-1.0-only, Tcl, double-metaphone, nagaysau-ishii |
| `postgresql-client-common` | 293.pgdg12+1 | GPL-2.0-only, GPL-2.0-or-later |  |
| `postgresql-common` | 293.pgdg12+1 | GPL-2.0-only, GPL-2.0-or-later |  |
| `readline-common` | 8.2-1.3 | GFDL-1.3-no-invariants-or-later, GFDL-1.3-or-later, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, GPL-3.0-or-later, ISC-no-attribution | GFDL-1.3-no-invariants-or-later, GFDL-1.3-or-later, ISC-no-attribution |
| `sed` | 4.9-1+deb12u1 | BSD-4-Clause-UC, BSL-1, GFDL-1.3-no-invariants-or-later, GFDL-1.3-only, GPL-3.0-only, GPL-3.0-or-later, ISC, X11, pcre | BSD-4-Clause-UC, BSL-1, GFDL-1.3-no-invariants-or-later, GFDL-1.3-only, X11, pcre |
| `sensible-utils` | 0.0.17+nmu1 | All-permissive, GPL-2.0-only, GPL-2.0-or-later, configure, installsh | All-permissive, configure, installsh |
| `ssl-cert` | 1.1.2 | BSD-3-Clause |  |
| `stdlib` | v1.24.6 | (not recorded) |  |
| `sysvinit-utils` | 3.06-4 | GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only |  |
| `tar` | 1.34+dfsg-1.2+deb12u1 | GPL-2.0-only, GPL-2.0-or-later, GPL-3+ with Bison exception, GPL-3.0-only, GPL-3.0-or-later, LGPL-3.0-only, LGPL-3.0-or-later | GPL-3+ with Bison exception |
| `tzdata` | 2026f-0+deb12u1 | public-domain | public-domain |
| `ucf` | 3.0043+nmu1+deb12u1 | GPL-2.0-only |  |
| `usr-is-merged` | 37~deb12u1 | GPL-2.0-only, GPL-2.0-or-later |  |
| `util-linux` | 2.38.1-5+deb12u3 | BSD-3-Clause, BSD-4-Clause, BSLA, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.0-only, LGPL-2.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, public-domain | BSD-4-Clause, BSLA, public-domain |
| `util-linux-extra` | 2.38.1-5+deb12u3 | BSD-3-Clause, BSD-4-Clause, BSLA, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, GPL-3.0-or-later, LGPL-2.0-only, LGPL-2.0-or-later, LGPL-2.1-only, LGPL-2.1-or-later, LGPL-3.0-only, LGPL-3.0-or-later, MIT, public-domain | BSD-4-Clause, BSLA, public-domain |
| `xz-utils` | 5.4.1-1+deb12u2 | Autoconf, GPL-2.0-only, GPL-2.0-or-later, GPL-3.0-only, LGPL-2.0-only, LGPL-2.1-only, LGPL-2.1-or-later, PD, PD-debian, config-h, noderivs, none, permissive-fsf, permissive-nowarranty, probably-PD | Autoconf, PD, PD-debian, config-h, noderivs, none, permissive-fsf, permissive-nowarranty, probably-PD |
| `zlib1g` | 1:1.2.13.dfsg-1 | Zlib |  |
| `zstd` | 1.5.4+dfsg2-5 | BSD-3-Clause, GPL-2.0-only, MIT, Zlib |  |

## prom/prometheus:v3.13.5

| Gói | Phiên bản | Giấy phép | Ngoài OSI |
|---|---|---|---|
| `cloud.google.com/go/auth` | v0.20.0 | Apache-2.0 |  |
| `cloud.google.com/go/auth/oauth2adapt` | v0.2.8 | Apache-2.0 |  |
| `cloud.google.com/go/compute/metadata` | v0.9.0 | Apache-2.0 |  |
| `github.com/Azure/azure-sdk-for-go/sdk/azcore` | v1.22.0 | MIT |  |
| `github.com/Azure/azure-sdk-for-go/sdk/azidentity` | v1.14.0 | MIT |  |
| `github.com/Azure/azure-sdk-for-go/sdk/internal` | v1.12.0 | MIT |  |
| `github.com/Azure/azure-sdk-for-go/sdk/resourcemanager/compute/armcompute/v5` | v5.7.0 | MIT |  |
| `github.com/Azure/azure-sdk-for-go/sdk/resourcemanager/network/armnetwork/v4` | v4.3.0 | MIT |  |
| `github.com/AzureAD/microsoft-authentication-library-for-go` | v1.7.2 | MIT |  |
| `github.com/Code-Hex/go-generics-cache` | v1.5.1 | MIT |  |
| `github.com/KimMachineGun/automemlimit` | v0.7.5 | MIT |  |
| `github.com/alecthomas/kingpin/v2` | v2.4.0 | MIT |  |
| `github.com/alecthomas/units` | v0.0.0-20240927000941-0f3dac36c52b | MIT |  |
| `github.com/armon/go-metrics` | v0.4.1 | MIT |  |
| `github.com/aws/aws-sdk-go` | v1.55.8 | Apache-2.0, BSD-3-Clause |  |
| `github.com/aws/aws-sdk-go-v2` | v1.42.0 | Apache-2.0, BSD-3-Clause |  |
| `github.com/aws/aws-sdk-go-v2/config` | v1.32.25 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go-v2/credentials` | v1.19.24 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go-v2/feature/ec2/imds` | v1.18.29 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go-v2/internal/configsources` | v1.4.29 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go-v2/internal/endpoints/v2` | v2.7.29 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go-v2/internal/v4a` | v1.4.30 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go-v2/service/ec2` | v1.307.0 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go-v2/service/ecs` | v1.83.0 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go-v2/service/elasticache` | v1.54.3 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go-v2/service/internal/accept-encoding` | v1.13.12 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go-v2/service/internal/presigned-url` | v1.13.29 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go-v2/service/kafka` | v1.52.6 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go-v2/service/lightsail` | v1.56.1 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go-v2/service/rds` | v1.119.3 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go-v2/service/signin` | v1.2.0 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go-v2/service/sso` | v1.31.3 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go-v2/service/ssooidc` | v1.36.6 | Apache-2.0 |  |
| `github.com/aws/aws-sdk-go-v2/service/sts` | v1.43.3 | Apache-2.0 |  |
| `github.com/aws/smithy-go` | v1.27.2 | Apache-2.0, BSD-3-Clause |  |
| `github.com/bahlo/generic-list-go` | v0.2.0 | BSD-3-Clause |  |
| `github.com/bboreham/go-loser` | v0.0.0-20230920113527-fcc2c21820a3 | Apache-2.0 |  |
| `github.com/beorn7/perks` | v1.0.1 | MIT |  |
| `github.com/buger/jsonparser` | v1.1.2 | MIT |  |
| `github.com/cenkalti/backoff/v5` | v5.0.3 | MIT |  |
| `github.com/cespare/xxhash/v2` | v2.3.0 | MIT |  |
| `github.com/cncf/xds/go` | v0.0.0-20260202195803-dba9d589def2 | Apache-2.0 |  |
| `github.com/containerd/errdefs` | v1.0.0 | Apache-2.0 |  |
| `github.com/containerd/errdefs/pkg` | v0.3.0 | Apache-2.0 |  |
| `github.com/coreos/go-systemd/v22` | v22.7.0 | Apache-2.0 |  |
| `github.com/davecgh/go-spew` | v1.1.2-0.20180830191138-d8f796af33cc | ISC |  |
| `github.com/dennwc/varint` | v1.0.0 | MIT |  |
| `github.com/digitalocean/godo` | v1.196.0 | MIT, BSD-3-Clause |  |
| `github.com/distribution/reference` | v0.6.0 | Apache-2.0 |  |
| `github.com/docker/go-connections` | v0.7.0 | Apache-2.0 |  |
| `github.com/docker/go-units` | v0.5.0 | Apache-2.0 |  |
| `github.com/edsrzf/mmap-go` | v1.2.1-0.20241212181136-fad1cd13edbd | BSD-3-Clause |  |
| `github.com/emicklei/go-restful/v3` | v3.12.2 | MIT |  |
| `github.com/envoyproxy/go-control-plane/envoy` | v1.37.0 | Apache-2.0 |  |
| `github.com/envoyproxy/protoc-gen-validate` | v1.3.3 | Apache-2.0 |  |
| `github.com/facette/natsort` | v0.0.0-20181210072756-2cd4dd1e2dcb | BSD-3-Clause |  |
| `github.com/fatih/color` | v1.18.0 | MIT |  |
| `github.com/felixge/fgprof` | v0.9.5 | MIT |  |
| `github.com/felixge/httpsnoop` | v1.0.4 | MIT |  |
| `github.com/fsnotify/fsnotify` | v1.10.1 | BSD-3-Clause |  |
| `github.com/fxamacker/cbor/v2` | v2.9.0 | MIT |  |
| `github.com/go-logr/logr` | v1.4.4 | Apache-2.0 |  |
| `github.com/go-logr/stdr` | v1.2.2 | Apache-2.0 |  |
| `github.com/go-openapi/analysis` | v0.25.0 | Apache-2.0 |  |
| `github.com/go-openapi/errors` | v0.22.7 | Apache-2.0 |  |
| `github.com/go-openapi/jsonpointer` | v0.23.1 | Apache-2.0 |  |
| `github.com/go-openapi/jsonreference` | v0.21.5 | Apache-2.0 |  |
| `github.com/go-openapi/loads` | v0.23.3 | Apache-2.0 |  |
| `github.com/go-openapi/spec` | v0.22.4 | Apache-2.0 |  |
| `github.com/go-openapi/strfmt` | v0.26.3 | Apache-2.0 |  |
| `github.com/go-openapi/swag` | v0.26.0 | Apache-2.0 |  |
| `github.com/go-openapi/swag/cmdutils` | v0.26.0 | Apache-2.0 |  |
| `github.com/go-openapi/swag/conv` | v0.26.0 | Apache-2.0 |  |
| `github.com/go-openapi/swag/fileutils` | v0.26.0 | Apache-2.0 |  |
| `github.com/go-openapi/swag/jsonname` | v0.26.0 | Apache-2.0 |  |
| `github.com/go-openapi/swag/jsonutils` | v0.26.0 | Apache-2.0 |  |
| `github.com/go-openapi/swag/loading` | v0.26.0 | Apache-2.0 |  |
| `github.com/go-openapi/swag/mangling` | v0.26.0 | Apache-2.0 |  |
| `github.com/go-openapi/swag/netutils` | v0.26.0 | Apache-2.0 |  |
| `github.com/go-openapi/swag/stringutils` | v0.26.0 | Apache-2.0 |  |
| `github.com/go-openapi/swag/typeutils` | v0.26.0 | Apache-2.0 |  |
| `github.com/go-openapi/swag/yamlutils` | v0.26.0 | Apache-2.0 |  |
| `github.com/go-openapi/validate` | v0.25.2 | Apache-2.0 |  |
| `github.com/go-resty/resty/v2` | v2.17.2 | MIT |  |
| `github.com/go-viper/mapstructure/v2` | v2.5.0 | MIT |  |
| `github.com/go-zookeeper/zk` | v1.0.4 | BSD-3-Clause |  |
| `github.com/gobwas/glob` | v0.2.3 | MIT |  |
| `github.com/gogo/protobuf` | v1.3.2 | BSD-3-Clause |  |
| `github.com/golang-jwt/jwt/v5` | v5.3.1 | MIT |  |
| `github.com/golang/snappy` | v1.0.0 | BSD-3-Clause |  |
| `github.com/google/gnostic-models` | v0.7.0 | Apache-2.0 |  |
| `github.com/google/go-cmp` | v0.7.0 | BSD-3-Clause |  |
| `github.com/google/go-querystring` | v1.2.0 | BSD-3-Clause |  |
| `github.com/google/pprof` | v0.0.0-20260604005048-7023385849c0 | Apache-2.0, BSD-3-Clause |  |
| `github.com/google/s2a-go` | v0.1.9 | Apache-2.0 |  |
| `github.com/google/uuid` | v1.6.0 | BSD-3-Clause |  |
| `github.com/googleapis/enterprise-certificate-proxy` | v0.3.15 | Apache-2.0 |  |
| `github.com/googleapis/gax-go/v2` | v2.22.0 | BSD-3-Clause |  |
| `github.com/gophercloud/gophercloud/v2` | v2.12.0 | Apache-2.0 |  |
| `github.com/gorilla/websocket` | v1.5.4-0.20250319132907-e064f32e3674 | BSD-2-Clause |  |
| `github.com/grafana/regexp` | v0.0.0-20250905093917-f7b3be9d1853 | BSD-3-Clause |  |
| `github.com/grpc-ecosystem/grpc-gateway/v2` | v2.29.0 | BSD-3-Clause |  |
| `github.com/hashicorp/consul/api` | v1.32.1 | MPL-2.0 |  |
| `github.com/hashicorp/cronexpr` | v1.1.3 | Apache-2.0 |  |
| `github.com/hashicorp/errwrap` | v1.1.0 | MPL-2.0 |  |
| `github.com/hashicorp/go-cleanhttp` | v0.5.2 | MPL-2.0 |  |
| `github.com/hashicorp/go-hclog` | v1.6.3 | MIT |  |
| `github.com/hashicorp/go-immutable-radix` | v1.3.1 | MPL-2.0 |  |
| `github.com/hashicorp/go-multierror` | v1.1.1 | MPL-2.0 |  |
| `github.com/hashicorp/go-retryablehttp` | v0.7.8 | MPL-2.0 |  |
| `github.com/hashicorp/go-rootcerts` | v1.0.2 | MPL-2.0 |  |
| `github.com/hashicorp/go-version` | v1.9.0 | MPL-2.0 |  |
| `github.com/hashicorp/golang-lru` | v0.6.0 | MPL-2.0 |  |
| `github.com/hashicorp/nomad/api` | v0.0.0-20260616181215-ea1ca2d932bf | MPL-2.0 |  |
| `github.com/hashicorp/serf` | v0.10.1 | MPL-2.0 |  |
| `github.com/hetznercloud/hcloud-go/v2` | v2.43.0 | MIT |  |
| `github.com/ionos-cloud/sdk-go/v6` | v6.3.8 | Apache-2.0 |  |
| `github.com/jmespath/go-jmespath` | v0.4.0 | Apache-2.0 |  |
| `github.com/jpillora/backoff` | v1.0.0 | MIT |  |
| `github.com/json-iterator/go` | v1.1.12 | MIT |  |
| `github.com/julienschmidt/httprouter` | v1.3.0 | BSD-3-Clause |  |
| `github.com/klauspost/compress` | v1.18.7 | BSD-3-Clause, Apache-2.0, MIT |  |
| `github.com/knadh/koanf/maps` | v0.1.2 | MIT |  |
| `github.com/knadh/koanf/providers/confmap` | v1.0.0 | MIT |  |
| `github.com/knadh/koanf/v2` | v2.3.5 | MIT |  |
| `github.com/kolo/xmlrpc` | v0.0.0-20220921171641-a4b6fa1dd06b | MIT |  |
| `github.com/kylelemons/godebug` | v1.1.0 | Apache-2.0 |  |
| `github.com/linode/linodego` | v1.69.1 | MIT |  |
| `github.com/mattn/go-colorable` | v0.1.14 | MIT |  |
| `github.com/mattn/go-isatty` | v0.0.20 | MIT |  |
| `github.com/mdlayher/socket` | v0.4.1 | MIT |  |
| `github.com/mdlayher/vsock` | v1.2.1 | MIT |  |
| `github.com/miekg/dns` | v1.1.72 | BSD-3-Clause |  |
| `github.com/mitchellh/copystructure` | v1.2.0 | MIT |  |
| `github.com/mitchellh/mapstructure` | v1.5.0 | MIT |  |
| `github.com/mitchellh/reflectwalk` | v1.0.2 | MIT |  |
| `github.com/moby/docker-image-spec` | v1.3.1 | Apache-2.0 |  |
| `github.com/moby/moby/api` | v1.54.2 | Apache-2.0 |  |
| `github.com/moby/moby/client` | v0.4.1 | Apache-2.0 |  |
| `github.com/modern-go/concurrent` | v0.0.0-20180306012644-bacd9c7ef1dd | Apache-2.0 |  |
| `github.com/modern-go/reflect2` | v1.0.3-0.20250322232337-35a7c28c31ee | Apache-2.0 |  |
| `github.com/munnerz/goautoneg` | v0.0.0-20191010083416-a7dc8b61c822 | BSD-3-Clause |  |
| `github.com/mwitkow/go-conntrack` | v0.0.0-20190716064945-2f068394615f | Apache-2.0 |  |
| `github.com/nsf/jsondiff` | v0.0.0-20260207060731-8e8d90c4c0ac | MIT |  |
| `github.com/oklog/run` | v1.2.0 | Apache-2.0 |  |
| `github.com/oklog/ulid/v2` | v2.1.1 | Apache-2.0 |  |
| `github.com/open-telemetry/opentelemetry-collector-contrib/internal/exp/metrics` | v0.154.0 | Apache-2.0 |  |
| `github.com/open-telemetry/opentelemetry-collector-contrib/pkg/pdatautil` | v0.154.0 | Apache-2.0 |  |
| `github.com/open-telemetry/opentelemetry-collector-contrib/processor/deltatocumulativeprocessor` | v0.154.0 | Apache-2.0 |  |
| `github.com/opencontainers/go-digest` | v1.0.0 | Apache-2.0, CC-BY-SA-4.0 | CC-BY-SA-4.0 |
| `github.com/opencontainers/image-spec` | v1.1.1 | Apache-2.0 |  |
| `github.com/outscale/osc-sdk-go/v2` | v2.34.0 | BSD-3-Clause |  |
| `github.com/ovh/go-ovh` | v1.9.0 | BSD-3-Clause |  |
| `github.com/pb33f/jsonpath` | v0.8.2 | Apache-2.0 |  |
| `github.com/pb33f/libopenapi` | v0.37.2 | MIT |  |
| `github.com/pb33f/ordered-map/v2` | v2.3.1 | Apache-2.0 |  |
| `github.com/pbnjay/memory` | v0.0.0-20210728143218-7b4eea64cf58 | BSD-3-Clause |  |
| `github.com/pkg/browser` | v0.0.0-20240102092130-5ac0b6a4141c | BSD-2-Clause |  |
| `github.com/pmezard/go-difflib` | v1.0.1-0.20181226105442-5d4384ee4fb2 | BSD-3-Clause |  |
| `github.com/prometheus/alertmanager` | v0.33.0 | Apache-2.0 |  |
| `github.com/prometheus/client_golang` | v1.23.2 | Apache-2.0, BSD-3-Clause |  |
| `github.com/prometheus/client_golang/exp` | v0.0.0-20260602051030-3537b20ac86b | Apache-2.0 |  |
| `github.com/prometheus/client_model` | v0.6.2 | Apache-2.0 |  |
| `github.com/prometheus/common` | v0.69.0 | Apache-2.0 |  |
| `github.com/prometheus/common/assets` | v0.2.0 | Apache-2.0 |  |
| `github.com/prometheus/exporter-toolkit` | v0.16.0 | Apache-2.0 |  |
| `github.com/prometheus/otlptranslator` | v1.0.0 | Apache-2.0 |  |
| `github.com/prometheus/procfs` | v0.16.1 | Apache-2.0 |  |
| `github.com/prometheus/prometheus` | v0.0.0-20261021102946-f497435984d1+dirty | (not recorded) |  |
| `github.com/prometheus/sigv4` | v0.4.1 | Apache-2.0 |  |
| `github.com/puzpuzpuz/xsync/v4` | v4.5.0 | Apache-2.0 |  |
| `github.com/scaleway/scaleway-sdk-go` | v1.0.0-beta.36 | Apache-2.0 |  |
| `github.com/spf13/pflag` | v1.0.10 | BSD-3-Clause |  |
| `github.com/stackitcloud/stackit-sdk-go/core` | v0.26.0 | Apache-2.0 |  |
| `github.com/stretchr/testify` | v1.11.1 | MIT |  |
| `github.com/vultr/govultr/v3` | v3.31.2 | MIT |  |
| `github.com/x448/float16` | v0.8.4 | MIT |  |
| `github.com/xhit/go-str2duration/v2` | v2.1.0 | BSD-3-Clause |  |
| `go.opentelemetry.io/auto/sdk` | v1.2.1 | Apache-2.0 |  |
| `go.opentelemetry.io/collector/component` | v1.60.0 | Apache-2.0 |  |
| `go.opentelemetry.io/collector/confmap` | v1.60.0 | Apache-2.0 |  |
| `go.opentelemetry.io/collector/confmap/xconfmap` | v0.154.0 | Apache-2.0 |  |
| `go.opentelemetry.io/collector/consumer` | v1.60.0 | Apache-2.0 |  |
| `go.opentelemetry.io/collector/featuregate` | v1.60.0 | Apache-2.0 |  |
| `go.opentelemetry.io/collector/internal/componentalias` | v0.154.0 | Apache-2.0 |  |
| `go.opentelemetry.io/collector/pdata` | v1.60.0 | Apache-2.0 |  |
| `go.opentelemetry.io/collector/pipeline` | v1.60.0 | Apache-2.0 |  |
| `go.opentelemetry.io/collector/processor` | v1.60.0 | Apache-2.0 |  |
| `go.opentelemetry.io/contrib/instrumentation/net/http/httptrace/otelhttptrace` | v0.69.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp` | v0.69.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/otel` | v1.45.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/otel/exporters/otlp/otlptrace` | v1.45.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc` | v1.45.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp` | v1.45.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/otel/metric` | v1.45.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/otel/sdk` | v1.45.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/otel/trace` | v1.45.0 | Apache-2.0, BSD-3-Clause |  |
| `go.opentelemetry.io/proto/otlp` | v1.11.0 | Apache-2.0 |  |
| `go.uber.org/atomic` | v1.11.0 | MIT |  |
| `go.uber.org/automaxprocs` | v1.6.0 | MIT |  |
| `go.uber.org/goleak` | v1.3.0 | MIT |  |
| `go.uber.org/multierr` | v1.11.0 | MIT |  |
| `go.uber.org/zap` | v1.28.0 | MIT |  |
| `go.yaml.in/yaml/v2` | v2.4.4 | Apache-2.0 |  |
| `go.yaml.in/yaml/v3` | v3.0.4 | MIT, Apache-2.0 |  |
| `go.yaml.in/yaml/v4` | v4.0.0-rc.5 | Apache-2.0 |  |
| `golang.org/x/crypto` | v0.57.0 | BSD-3-Clause |  |
| `golang.org/x/exp` | v0.0.0-20260218203240-3dfff04db8fa | BSD-3-Clause |  |
| `golang.org/x/net` | v0.60.0 | BSD-3-Clause |  |
| `golang.org/x/oauth2` | v0.36.0 | BSD-3-Clause |  |
| `golang.org/x/sync` | v0.23.0 | BSD-3-Clause |  |
| `golang.org/x/sys` | v0.48.0 | BSD-3-Clause |  |
| `golang.org/x/term` | v0.46.0 | BSD-3-Clause |  |
| `golang.org/x/text` | v0.42.0 | BSD-3-Clause |  |
| `golang.org/x/time` | v0.15.0 | BSD-3-Clause |  |
| `google.golang.org/api` | v0.278.0 | BSD-3-Clause |  |
| `google.golang.org/genproto/googleapis/api` | v0.0.0-20260803160001-6ac0973c030d | Apache-2.0 |  |
| `google.golang.org/genproto/googleapis/rpc` | v0.0.0-20260803160001-6ac0973c030d | Apache-2.0 |  |
| `google.golang.org/grpc` | v1.83.1 | Apache-2.0 |  |
| `google.golang.org/protobuf` | v1.36.11 | BSD-3-Clause |  |
| `gopkg.in/evanphx/json-patch.v4` | v4.13.0 | BSD-3-Clause |  |
| `gopkg.in/inf.v0` | v0.9.1 | BSD-3-Clause |  |
| `gopkg.in/ini.v1` | v1.67.2 | Apache-2.0 |  |
| `gopkg.in/yaml.v2` | v2.4.0 | Apache-2.0 |  |
| `gopkg.in/yaml.v3` | v3.0.1 | MIT, Apache-2.0 |  |
| `k8s.io/api` | v0.35.3 | Apache-2.0 |  |
| `k8s.io/apimachinery` | v0.35.3 | Apache-2.0, BSD-3-Clause |  |
| `k8s.io/client-go` | v0.35.3 | Apache-2.0, BSD-3-Clause |  |
| `k8s.io/klog` | v1.0.0 | Apache-2.0 |  |
| `k8s.io/klog/v2` | v2.140.0 | Apache-2.0 |  |
| `k8s.io/kube-openapi` | v0.0.0-20250910181357-589584f1c912 | Apache-2.0, BSD-3-Clause, MIT |  |
| `k8s.io/utils` | v0.0.0-20251002143259-bc988d571ff4 | Apache-2.0, BSD-3-Clause |  |
| `sigs.k8s.io/json` | v0.0.0-20250730193827-2d320260d730 | Apache-2.0, BSD-3-Clause |  |
| `sigs.k8s.io/randfill` | v1.0.0 | Apache-2.0 |  |
| `sigs.k8s.io/structured-merge-diff/v6` | v6.3.0 | Apache-2.0 |  |
| `sigs.k8s.io/yaml` | v1.6.0 | MIT, BSD-3-Clause, Apache-2.0 |  |
| `stdlib` | v1.26.10 | (not recorded) |  |

## python:3.12-alpine

| Gói | Phiên bản | Giấy phép | Ngoài OSI |
|---|---|---|---|
| `.python-rundeps` | 20261105.193119 | (not recorded) |  |
| `alpine-baselayout` | 3.7.2-r1 | GPL-2.0-only |  |
| `alpine-baselayout-data` | 3.7.2-r1 | GPL-2.0-only |  |
| `alpine-keys` | 2.6-r0 | MIT |  |
| `alpine-release` | 3.24.4-r0 | MIT |  |
| `apk-tools` | 3.0.9-r0 | GPL-2.0-only |  |
| `busybox` | 1.37.0-r31 | GPL-2.0-only |  |
| `busybox-binsh` | 1.37.0-r31 | GPL-2.0-only |  |
| `ca-certificates` | 20261020-r0 | MIT, MPL-2.0 |  |
| `ca-certificates-bundle` | 20261020-r0 | MIT, MPL-2.0 |  |
| `gdbm` | 1.26-r0 | GPL-3.0-or-later |  |
| `keyutils-libs` | 1.6.3-r4 | GPL-2.0-or-later, LGPL-2.0-or-later |  |
| `krb5-conf` | 1.0-r2 | MIT |  |
| `krb5-libs` | 1.22.2-r1 | MIT |  |
| `libapk` | 3.0.9-r0 | GPL-2.0-only |  |
| `libbz2` | 1.0.8-r6 | bzip-2-1.0.6 | bzip-2-1.0.6 |
| `libcom_err` | 1.47.4-r0 | BSD-3-Clause, GPL-2.0-or-later, LGPL-2.0-or-later, MIT |  |
| `libcrypto3` | 3.5.9-r0 | Apache-2.0 |  |
| `libffi` | 3.5.2-r1 | MIT |  |
| `libintl` | 1.0-r0 | LGPL-2.1-or-later |  |
| `libncursesw` | 6.6_p20260516-r0 | X-11 | X-11 |
| `libnsl` | 2.0.1-r2 | LGPL-2.0-or-later |  |
| `libpanelw` | 6.6_p20260516-r0 | X-11 | X-11 |
| `libssl3` | 3.5.9-r0 | Apache-2.0 |  |
| `libtirpc` | 1.3.5-r1 | BSD-3-Clause |  |
| `libtirpc-conf` | 1.3.5-r1 | BSD-3-Clause |  |
| `libuuid` | 2.42.3-r1 | BSD-3-Clause |  |
| `libverto` | 0.3.2-r2 | MIT |  |
| `musl` | 1.2.6-r3 | MIT |  |
| `musl-utils` | 1.2.6-r3 | BSD-2-Clause, GPL-2.0-or-later, MIT |  |
| `ncurses-terminfo-base` | 6.6_p20260516-r0 | X-11 | X-11 |
| `pip` | 25.0.1 | MIT |  |
| `readline` | 8.3.3-r1 | GPL-3.0-or-later |  |
| `scanelf` | 1.3.9-r1 | GPL-2.0-only |  |
| `sqlite-libs` | 3.53.4-r0 | blessing | blessing |
| `ssl_client` | 1.37.0-r31 | GPL-2.0-only |  |
| `tzdata` | 2026f-r0 | Public-Domain | Public-Domain |
| `xz-libs` | 5.8.4-r0 | 0BSD, GPL-2.0-or-later, LGPL-2.1-or-later, Public-Domain | Public-Domain |
| `zlib` | 1.3.2-r1 | Zlib |  |

## python:3.13.18-alpine3.24

| Gói | Phiên bản | Giấy phép | Ngoài OSI |
|---|---|---|---|
| `.python-rundeps` | 20261105.193026 | (not recorded) |  |
| `CacheControl` | 0.14.4 | (not recorded) |  |
| `alpine-baselayout` | 3.7.2-r1 | GPL-2.0-only |  |
| `alpine-baselayout-data` | 3.7.2-r1 | GPL-2.0-only |  |
| `alpine-keys` | 2.6-r0 | MIT |  |
| `alpine-release` | 3.24.4-r0 | MIT |  |
| `apk-tools` | 3.0.9-r0 | GPL-2.0-only |  |
| `busybox` | 1.37.0-r31 | GPL-2.0-only |  |
| `busybox-binsh` | 1.37.0-r31 | GPL-2.0-only |  |
| `ca-certificates` | 20261020-r0 | MIT, MPL-2.0 |  |
| `ca-certificates-bundle` | 20261020-r0 | MIT, MPL-2.0 |  |
| `certifi` | 2026.10.19 | (not recorded) |  |
| `distlib` | 0.4.2 | (not recorded) |  |
| `distro` | 1.9.0 | (not recorded) |  |
| `gdbm` | 1.26-r0 | GPL-3.0-or-later |  |
| `idna` | 3.18 | (not recorded) |  |
| `libapk` | 3.0.9-r0 | GPL-2.0-only |  |
| `libbz2` | 1.0.8-r6 | bzip-2-1.0.6 | bzip-2-1.0.6 |
| `libcrypto3` | 3.5.9-r0 | Apache-2.0 |  |
| `libffi` | 3.5.2-r1 | MIT |  |
| `libncursesw` | 6.6_p20260516-r0 | X-11 | X-11 |
| `libpanelw` | 6.6_p20260516-r0 | X-11 | X-11 |
| `libssl3` | 3.5.9-r0 | Apache-2.0 |  |
| `libuuid` | 2.42.3-r1 | BSD-3-Clause |  |
| `msgpack` | 1.1.2 | (not recorded) |  |
| `musl` | 1.2.6-r3 | MIT |  |
| `musl-utils` | 1.2.6-r3 | BSD-2-Clause, GPL-2.0-or-later, MIT |  |
| `ncurses-terminfo-base` | 6.6_p20260516-r0 | X-11 | X-11 |
| `packaging` | 26.2 | (not recorded) |  |
| `pip` | 26.2.2 | MIT |  |
| `platformdirs` | 4.10.0 | (not recorded) |  |
| `pygments` | 2.20.0 | (not recorded) |  |
| `pyproject-hooks` | 1.2.0 | (not recorded) |  |
| `readline` | 8.3.3-r1 | GPL-3.0-or-later |  |
| `requests` | 2.34.2 | (not recorded) |  |
| `resolvelib` | 1.2.1 | (not recorded) |  |
| `rich` | 14.2.0 | (not recorded) |  |
| `scanelf` | 1.3.9-r1 | GPL-2.0-only |  |
| `setuptools` | 70.3.0 | (not recorded) |  |
| `sqlite-libs` | 3.53.4-r0 | blessing | blessing |
| `ssl_client` | 1.37.0-r31 | GPL-2.0-only |  |
| `tomli` | 2.4.1 | (not recorded) |  |
| `tomli-w` | 1.2.0 | (not recorded) |  |
| `truststore` | 0.10.4 | (not recorded) |  |
| `tzdata` | 2026f-r0 | Public-Domain | Public-Domain |
| `urllib3` | 2.7.0 | (not recorded) |  |
| `xz-libs` | 5.8.4-r0 | 0BSD, GPL-2.0-or-later, LGPL-2.1-or-later, Public-Domain | Public-Domain |
| `zlib` | 1.3.2-r1 | Zlib |  |

## restic/restic:0.17.3

| Gói | Phiên bản | Giấy phép | Ngoài OSI |
|---|---|---|---|
| `alpine-baselayout` | 3.6.5-r0 | GPL-2.0-only |  |
| `alpine-baselayout-data` | 3.6.5-r0 | GPL-2.0-only |  |
| `alpine-keys` | 2.4-r1 | MIT |  |
| `apk-tools` | 2.14.4-r0 | GPL-2.0-only |  |
| `busybox` | 1.36.1-r29 | GPL-2.0-only |  |
| `busybox-binsh` | 1.36.1-r29 | GPL-2.0-only |  |
| `ca-certificates` | 20240705-r0 | MIT, MPL-2.0 |  |
| `ca-certificates-bundle` | 20240705-r0 | MIT, MPL-2.0 |  |
| `cloud.google.com/go` | v0.115.0 | Apache-2.0, BSD-3-Clause |  |
| `cloud.google.com/go/auth` | v0.6.1 | Apache-2.0 |  |
| `cloud.google.com/go/auth/oauth2adapt` | v0.2.2 | Apache-2.0 |  |
| `cloud.google.com/go/compute/metadata` | v0.3.0 | Apache-2.0 |  |
| `cloud.google.com/go/iam` | v1.1.8 | Apache-2.0 |  |
| `cloud.google.com/go/storage` | v1.43.0 | Apache-2.0 |  |
| `fuse` | 2.9.9-r5 | GPL-2.0-only, LGPL-2.1-only |  |
| `fuse-common` | 3.16.2-r0 | GPL-2.0-only, LGPL-2.1-only |  |
| `github.com/Azure/azure-sdk-for-go/sdk/azcore` | v1.12.0 | MIT |  |
| `github.com/Azure/azure-sdk-for-go/sdk/azidentity` | v1.7.0 | MIT |  |
| `github.com/Azure/azure-sdk-for-go/sdk/internal` | v1.9.0 | MIT |  |
| `github.com/Azure/azure-sdk-for-go/sdk/storage/azblob` | v1.3.2 | MIT |  |
| `github.com/AzureAD/microsoft-authentication-library-for-go` | v1.2.2 | MIT |  |
| `github.com/Backblaze/blazer` | v0.6.1 | Apache-2.0 |  |
| `github.com/anacrolix/fuse` | v0.3.1 | BSD-3-Clause, HPND, BSD-2-Clause |  |
| `github.com/cenkalti/backoff/v4` | v4.3.0 | MIT |  |
| `github.com/cespare/xxhash/v2` | v2.3.0 | MIT |  |
| `github.com/cpuguy83/go-md2man/v2` | v2.0.4 | MIT |  |
| `github.com/elithrar/simple-scrypt` | v1.3.0 | MIT |  |
| `github.com/felixge/httpsnoop` | v1.0.4 | MIT |  |
| `github.com/go-logr/logr` | v1.4.1 | Apache-2.0 |  |
| `github.com/go-logr/stdr` | v1.2.2 | Apache-2.0 |  |
| `github.com/golang-jwt/jwt/v5` | v5.2.1 | MIT |  |
| `github.com/golang/groupcache` | v0.0.0-20210331224755-41bb18bfe9da | Apache-2.0 |  |
| `github.com/golang/protobuf` | v1.5.4 | BSD-3-Clause |  |
| `github.com/google/s2a-go` | v0.1.7 | Apache-2.0 |  |
| `github.com/google/uuid` | v1.6.0 | BSD-3-Clause |  |
| `github.com/googleapis/enterprise-certificate-proxy` | v0.3.2 | Apache-2.0 |  |
| `github.com/googleapis/gax-go/v2` | v2.12.5 | BSD-3-Clause |  |
| `github.com/hashicorp/golang-lru/v2` | v2.0.7 | MPL-2.0 |  |
| `github.com/json-iterator/go` | v1.1.12 | MIT |  |
| `github.com/klauspost/compress` | v1.17.9 | BSD-3-Clause, Apache-2.0, MIT |  |
| `github.com/klauspost/cpuid/v2` | v2.2.6 | MIT |  |
| `github.com/kr/fs` | v0.1.0 | BSD-3-Clause |  |
| `github.com/kylelemons/godebug` | v1.1.0 | Apache-2.0 |  |
| `github.com/minio/md5-simd` | v1.1.2 | Apache-2.0 |  |
| `github.com/minio/minio-go/v7` | v7.0.66 | Apache-2.0 |  |
| `github.com/minio/sha256-simd` | v1.0.1 | Apache-2.0 |  |
| `github.com/modern-go/concurrent` | v0.0.0-20180306012644-bacd9c7ef1dd | Apache-2.0 |  |
| `github.com/modern-go/reflect2` | v1.0.2 | Apache-2.0 |  |
| `github.com/ncw/swift/v2` | v2.0.2 | MIT |  |
| `github.com/peterbourgon/unixtransport` | v0.0.4 | Apache-2.0 |  |
| `github.com/pkg/browser` | v0.0.0-20240102092130-5ac0b6a4141c | BSD-2-Clause |  |
| `github.com/pkg/errors` | v0.9.1 | BSD-2-Clause |  |
| `github.com/pkg/sftp` | v1.13.6 | BSD-2-Clause |  |
| `github.com/pkg/xattr` | v0.4.10 | BSD-2-Clause |  |
| `github.com/restic/chunker` | v0.4.0 | BSD-2-Clause |  |
| `github.com/restic/restic` |  | (not recorded) |  |
| `github.com/rs/xid` | v1.5.0 | MIT |  |
| `github.com/russross/blackfriday/v2` | v2.1.0 | BSD-2-Clause |  |
| `github.com/spf13/cobra` | v1.8.1 | Apache-2.0 |  |
| `github.com/spf13/pflag` | v1.0.5 | BSD-3-Clause |  |
| `go.opencensus.io` | v0.24.0 | Apache-2.0 |  |
| `go.opentelemetry.io/contrib/instrumentation/google.golang.org/grpc/otelgrpc` | v0.49.0 | Apache-2.0 |  |
| `go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp` | v0.49.0 | Apache-2.0 |  |
| `go.opentelemetry.io/otel` | v1.24.0 | Apache-2.0 |  |
| `go.opentelemetry.io/otel/metric` | v1.24.0 | Apache-2.0 |  |
| `go.opentelemetry.io/otel/trace` | v1.24.0 | Apache-2.0 |  |
| `go.uber.org/automaxprocs` | v1.5.3 | MIT |  |
| `golang.org/x/crypto` | v0.24.0 | BSD-3-Clause |  |
| `golang.org/x/net` | v0.26.0 | BSD-3-Clause |  |
| `golang.org/x/oauth2` | v0.21.0 | BSD-3-Clause |  |
| `golang.org/x/sync` | v0.7.0 | BSD-3-Clause |  |
| `golang.org/x/sys` | v0.22.0 | BSD-3-Clause |  |
| `golang.org/x/term` | v0.22.0 | BSD-3-Clause |  |
| `golang.org/x/text` | v0.16.0 | BSD-3-Clause |  |
| `golang.org/x/time` | v0.5.0 | BSD-3-Clause |  |
| `google.golang.org/api` | v0.187.0 | BSD-3-Clause |  |
| `google.golang.org/genproto` | v0.0.0-20240624140628-dc46fd24d27d | Apache-2.0 |  |
| `google.golang.org/genproto/googleapis/api` | v0.0.0-20240617180043-68d350f18fd4 | Apache-2.0 |  |
| `google.golang.org/genproto/googleapis/rpc` | v0.0.0-20240624140628-dc46fd24d27d | Apache-2.0 |  |
| `google.golang.org/grpc` | v1.64.1 | Apache-2.0 |  |
| `google.golang.org/protobuf` | v1.34.2 | BSD-3-Clause |  |
| `gopkg.in/ini.v1` | v1.67.0 | Apache-2.0 |  |
| `gopkg.in/yaml.v3` | v3.0.1 | MIT, Apache-2.0 |  |
| `jq` | 1.7.1-r0 | MIT |  |
| `libcrypto3` | 3.3.2-r0 | Apache-2.0 |  |
| `libedit` | 20240517.3.1-r0 | BSD-3-Clause |  |
| `libncursesw` | 6.4_p20240420-r1 | X-11 | X-11 |
| `libssl3` | 3.3.2-r0 | Apache-2.0 |  |
| `musl` | 1.2.5-r0 | MIT |  |
| `musl-utils` | 1.2.5-r0 | BSD-2-Clause, GPL-2.0-or-later, MIT |  |
| `ncurses-terminfo-base` | 6.4_p20240420-r1 | X-11 | X-11 |
| `oniguruma` | 6.9.9-r0 | BSD-2-Clause |  |
| `openssh-client-common` | 9.7_p1-r4 | SSH-OpenSSH | SSH-OpenSSH |
| `openssh-client-default` | 9.7_p1-r4 | SSH-OpenSSH | SSH-OpenSSH |
| `openssh-keygen` | 9.7_p1-r4 | SSH-OpenSSH | SSH-OpenSSH |
| `scanelf` | 1.3.7-r2 | GPL-2.0-only |  |
| `ssl_client` | 1.36.1-r29 | GPL-2.0-only |  |
| `stdlib` | v1.23.3 | (not recorded) |  |
| `tzdata` | 2024b-r0 | Public-Domain | Public-Domain |
| `zlib` | 1.3.1-r1 | Zlib |  |
