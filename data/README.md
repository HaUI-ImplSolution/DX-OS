# Dữ liệu seed và tri thức (`data`)

Nguồn duy nhất cho dữ liệu seed nghiệp vụ của Farm và corpus RAG của cả hai ứng dụng.

| Thư mục | Nội dung | Đi đâu |
|---|---|---|
| `structured/` | Bệnh, thuốc, treatment rule, policy, class contract của CV | `generate_seed_sql.py` sinh `backend/app-farm/src/main/resources/farm/data.sql`, Spring chạy lúc khởi động (Backend không đọc JSON) |
| `rag/` | `processed/disease_knowledge.json` (6 tài liệu bệnh lúa) và `source_registry.json` (10 nguồn) | Job `rag-ingest` nạp vào AI Vector DB; không seed vào database nghiệp vụ |
| `rag/lab/` | `processed/lab_knowledge.json` (10 bài tự soạn, CC BY 4.0) và `source_registry.json` | Job `rag-ingest` nạp vào namespace `lab` |

Job `rag-ingest` nạp corpus của app đang chạy theo `DXOS_APP` (xem [`ai/README.md`](../ai/README.md)); Compose bind-mount `data/` read-only tại `/data` (`AI_DATA_DIR`).

## Nguyên tắc

1. `disease_code` khớp tuyệt đối `diseases.code` và nhãn đầu ra CV; không fuzzy match.
2. Tên thuốc, liều, đơn vị, PHI là dữ liệu deterministic: không lấy từ RAG, không để LLM sửa.
3. Chỉ seed treatment rule khi có quyết định rõ trong dữ liệu nguồn (`eligible_for_seed=true` kèm `seed_authorization`); không bulk-seed file `candidates`.
4. Seed idempotent; `source_ids` truy được về registry để audit.
5. Khi `rag/source_registry.json` đổi, cập nhật `backend/app-farm/src/main/resources/knowledge-sources.json` để trang hỏi đáp hiện đúng nguồn; `python -m unittest data.scripts.test_knowledge_sources` kiểm hai bên khớp nhau (cả corpus Lab).

## Đổi dữ liệu nguồn

Không sửa tay `data.sql`; sinh lại, kiểm tra rồi commit `data/` cùng `data.sql`:

```bash
python data/scripts/generate_seed_sql.py
python data/scripts/generate_seed_sql.py --check
python -m unittest data.scripts.test_generate_seed_sql
```

CI chạy hai lệnh kiểm tra để chặn JSON và SQL lệch nhau.
`scripts/validate_rice_workbook_export.py` đối chiếu JSON với workbook thu thập cục bộ (thủ công).

## Seed hiện hành

- 6 bệnh `active=TRUE` (6 lớp của CV, gồm `Rice___healthy`); bệnh và thuốc `active=FALSE` không xuất hiện trong danh mục và lookup mới nhưng giữ lịch sử, chỉ bị xóa khi không còn diagnosis, rule hay bản khai phun tham chiếu.
- Thuốc `NEWTEC_300SC` và 2 treatment rule được chuyên gia xác nhận ngày 2026-10-05 (đạo ôn, khô vằn).
- `HELP_400SC` và rule đốm nâu (`P-RICE-002`) giữ làm ứng viên: rule đốm nâu mới có `E2E_ASSUMPTION`, chưa có phê duyệt chuyên gia nên bị chặn.
  `blocked_reason`, `verification`, `eligible_for_seed` là hàng rào an toàn, không bỏ qua được.
- `disease_management_policies` để trống vì chưa policy nào đủ điều kiện (`eligible_for_deterministic_guardrail=true`); bảng chỉ dùng để chặn và tra cứu, không sinh khuyến nghị hóa chất.

## Mở khóa thuốc, rule, policy

Khi chuyên gia duyệt: cập nhật JSON trong `structured/`, chạy generator.

- **Pesticide:** `code` (duy nhất, `UPPER_SNAKE_CASE`), `name`, `unit` đọc trực tiếp từ JSON, không suy ra từ `name` hay `formulation` (`SC`, `WP`, `EC`, `SL` là dạng thuốc, không phải đơn vị liều); chỉ record đủ ba trường với nguồn phù hợp mới có `eligible_for_pesticide_seed=true`.
- **Treatment rule:** liên kết `disease_code` → `diseases.id` và `pesticide_candidate_id` → thuốc đã seed.
  `dose_min`, `dose_max` (liều cố định thì bằng nhau), `dose_unit`, `phi_days` (`null` thì không seed) vào cùng tên cột; `source_ids` → `source`, `seed_version` → `version`; `application_method`, lượng nước, `seed_authorization` vào `notes`.
- **Thu hồi:** rule bị thu hồi đặt `active=false`, chưa có dữ liệu tham chiếu thì xóa; thuốc mất eligibility chỉ xóa khi không còn rule hay bản khai phun tham chiếu.
- **Policy:** mô tả bệnh không có thuốc chữa trực tiếp hoặc chưa xác minh rule địa phương; không chuyển thành treatment rule.

## Kiểm tra sau khi seed

- Đúng 6 `diseases.code` active, khớp contract CV.
- Mọi rule active tham chiếu disease và pesticide tồn tại, không trùng `(disease_id, pesticide_id)`, và có đủ `dose_min`, `dose_max`, `dose_unit`, `phi_days`, `source`, `version`.
- `/internal/v1/diagnosis-context` trả đúng rule theo `disease_code`; rule không tồn tại hoặc chưa duyệt trả `review_required`, không fallback sang số liệu do RAG hay LLM tạo.
