# SBOM

Danh mục thành phần phần mềm theo định dạng CycloneDX (JSON, đặc tả 1.6).
Chỉ gồm dependency chạy thật, không gồm scope test và dev.
Giấy phép của từng thành phần ở [`DEPENDENCIES.txt`](../DEPENDENCIES.txt).

## Các tệp

| Tệp | Nguồn |
|---|---|
| `backend.cdx.json` | Maven, `backend` (gốc `dx-os-parent`, cả ba module `dx-core`, `dx-engine`, `dx-monitor`) |
| `frontend.cdx.json` | npm, `frontend` |
| `website.cdx.json` | npm, `website` (trang tài liệu) |

Các tệp chỉ gồm thư viện của mã do nhóm viết.
Image bên thứ ba khai báo ở [`.github/component-catalog.json`](../.github/component-catalog.json), giấy phép của chúng ở mục 4 của [`DEPENDENCIES.txt`](../DEPENDENCIES.txt), và `deploy/docker-compose.yml` ghim phiên bản và digest.

## Sinh lại

Sinh lại sau khi đổi dependency hoặc số phiên bản của dự án.

```bash
# Backend
cd backend
./mvnw org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom -DoutputFormat=json -DoutputName=backend.cdx -DincludeTestScope=false
cp target/backend.cdx.json ../sbom/

# Frontend
cd frontend && npx @cyclonedx/cyclonedx-npm --omit dev --output-format JSON --output-file ../sbom/frontend.cdx.json

# Website
cd website && npx @cyclonedx/cyclonedx-npm --omit dev --output-format JSON --output-file ../sbom/website.cdx.json
```

Backend sinh lại bằng Maven vì `pom.xml` không ghi phiên bản của các gói do Spring Boot quản lý.

## Kiểm tra tự động

| Kiểm gì | Ở đâu | Khi nào |
|---|---|---|
| Mọi thành phần có giấy phép nằm trong danh sách OSI; ngoại lệ có lý do ở `.github/dependency-exceptions.json` | `.github/scripts/check_licenses.py`, job `Check licenses` của workflow `Compliance` | Mỗi pull request |
| Giấy phép của gói trong SBOM sinh lại từ pull request, trước khi `sbom/` được sinh lại | `check_licenses.py sbom <tệp>`, job `Check SBOMs against lockfiles` của `Compliance` (frontend, website) và `Scan the backend SBOM` của `Dependencies` (backend) | Mỗi pull request |
| SBOM của `frontend` và `website` khớp tệp khóa | `.github/scripts/check_sbom_fresh.py`, job `Check SBOMs against lockfiles` của `Compliance` | Mỗi pull request |
| SBOM của `backend` khớp các gói Maven đã giải | `check_sbom_fresh.py`, job `Scan the backend SBOM` của workflow `Dependencies`, sinh lại SBOM rồi so | Khi file khai báo đổi và mỗi tuần |
| Lỗ hổng HIGH hoặc CRITICAL đã có bản vá chặn CI | Trivy, job `Scan dependencies` và `Scan the backend SBOM` của `Dependencies` | Khi file khai báo đổi và mỗi tuần |
| Lỗ hổng của image bên thứ ba trong `deploy/docker-compose.yml`, chỉ báo bằng cảnh báo | Job `Scan third-party images` của `Dependencies` | Khi file khai báo đổi và mỗi tuần |
| Cập nhật phiên bản | Dependabot (`.github/dependabot.yml`) mở pull request vào `Khuong/main` | Theo lịch |

## Phát hành

Workflow `Release Package` đính kèm các tệp này vào GitHub Release.
