# Phase 0: Chốt quyết định và phạm vi


| Mục                       | Giá trị               |
| ---------------------------- | ------------------------- |
| Trạng thái               | Xong                   |
| Mức độ                  | Nghiêm trọng          |
| Nhóm ưu tiên            | A                       |
| Ước lượng              | < 1 ngày              |
| Người phụ trách        | Phạm Minh Khương     |
| Bắt đầu, dự kiến xong | 05/10/2026 - 05/10/2026 |
| Phụ thuộc                | Không                  |

## 1. Tham chiếu


| Mục`PROJECT_IMPROVEMENTS.md` | Nội dung                                               | Mức độ      | Phạm vi trong phase này                                          |
| ------------------------------- | --------------------------------------------------------- | ---------------- | -------------------------------------------------------------------- |
| mục 22                       | Rủi ro lệch đề bài, chốt với giảng viên/BTC    | Nghiêm trọng | Toàn bộ                                                          |
| mục 23                       | Thời gian và cách xếp ưu tiên                     | Nghiêm trọng | Toàn bộ                                                          |
| mục 7 (một phần)           | Ranh giới Open-Core, workflow engine, gateway, storage | Nghiêm trọng | Chỉ phần chọn phương án (D2-D4), không thiết kế chi tiết |

Đề thi HaUI mục 2 và 4.3 (bối cảnh "đại học") và rubric PoF (đề mục 5, 10-12) là căn cứ cho D1.
Phase này không code, không đụng rubric điểm trực tiếp, nhưng mọi phase sau (P1-P9) phụ thuộc kết quả ở đây.

## 2. Lý do

- Vấn đề đang gặp: 10 quyết định nền tảng (D1-D10) trong `PHASE_PLAN.md` mục 4 chưa có kết luận, và ngày hạn nộp thật (14 ngày kể từ lúc công bố nội bộ) chưa xác định.
- Vì sao làm ở phase này: các phase P2-P9 có nội dung đổi hẳn tùy theo lựa chọn (ví dụ D2 chọn Flowable hay tự làm đổi toàn bộ work package của P3; D1 quyết định P9 có tồn tại hay không).
  Code trước khi chốt dễ phải làm lại.

## 3. Tổng quan sửa đổi

Phase này chỉ ra quyết định và cập nhật tài liệu kế hoạch, không sửa code.


| Lớp                    | Có thay đổi | Ghi chú ngắn                                                                    |
| ------------------------- | ---------------- | ----------------------------------------------------------------------------------- |
| Backend                 | Không         |                                                                                   |
| Frontend (web)          | Không         |                                                                                   |
| Mobile                  | Không         |                                                                                   |
| AI                      | Không         |                                                                                   |
| Data / Seed / Migration | Không         |                                                                                   |
| Infra / Deploy / CI     | Không         |                                                                                   |
| Docs / PoF              | Có            | Chốt`01_PLAN.md` của phase này, cập nhật lại `PHASE_PLAN.md` theo kết quả |

## 4. Phạm vi

- Trong phạm vi: chốt D1-D10 (bảng ở `01_PLAN.md`), xác định ngày hạn nộp thật, phân người phụ trách theo nhóm Core / nghiệp vụ / AI-dữ liệu (gợi ý ở `PHASE_PLAN.md` mục 5), cập nhật `PHASE_PLAN.md` nếu quyết định làm lệch thứ tự phase đã vẽ.
- Ngoài phạm vi (không làm ở phase này): mọi thay đổi code, thiết kế chi tiết schema/API (để ở P1 trở đi), viết báo cáo/slide/video (P8).
- Giả định: giảng viên hướng dẫn / BTC trả lời được trong thời gian ngắn (1-2 ngày); nếu không trả lời kịp, áp dụng phương án an toàn ở D1 (xem `01_PLAN.md` WP0-01) để nhóm có thể bắt đầu P1 mà không chờ.

## 5. Ưu điểm và hạn chế

- Ưu điểm: tránh code sai hướng ở các phase nặng nhất (P2, P3); cho người phụ trách từng phase một bảng quyết định duy nhất để tham chiếu thay vì hỏi lại nhiều lần.
- Hạn chế: tốn 1-2 ngày không ra tính năng hay bằng chứng PoF nào.
- Trade-off lớn: xem `04_TRADEOFFS_RISKS.md` (D1 và D2 là hai quyết định khó đảo ngược nhất).

## 6. Kết quả đầu ra mong đợi (Definition of Done)

- Demo được: không áp dụng (phase tài liệu).
- Sản phẩm bàn giao: bảng quyết định D1-D10 đã có cột "Kết luận" điền đủ ở `01_PLAN.md`; `PHASE_PLAN.md` đã cập nhật theo kết quả (P9 loại bỏ, lộ trình rút gọn chuyển thành phương án dự phòng không áp dụng).
- Tiêu chí đo được: cả 10 dòng D1-D10 ở `01_PLAN.md` mục "Quyết định thiết kế đã chốt" có giá trị khác `_(chưa chốt)_` — đạt.
- Ngày hạn: đã chốt là **không bị ràng buộc mốc 14 ngày**, làm đầy đủ P0-P8 + P10 theo ước lượng thật, không cần ghi ngày cụ thể vào README/CHANGELOG vì không có hạn chót ép buộc.
- Bằng chứng: biên bản họp/tin nhắn chốt với giảng viên về D1 (nếu có) dẫn link hoặc tóm tắt ở `01_PLAN.md`.

## 7. Phụ thuộc và thứ tự

- Cần từ phase trước: không có (phase đầu tiên).
- Phase sau cần từ phase này: P1 cần D6 (bật test); P2 cần D9; P3 cần D2, D3, D4; P4 cần D8; P6 cần D7; P7 cần D10.
  P9 đã bị loại bỏ theo D1, không còn trong `PHASE_PLAN.md`.

## 8. Ảnh hưởng đến thi (PoF và sản phẩm)

- Điểm rubric liên quan: gián tiếp toàn bộ — D1 quyết định có bị loại ngay từ vòng xét phạm vi đề bài hay không (đề mục 2), D2-D4 quyết định điểm kiến trúc Open-Core (đề mục 11, 10 điểm).
- Dùng làm bằng chứng cho: câu trả lời hội đồng số 1, 4 (mục 20 của `PROJECT_IMPROVEMENTS.md`) — "Open-Core nằm ở đâu, thay ứng dụng có phải đổi Core không".

## 9. Câu hỏi mở và quyết định cần chốt

Đây là nội dung chính của phase, chi tiết từng lựa chọn và căn cứ ở `01_PLAN.md`.


| #   | Câu hỏi                                                            | Lựa chọn                                                                                                      | Hạn chốt | Kết luận      |
| ----- | ---------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------- | ------------ | ----------------- |
| D1  | Đề tài nông nghiệp có được chấp nhận ở vòng HaUI không | Chấp nhận nguyên trạng / Thêm ứng dụng thứ hai bối cảnh trường học (P9) / Đổi hẳn đề tài     | 05/10/2026    | A — đã xác nhận với giảng viên/BTC, không làm P9 |
| D2  | Workflow engine dùng chung                                          | Flowable (nhúng) / Tự làm bảng`workflow_case`/`workflow_event`                                              | 05/10/2026    | Tự làm |
| D3  | Cửa API (gateway)                                                   | APISIX standalone / Traefik / Nginx nâng cấp                                                                  | 05/10/2026    | APISIX standalone |
| D4  | Lưu ảnh                                                            | MinIO / Storage service tự bọc trên filesystem hiện có                                                     | 05/10/2026    | MinIO |
| D5  | Kênh thông báo cho farmer                                         | Hộp thông báo trong app (poll) trước, push sau / Làm cả hai cùng lúc                                   | 05/10/2026    | Hộp thông báo: GET danh sách + PATCH đánh dấu đã đọc, push để sau |
| D6  | Viết test cho phần lõi                                            | Bật lại theo yêu cầu đề / Giữ nguyên quy ước không viết test                                        | 05/10/2026    | Bật lại, chỉ case ảnh hưởng logic/an toàn hệ thống (xem danh sách ở `01_PLAN.md`) |
| D7  | Phạm vi nhận diện AI                                              | Giữ 5 bệnh lá lúa hiện có / Thêm lớp "khác/không rõ" cho kỹ sư nhập tay                           | 05/10/2026    | Giữ 5 bệnh lá, thêm đường nhập triệu chứng qua RAG |
| D8  | Danh mục giống lúa                                                | Tự biên soạn từ nguồn công khai (Viện Lúa ĐBSCL, Sở NN) / Hoãn, dùng nhập tay có cảnh báo lệch | 05/10/2026    | Tự biên soạn từ nguồn công khai |
| D9  | Ma trận quyền engineer                                             | Theo thao tác (không theo khu vực) / Thêm "phạm vi phụ trách" theo xã/khu vực                          | 05/10/2026    | Theo thao tác, hoãn phạm vi khu vực |
| D10 | Nguồn dữ liệu mở thứ hai                                        | Open-Meteo (thời tiết) / Nguồn khác {đề xuất}                                                            | 05/10/2026    | Open-Meteo + snapshot dự phòng |

## 10. Tài liệu cần cập nhật khi xong

- [ ]  `README.md`, `CHANGELOG.md` (chỉ ghi trạng thái cuối — ví dụ "đã chốt phạm vi và 10 quyết định nền tảng").
- [ ]  `improvement_docs/PHASE_PLAN.md` (cập nhật bảng tổng quan, lộ trình rút gọn nếu D1 chọn thêm P9, mục 4 đổi "chưa chốt" thành giá trị thật).
- [ ]  `improvement_docs/README.md` mục "Chỉ mục phase" (thêm dòng cho Phase 0).
