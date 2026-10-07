# SBOM

Danh mục thành phần phần mềm định dạng CycloneDX 1.5 (JSON), chỉ gồm dependency chạy thật (không gồm scope test và dev).

| File | Nguồn |
|---|---|
| `backend.cdx.json` | Maven, `backend/DX-Farm` |
| `ai.cdx.json` | `ai/requirements.lock` |
| `frontend.cdx.json` | npm, `frontend` |
| `mobile.cdx.json` | npm, `mobile` |

## Sinh lại

```bash
# Backend
cd backend/DX-Farm
./mvnw org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom -DoutputFormat=json -DoutputName=backend.cdx -DincludeTestScope=false
cp target/backend.cdx.json ../../sbom/

# AI
pip install cyclonedx-bom
cyclonedx-py requirements ai/requirements.lock --of JSON -o sbom/ai.cdx.json --sv 1.5

# Frontend và mobile
cd frontend && npx @cyclonedx/cyclonedx-npm --omit dev --output-format JSON --output-file ../sbom/frontend.cdx.json
cd mobile && npx @cyclonedx/cyclonedx-npm --omit dev --ignore-npm-errors --output-format JSON --output-file ../sbom/mobile.cdx.json
```

Sinh lại sau khi đổi dependency; giấy phép từng thành phần ở [`LICENSES.md`](../LICENSES.md).
