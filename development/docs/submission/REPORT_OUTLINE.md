# Dàn ý báo cáo kỹ thuật (8-10 trang) và slide (tối đa 12)

Mỗi mục nêu nội dung cần viết và nơi lấy số liệu, bằng chứng.

| Mục báo cáo | Trang | Nội dung | Nguồn |
|---|---|---|---|
| 1. Bài toán và phạm vi | 1 | Agri-Carbon DX-OS, người dùng, luồng xương sống | `README.md` |
| 2. Kiến trúc 3 tầng và H-P-D-I | 1-2 | Sơ đồ, ranh giới Core và ứng dụng, luồng request | `development/docs/submission/ARCHITECTURE_OPEN_CORE.md` |
| 3. Quy trình và an toàn | 1-2 | Workflow, ma trận quyền, poka-yoke (PHI, GPS, ngày thu hoạch), vòng đời vụ mùa | `legacy_docs/PARCEL_SEASON_CODES.md`, phase 1-5 trong `improvement_docs/` |
| 4. Dữ liệu | 1 | PostgreSQL, view cho BI, MinIO, thời tiết và snapshot | `bi/README.md`, `CHANGELOG.md` |
| 5. Trí tuệ nhân tạo | 1-2 | CV, RAG, LangGraph, LLM; ngưỡng, giới hạn, vai trò kỹ sư | `ai/README.md`, phase 6 |
| 6. Kiểm thử và kiểm chứng | 1 | Test backend và AI, E2E trên stack sạch, số bước đạt | log workflow `E2E`, `deploy/demo/README.md` |
| 7. Mã nguồn mở và giấy phép | 1 | Bảng license, SBOM, lockfile, SPDX, CONTRIBUTING, issue tracker | `LICENSES.md`, `sbom/` |
| 8. Giới hạn và hướng phát triển | 0.5 | Mới có rule thuốc được duyệt cho đạo ôn và khô vằn, điểm nghẽn mở rộng, mục để sau | `COUNCIL_QA.md`, `phase-10-de-sau/` |

## Slide (12)

1. Bài toán và người dùng
2. Sơ đồ 3 tầng
3. H: danh tính và phân quyền
4. P: workflow và vòng đời vụ mùa
5. Poka-yoke: khóa PHI, GPS, ngày thu hoạch
6. I: CV, RAG và vai trò kỹ sư
7. D: dữ liệu, BI, thời tiết
8. Demo: chuỗi báo bệnh đến khóa thu hoạch
9. Kiểm thử: E2E và test lõi
10. Mã nguồn mở: license, SBOM, đóng góp
11. Giới hạn trung thực
12. Hướng phát triển

## Video 3-5 phút

Quay từ bản chạy thật theo `DEMO_SCRIPT.md`, rút gọn ở các bước 1, 3, 5, 8.
Quay thử toàn bộ một lần trước khi quay bản nộp.
