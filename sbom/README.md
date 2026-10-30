# SBOM

Danh mục thành phần phần mềm định dạng CycloneDX (JSON; đặc tả 1.6, riêng `ai.cdx.json` là 1.5), chỉ gồm dependency chạy thật (không gồm scope test và dev).
Công cụ đánh giá RAG (`ai/requirements-eval.txt`, Langfuse, k6) chỉ chạy trong môi trường đánh giá riêng nên không nằm trong SBOM; giấy phép của chúng ở [`COPYRIGHT.txt`](../COPYRIGHT.txt).

| File | Nguồn |
|---|---|
| `backend.cdx.json` | Maven, `backend` (gốc `dx-os-parent`, cả sáu module `dx-core-api`, `dx-core`, `app-farm`, `app-lab`, `dx-host`, `dx-monitor`) |
| `ai.cdx.json` | `ai/requirements.lock` |
| `frontend.cdx.json` | npm, `frontend` |
| `mobile.cdx.json` | npm, `mobile` |

## Sinh lại

```bash
# Backend
cd backend
./mvnw org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom -DoutputFormat=json -DoutputName=backend.cdx -DincludeTestScope=false
cp target/backend.cdx.json ../sbom/

# AI (cyclonedx-py không ghi giấy phép; script bổ sung từ PyPI)
pip install cyclonedx-bom
cyclonedx-py requirements ai/requirements.lock --of JSON -o sbom/ai.cdx.json --sv 1.5
python3 .github/scripts/enrich_sbom_licenses.py sbom/ai.cdx.json

# Frontend và mobile
cd frontend && npx @cyclonedx/cyclonedx-npm --omit dev --output-format JSON --output-file ../sbom/frontend.cdx.json
cd mobile && npx @cyclonedx/cyclonedx-npm --omit dev --ignore-npm-errors --output-format JSON --output-file ../sbom/mobile.cdx.json
```

Sinh lại sau khi đổi dependency hoặc số phiên bản của dự án; giấy phép từng thành phần ở [`COPYRIGHT.txt`](../COPYRIGHT.txt).
Mọi thành phần trong các file này đều có giấy phép; `.github/scripts/check_licenses.py` (job `license` của workflow `CI Infra`) chặn thành phần không có giấy phép hoặc giấy phép ngoài danh sách cho phép, trừ ngoại lệ có lý do ở `.github/license-exceptions.json`.
Các file SBOM chỉ gồm thư viện của mã do nhóm viết; image của bên thứ ba (Keycloak, Apache APISIX, Superset, Mattermost, PostgreSQL, SeaweedFS, Ollama, ClamAV) ghi ở mục 6 của [`COPYRIGHT.txt`](../COPYRIGHT.txt), ghim phiên bản và digest trong `deploy/docker-compose.yml`.
Khi phát hành, workflow `Release Package` đính kèm các file này vào GitHub Release.
Workflow `CI Dependencies` (`ci-deps.yml`) quét lỗ hổng của các dependency này bằng Trivy khi file khai báo đổi và mỗi tuần.
Workflow này sinh lại SBOM của backend ngay trong CI và quét nó, vì `pom.xml` không ghi phiên bản của các gói do Spring Boot quản lý.
Quét SBOM chặn CI khi có lỗi HIGH hoặc CRITICAL đã có bản vá.
Cùng workflow quét các image của bên thứ ba trong `deploy/docker-compose.yml` và chỉ báo bằng cảnh báo.
Dependabot (`.github/dependabot.yml`) mở pull request cập nhật vào `Khuong/main`.
