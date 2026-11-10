# Phase 15: Mở rộng lõi theo hướng PaaS, dynamic và trải nghiệm Tầng 2

| Mục | Giá trị |
|---|---|
| Trạng thái | Đã chốt Q1, Q2, Q3 ngày 2026-11-10; chưa bắt đầu code, chưa tick việc nào ở `02_CHECKLIST.md` ngoài `PRE-01` |
| Mức độ | Quan trọng |
| Nhóm ưu tiên | A: P1, P2; B: D1, D2, D3; C: U1, U2 (thứ tự đề nghị, xem câu hỏi Q1) |
| Module chính | `dx-model`, `dx-core`, `dx-engine`, frontend, deploy |
| Người phụ trách | acc1: giao diện và áp dụng mô hình; acc2: quy trình, định dạng mô hình và hạ tầng (phân việc ở `01_PLAN.md`) |
| Phụ thuộc | Không; nhánh `Khuong/core-expansion` cắt từ `Khuong/main` sau khi `Khuong/demo-readiness` vào |

## 1. Tham chiếu

| Nguồn yêu cầu | Nội dung | Mức độ | Phạm vi trong phase này |
|---|---|---|---|
| Đề xuất mở rộng lõi (bản nháp ngày 2026-11-10) | Bốn hướng: PaaS, dynamic, tích hợp khi đóng vai trò service, trải nghiệm Tầng 2 | Quan trọng | Một phần: P1, P2, D1, D2, D3, U1, U2 |
| `docs/dynamic-core/README.md`, mục "Đề thi" | Lõi là PaaS cho ứng dụng tầng trên; đổi nghiệp vụ là đổi tệp mô hình | Quan trọng | Toàn bộ định hướng |
| `docs/dynamic-core/README.md`, mục giới hạn của mô hình | Một năng lực mới chỉ nhận khi cần ở ít nhất ba lĩnh vực, không làm hỏng mô hình đã có | Quan trọng | Tiêu chí nhận từng gợi ý; xem mục 4 và câu hỏi Q2 |

Đề thi: mục yêu cầu lõi không giao diện phục vụ ứng dụng tầng trên (P1, P2), mục lõi đổi được bằng khai báo (D1 đến D3, U1), và ứng dụng minh họa Tầng 2 (U1, U2).

## 2. Lý do

- Vấn đề đang gặp: một ứng dụng ngoài muốn gọi lõi phải dùng tài khoản người hay client `dxos-cli`; OpenAPI chỉ mô tả API chung nên không sinh được SDK đúng kiểu cho một mô hình; tệp mô hình chưa nói được vài quy tắc dữ liệu phổ biến (đúng/sai, chỉ ngày, nhiều lựa chọn, khoảng giá trị, điều kiện bắt buộc); giao diện dựng hoàn toàn từ metadata nên người triển khai không chỉnh được cột mặc định, nhóm trường hay menu bằng tệp.
- Vì sao làm ở phase này: P1 và P2 là điểm nhấn PaaS rõ nhất của đề; D1 đến D3 và U1 đổi định dạng tệp mô hình, nên làm gộp một lần để mô hình hiện có chỉ phải kiểm lại một lần.

## 3. Tổng quan sửa đổi

Phase làm bảy việc theo bốn đợt: nền metadata và tích hợp (P1, P2), mở rộng định dạng mô hình (D1, D2, D3, U1), rồi trải nghiệm của cán bộ (U2).
P1 sinh một tài liệu OpenAPI cho từng mô hình đang chạy từ chính tệp mô hình.
P2 cho đăng ký một ứng dụng ngoài với vai giới hạn trong một mô hình.
D1 đến D3 thêm kiểu trường, ràng buộc giá trị và bắt buộc có điều kiện.
U1 thêm khối `ui:` để tệp mô hình chỉnh giao diện.
U2 cho chọn nhiều hồ sơ rồi thực hiện một hành động trên từng hồ sơ.

| Module hoặc lớp | Có thay đổi | Ghi chú ngắn |
|---|---|---|
| `dx-model` (định dạng tệp mô hình) | Có | Kiểu trường mới, `min`, `max`, `pattern`, `required_when`, khối `ui:`; JSON Schema đổi theo |
| `dx-core` (thư viện dùng chung) | Có | `KeycloakAdminClient` thêm tạo, đổi secret và xóa client dạng client credentials |
| `dx-engine` (API chung, quy trình, áp dụng mô hình) | Có | Sinh OpenAPI; API đăng ký ứng dụng; kiểm giá trị; DDL cho kiểu mới; metadata trả thêm |
| `dx-monitor` (giám sát) | Không | |
| Frontend (web) | Có | Tab ứng dụng ngoài; ô nhập kiểu mới; đọc `ui:`; chọn nhiều hồ sơ |
| Data / Migration | Có | Cột và ràng buộc của bảng mô hình do DDL sinh; không có migration lõi mới dự kiến, trừ lịch sử ứng dụng ngoài nếu cần |
| Infra / Deploy / CI | Có | `dxos client ...` trong `deploy/dxos`; route gateway cho tài liệu OpenAPI nếu cần |
| Docs | Có | `docs/MODEL.md`, `docs/API.md`, README lõi (cần xin phép), CHANGELOG |

## 4. Phạm vi

- Trong phạm vi: P1 OpenAPI theo mô hình; P2 đăng ký ứng dụng ngoài; D1 kiểu `boolean`, `date`, nhiều lựa chọn; D2 ràng buộc `min`, `max`, `pattern`; D3 bắt buộc có điều kiện; U1 khối `ui:`; U2 hành động hàng loạt.
- Ngoài phạm vi (để sau): P3 `Idempotency-Key`; P4 thao tác hoàn tất `call_target` dùng chung danh sách đích webhook; U3 bảng Kanban; U4 xuất CSV.
- Ngoài phạm vi vĩnh viễn: các ý đã loại (nhập CSV, gói mô hình kèm danh mục, tìm kiếm, đăng ký thông báo theo người nhận, chuyển hồ sơ sang phiên bản quy trình mới, kiểm tương thích khi đổi mô hình, người thứ hai duyệt, ủy quyền có phạm vi, quyền theo từng trường) và các dòng đã khóa ở mục 4 của `BACKLOG.md`.
- Giả định: mô hình đang chạy vẫn hợp lệ sau phase; mọi khóa mới của tệp mô hình đều tùy chọn; backend chạy một bản.
- Ranh giới lõi và mô hình:
  - Kiểu và khóa mới thêm ở `dx-model`; `apply/`, `registry/` và `meta/` chỉ đọc chúng.
  - Kiểm giá trị nằm ở `data/` của `dx-engine`; DDL nằm ở `apply/`.
  - `dx-core` không phụ thuộc `dx-engine`.
  - Mã lõi không chứa tên thực thể, vai hay luồng của một lĩnh vực.
- Tiêu chí nhận năng lực mới của README lõi nói "khai báo được mà không cần cú pháp mới".
  D1 đến D3 và U1 thêm cú pháp, nên cần người dùng chốt ở câu hỏi Q2 rằng đây là một ngoại lệ có chủ đích, và cần ghi lại ở README lõi sau khi xong.

## 5. Ưu điểm và hạn chế

- Ưu điểm: ứng dụng ngoài nối vào lõi bằng SDK sinh từ tài liệu đúng mô hình và client có vai giới hạn; nhiều lĩnh vực hơn khai báo được bằng tệp; giao diện chỉnh được bằng tệp mà không viết mã; cán bộ xử lý nhanh hơn.
- Hạn chế: định dạng tệp mô hình lớn hơn nên `docs/MODEL.md`, JSON Schema và bộ kiểm phải theo kịp; thêm bề mặt API mới (OpenAPI, đăng ký ứng dụng) cần giữ đúng quyền.
- Trade-off lớn: xem `04_TRADEOFFS_RISKS.md`.

## 6. Kết quả đầu ra mong đợi (Definition of Done)

- Demo được:
  - Quản trị viên mở tài liệu OpenAPI của một mô hình, thấy đúng thực thể, kiểu trường, lựa chọn và hành động của mô hình, rồi nhập vào Postman.
  - Quản trị viên chạy `dxos client add` hoặc dùng tab Ứng dụng ngoài, nhận secret một lần, và ứng dụng ngoài gọi API dữ liệu của mô hình đó bằng token máy.
  - Một mô hình dùng kiểu `boolean`, `date`, nhiều lựa chọn, ràng buộc `min`, `max`, `pattern` và `required_when`, áp dụng được, và API từ chối giá trị sai kèm tên trường.
  - Một mô hình có khối `ui:` hiện cột, nhóm trường và menu theo tệp.
  - Cán bộ chọn nhiều hồ sơ trong danh sách, bấm Duyệt, và từng hồ sơ qua đủ luật quy trình, hồ sơ bị từ chối hiện lý do.
- Sản phẩm bàn giao: code, `docs/MODEL.md` và JSON Schema cập nhật, `docs/API.md`, CHANGELOG, hướng dẫn kiểm tay.
- Tiêu chí đo được:
  - Tài liệu OpenAPI của mô hình đổi khi mô hình đổi, và người không có vai nào của mô hình nhận 403 hoặc 404.
  - Client ứng dụng ngoài có vai `X` của mô hình `M` gọi API của mô hình khác nhận 403.
  - Client ứng dụng ngoài không nhận được vai `ADMIN` hay vai nền tảng nào.
  - Mọi mẫu trong thư viện mẫu vẫn áp dụng được và không đổi hành vi.
- Với mỗi tệp mô hình mà phase đụng tới: áp dụng được vào hệ thống rỗng, áp dụng lại cùng tệp thì không làm gì, và `python3 deploy/dev/check_conformance.py <tên mô hình>` đạt mà không phải sửa mã lõi.
- Bằng chứng: log chạy các `deploy/dev/check_*.py` hiện có, ảnh chụp giao diện, tài liệu OpenAPI sinh ra.

## 7. Phụ thuộc và thứ tự

- Cần từ phase trước: `Khuong/demo-readiness` đã vào `Khuong/main`.
- Phase sau cần từ phase này: P3, P4, U3, U4 nếu làm sau; bản trình diễn dùng các mô hình có khối `ui:` và kiểu mới.
- Trong phase: P1 trước P2 (chung nền metadata); D1 đến D3 và U1 cùng đổi định dạng nên đi chung một đợt đổi `dx-model`; U2 sau cùng, chỉ cần frontend.

## 8. Ảnh hưởng đến thi

- Điểm rubric liên quan: "Tính nguyên gốc của giải pháp", "Mức độ hoàn thiện", "Mức độ thân thiện" (mỗi phần 10 điểm).
- Dùng làm bằng chứng cho: báo cáo và video demo (tích hợp như một service, đổi giao diện bằng tệp).

## 9. Câu hỏi mở và quyết định cần chốt

| # | Câu hỏi | Lựa chọn | Hạn chốt | Kết luận |
|---|---|---|---|---|
| Q1 | Thứ tự ưu tiên của các hướng | A: P1, P2, rồi D1 đến D3, rồi U1, U2. B: đảo hướng Tầng 2 (U1, U2) lên trước | Trước khi code | Chọn A, người dùng chốt 2026-11-10 |
| Q2 | Có đổi định dạng tệp mô hình (kiểu trường mới, khối `ui:`) kèm `docs/MODEL.md` và JSON Schema không | A: có, mọi khóa mới tùy chọn nên `format: 0` giữ nguyên. B: có, nâng `format` lên 1. C: không đổi định dạng, bỏ D1 đến D3 và U1 | Trước khi code | Chọn A, người dùng chốt 2026-11-10 (đề nghị của acc2; bản cũ vẫn từ chối khóa lạ bằng lỗi `unknown key` kèm dòng và cột) |
| Q3 | Ứng dụng ngoài cấp quyền theo cấp nào | A: theo vai của một mô hình. B: theo từng thực thể | Trước khi code | Chọn A, người dùng chốt 2026-11-10 |

## 10. Tài liệu cần cập nhật khi xong

- [ ] `README.md`, `README_EN.md`, `CHANGELOG.md` (chỉ ghi trạng thái cuối).
- [ ] Swagger (springdoc) cho API mới: tài liệu OpenAPI của mô hình, đăng ký ứng dụng ngoài, siêu dữ liệu thêm.
- [ ] `docs/MODEL.md` và `backend/dx-model/src/main/resources/schema/model-v0.schema.json`.
- [ ] `docs/dynamic-core/README.md`: bảng giới hạn kiểu trường và ghi chú ngoại lệ "không cần cú pháp mới" (cần xin phép trước khi sửa).
- [ ] `docs/API.md`, `deploy/OPERATIONS.md` (lệnh `dxos client`), `ARCHITECTURE.md` mục 4 và mục "Thêm một nghiệp vụ mới" ở `CONTRIBUTING.md`.
- [ ] `DEPENDENCIES.txt` và `sbom/` nếu thêm dependency, `deploy/.env.example` nếu thêm cấu hình.
- [ ] `docs/dynamic-core/BACKLOG.md`: thêm P3, P4, U3, U4 vào việc để sau; xóa việc đã xong.
