# SBOM

Danh mục thành phần phần mềm định dạng CycloneDX (JSON; đặc tả 1.6, riêng `ai.cdx.json` là 1.5), chỉ gồm dependency chạy thật (không gồm scope test và dev).
Công cụ đánh giá RAG (`ai/requirements-eval.txt`, Langfuse, k6) chỉ chạy trong môi trường đánh giá riêng nên không nằm trong SBOM; giấy phép của chúng ở [`LICENSES.md`](../LICENSES.md).

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

# AI
pip install cyclonedx-bom
cyclonedx-py requirements ai/requirements.lock --of JSON -o sbom/ai.cdx.json --sv 1.5

# Frontend và mobile
cd frontend && npx @cyclonedx/cyclonedx-npm --omit dev --output-format JSON --output-file ../sbom/frontend.cdx.json
cd mobile && npx @cyclonedx/cyclonedx-npm --omit dev --ignore-npm-errors --output-format JSON --output-file ../sbom/mobile.cdx.json
```

Sinh lại sau khi đổi dependency; giấy phép từng thành phần ở [`LICENSES.md`](../LICENSES.md).
