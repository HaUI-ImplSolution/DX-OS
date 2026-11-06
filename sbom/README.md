# SBOM

Danh mục thành phần phần mềm định dạng CycloneDX (JSON; đặc tả 1.6), chỉ gồm dependency chạy thật (không gồm scope test và dev).

| File | Nguồn |
|---|---|
| `backend.cdx.json` | Maven, `backend` (gốc `dx-os-parent`, cả ba module `dx-core`, `dx-engine`, `dx-monitor`) |
| `frontend.cdx.json` | npm, `frontend` |
| `website.cdx.json` | npm, `website` (trang tài liệu) |

## Sinh lại

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

Sinh lại sau khi đổi dependency hoặc số phiên bản của dự án; giấy phép từng thành phần ở [`DEPENDENCIES.txt`](../DEPENDENCIES.txt).
Mọi thành phần trong các file này đều có giấy phép; `.github/scripts/check_licenses.py` (job `Check licenses` của workflow `Compliance`) chặn thành phần không có giấy phép hoặc giấy phép ngoài danh sách cho phép, trừ ngoại lệ có lý do ở `.github/dependency-exceptions.json`.
Các file SBOM chỉ gồm thư viện của mã do nhóm viết; image của bên thứ ba khai báo ở [`.github/component-catalog.json`](../.github/component-catalog.json), giấy phép của từng thành phần ở mục 6 của [`DEPENDENCIES.txt`](../DEPENDENCIES.txt); image ghim phiên bản và digest trong `deploy/docker-compose.yml`.
Khi phát hành, workflow `Release Package` đính kèm các file này vào GitHub Release.
Workflow `Dependencies` (`ci-dependencies.yml`) quét lỗ hổng của các dependency này bằng Trivy khi file khai báo đổi và mỗi tuần.
Workflow này sinh lại SBOM của backend ngay trong CI và quét nó, vì `pom.xml` không ghi phiên bản của các gói do Spring Boot quản lý.
Quét SBOM chặn CI khi có lỗi HIGH hoặc CRITICAL đã có bản vá.
Cùng workflow quét các image của bên thứ ba trong `deploy/docker-compose.yml` và chỉ báo bằng cảnh báo.
Dependabot (`.github/dependabot.yml`) mở pull request cập nhật vào `Khuong/main`.
