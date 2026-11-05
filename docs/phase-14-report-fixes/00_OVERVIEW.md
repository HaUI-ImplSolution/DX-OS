# Phase 14: Sửa các lỗi của báo cáo kỹ thuật

| Mục | Giá trị |
|---|---|
| Trạng thái | Đang làm |
| Mức độ | Quan trọng |
| Ứng dụng và module chính | `dx-core`, `dx-engine`, frontend, `deploy`, CI, Keycloak |
| Phụ thuộc | Không; nhánh `Khuong/phase-14-report-fixes` cắt từ `Khuong/main` ở `cac2f305` |

## 1. Tham chiếu

| Nguồn yêu cầu | Nội dung | Phạm vi trong phase này |
|---|---|---|
| [`docs/dynamic-core/BACKLOG.md`](../dynamic-core/BACKLOG.md), phần 2 | 13 việc đã kiểm là có thật và nên sửa, rút từ `docs/Bao_cao_ky_thuat.md` | Toàn bộ, theo thứ tự người dùng đã chốt |
| [Đề OLP 2026](https://vfossa.vn/tin-tuc/de-thi-phan-mem-nguon-mo-olp-2026-798.html), bốn nguyên lý | (i) dữ liệu lịch sử xuống hồ dữ liệu; (ii) hội tụ IT/OT; (iii) nguồn gốc số liệu; (iv) Zero-Trust, GitOps, tự phục hồi | WP14-07, WP14-10, WP14-11, WP14-12 |
| Đề OLP 2026, tầng dịch vụ lõi | Kiểm toán hành vi bất biến; sao lưu và khôi phục thảm họa tự động | WP14-03, WP14-09 |

Thông báo cho người được giao việc (mục 10 của báo cáo) để sau, nằm ở phần 4 của BACKLOG.

## 2. Lý do

- Báo cáo kỹ thuật liệt kê 15 rủi ro; đã kiểm từng mục trong mã, 13 mục có thật và chạm tới yêu cầu của đề hoặc tới điểm khác biệt "đổi mô hình an toàn trên hệ thống đang chạy".
- Ba mục P0 (khe hở khi áp dụng, client còn lại khi hủy bỏ, sao lưu không nhất quán) ảnh hưởng tính đúng của dữ liệu và quyền, nên làm trước.

## 3. Tổng quan sửa đổi

Làm theo thứ tự: khe hở khi áp dụng, dọn client khi hủy bỏ, sao lưu nhất quán, E2E trong CI, CLI `plan` và `apply`, outbox có giới hạn, kiểm khi ClamAV không trả lời, tự phục hồi, xóa tệp qua outbox, lịch sử chống sửa, thống kê, IT/OT, hồ dữ liệu.
Hai session chia việc theo vùng ở `.team/areas.json`; các WP không phụ thuộc nhau chạy song song.

| Module hoặc lớp | Có thay đổi | Ghi chú ngắn |
|---|---|---|
| `dx-model` | Không | |
| `dx-core` | Có | Xóa client Keycloak; nhận tài khoản dịch vụ của CLI |
| `dx-engine` | Có | Khóa theo mô hình; outbox; xóa tệp; chuỗi hash lịch sử; API thống kê |
| Frontend (web) | Có | Thống kê; outbox đã dừng |
| Data / Migration | Có | `V7` của engine |
| Infra / Deploy / CI | Có | `backup.sh`, `docker-compose.yml`, `dxos`, job E2E |
| Keycloak realm | Có | Client cho CLI |
| Docs | Có | BACKLOG, TESTING, OPERATIONS, CHANGELOG, báo cáo kỹ thuật |

## 4. Phạm vi

- Trong phạm vi: 13 việc ở phần 2 của BACKLOG với phạm vi đã thu nhỏ ghi ở `01_PLAN.md`.
- Ngoài phạm vi: thông báo cho người xử lý; webhook theo từng mô hình; nhiều bản sao backend; so nội dung từng tệp khi kiểm bản sao lưu.
- Giả định: chỉ chạy một bản backend; bản 2.0.0 chưa triển khai nơi nào nên migration không phải giữ dữ liệu thật.

## 5. Ưu điểm và hạn chế

- Ưu điểm: đóng các rủi ro về dữ liệu và quyền trước khi phát hành; mỗi nguyên lý của đề có một phần chạy được để trình diễn.
- Hạn chế: phần IT/OT và hồ dữ liệu là bản nhỏ, nằm ngoài lõi; neo của chuỗi hash nằm trên máy chủ, nên chỉ chống được người chỉ có quyền trên cơ sở dữ liệu.
- Trade-off lớn: xem [`04_TRADEOFFS_RISKS.md`](./04_TRADEOFFS_RISKS.md).

## 6. Kết quả đầu ra mong đợi (Definition of Done)

- Mỗi WP đạt tiêu chí ở `01_PLAN.md` và bài kiểm ở `03_TEST_PLAN.md`.
- Chuỗi script ở `deploy/dev` đạt trên stack rỗng và chạy trong CI.
- Việc tương ứng được xóa khỏi BACKLOG và báo cáo kỹ thuật; CHANGELOG ghi trạng thái cuối.

## 7. Phụ thuộc và thứ tự

- WP14-09 (xóa tệp qua outbox) đi trước phần kiểm tệp của WP14-03.
- WP14-04 (E2E trong CI) dùng chuỗi script đã có; các WP sau thêm bài kiểm vào cùng chuỗi.

## 8. Ảnh hưởng đến thi

- 50 điểm kho mã và thực hành nguồn mở: E2E trong CI, bằng chứng gắn với commit.
- 50 điểm sản phẩm: độ hoàn thiện (sao lưu, tự phục hồi, kiểm dịch), tính bền vững (lịch sử chống sửa), phủ thêm D của H-P-D-I.

## 9. Câu hỏi mở và quyết định cần chốt

Người dùng vắng mặt và để hai session tự quyết; quyết định đã chốt ở `01_PLAN.md`, quyết định còn vướng ghi vào phần 4 của BACKLOG.

## 10. Tài liệu cần cập nhật khi xong

- [x] `CHANGELOG.md` (chỉ ghi trạng thái cuối), Swagger cho API mới.
- [x] `deploy/OPERATIONS.md`, `docs/TESTING.md`, `.env.example` nếu có biến mới.
- [x] `docs/dynamic-core/BACKLOG.md` và `docs/Bao_cao_ky_thuat.md`: xóa phần đã sửa.
