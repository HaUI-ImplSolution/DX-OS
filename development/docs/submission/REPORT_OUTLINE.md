# Dàn ý báo cáo kỹ thuật (8-10 trang) và slide (tối đa 12)

Mỗi mục nêu nội dung cần viết và nơi lấy số liệu, bằng chứng.

| Mục báo cáo | Trang | Nội dung | Nguồn |
|---|---|---|---|
| 1. Bài toán và phạm vi | 1 | DX-OS Open-Core, hai ứng dụng minh họa (DX-Farm, DX-Lab), người dùng, luồng xương sống của từng ứng dụng | `README.md` |
| 2. Kiến trúc Open-Core 3 tầng | 1-2 | Sơ đồ 3 tầng, module và ranh giới biên dịch, danh sách interface, cách chọn ứng dụng lúc chạy | `development/docs/submission/ARCHITECTURE_OPEN_CORE.md`, `development/docs/refactor_docs/SPI_CATALOG.md` |
| 3. Dịch vụ dùng chung của Core | 1 | Identity/SSO, API Gateway, dữ liệu, workflow, thông báo, cổng AI: ai cung cấp, ứng dụng khai báo gì | `ARCHITECTURE_OPEN_CORE.md` mục 2, `sso/README.md`, `deploy/apisix/` |
| 4. H-P-D-I qua hai ứng dụng | 2 | Farm: vai và phạm vi hộ, duyệt và khóa PHI, view BI, CV và RAG. Lab: vai, ba loại case, SLA, view BI, luật phân loại và RAG | `backend/app-farm/README.md`, `backend/app-lab/README.md` |
| 5. Trí tuệ nhân tạo | 1 | CV, RAG, LangGraph, LLM, luật phân loại; ngưỡng, giới hạn, vai trò kỹ sư; dữ liệu và nguồn gốc | `ai/README.md`, `COUNCIL_QA.md` câu 6 |
| 6. Kiểm thử và kiểm chứng | 1 | Test backend, E2E hai ứng dụng trên stack sạch (Farm 10 bước, Lab 16 bước), bằng chứng Core không đổi khi thêm ứng dụng, ứng dụng mẫu rỗng | log workflow `E2E`, `deploy/demo/README.md`, `development/docs/refactor_docs/phase-c*/03_TEST_PLAN.md` |
| 7. Mã nguồn mở và giấy phép | 1 | Bảng license, SBOM, lockfile, SPDX, CONTRIBUTING và hướng dẫn thêm ứng dụng, issue tracker | `LICENSES.md`, `sbom/`, `CONTRIBUTING.md` |
| 8. Giới hạn và hướng phát triển | 0.5 | Rule thuốc được duyệt mới có hai bệnh, điểm nghẽn mở rộng, mục chưa làm của Lab (mobile, dashboard Superset, ticket tương tự), giấy phép của `ai/` | `COUNCIL_QA.md`, `improvement_docs/REMAINING_WORK.md` |

## Slide (12)

1. Bài toán và người dùng: hai ứng dụng trên một nền tảng
2. Sơ đồ 3 tầng và ranh giới Core / ứng dụng
3. Dịch vụ dùng chung: Identity/SSO, Gateway, Data, Workflow
4. H: danh tính và phân quyền theo vai
5. P: workflow cấu hình được và vòng đời (Farm, Lab)
6. Poka-yoke: khóa PHI, GPS, SLA, chống trùng giờ đặt phòng
7. I: CV, RAG, luật phân loại và vai trò con người
8. D: dữ liệu, BI, thời tiết
9. Demo: chuỗi Farm, đổi ứng dụng, chuỗi Lab
10. Kiểm thử: E2E hai ứng dụng và test lõi
11. Mã nguồn mở: license, SBOM, đóng góp, thêm ứng dụng mới
12. Giới hạn trung thực và hướng phát triển

## Video 3-5 phút

Quay từ bản chạy thật theo `DEMO_SCRIPT.md`, rút gọn: sơ đồ, một chuỗi Farm (duyệt, chẩn đoán, khóa thu hoạch), lệnh đổi ứng dụng, một chuỗi Lab (yêu cầu, xử lý, đặt phòng), thống kê.
Quay thử toàn bộ một lần trước khi quay bản nộp.
