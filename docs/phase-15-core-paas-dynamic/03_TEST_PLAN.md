# Kế hoạch kiểm thử: Phase 15

Kiểm tra phase đã đạt tiêu chuẩn và chạy đúng kỳ vọng chưa.
Quy ước dự án: không viết file kiểm mới khi người dùng chưa yêu cầu.
Mặc định kiểm bằng compile, `formatter:validate`, `deploy/dev/check_*.py` hiện có, kịch bản chạy tạm trên stack tạm và thử trên giao diện thật.
Ghi ai chạy và khi nào ở cột "Người chạy".

## 1. Chiến lược

- Mức kiểm thử áp dụng: tích hợp trên stack thật (API), E2E trên giao diện, bảo mật (quyền và secret), hồi quy.
- Môi trường: stack tạm rỗng dựng từ nhánh đang làm (cổng riêng, project riêng), giữ khóa `stack` khi chạy compose; gỡ cả volume sau khi xong.
- Chạy lại với từng tệp mô hình mà phase đụng tới, kể cả các mẫu có sẵn để chắc không đổi hành vi.
- Dữ liệu và tài khoản test: tài khoản thử theo vai của từng mô hình do `deploy/dev/kc.py` tạo; `dxos demo` cho bộ hồ sơ mẫu; một mô hình thử dùng đủ kiểu và quy tắc mới.

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T15-01 | WP15-01 | Happy path | `ADMIN` gọi `GET /api/v1/meta/models/{model}/openapi` của mô hình mẫu | 200; có đường danh sách, tạo, đọc, sửa, xóa, hành động và tệp của từng thực thể; schema có kiểu, bắt buộc, `enum` | | |
| T15-02 | WP15-01 | Happy path | Nhập tài liệu vào một trình sinh SDK và vào Postman | Nhập được không lỗi; một lệnh tạo hồ sơ sinh từ tài liệu chạy được | | |
| T15-03 | WP15-01 | Phân quyền | Gọi `/api/v1/meta/models/{model}/openapi` bằng vai của mô hình, vai của mô hình khác, người không có vai, không token | Có vai: 200; mô hình khác và không vai: 403 hoặc 404; không token: 401 | | |
| T15-04 | WP15-01 | Ranh giới | Đổi một trường rồi áp dụng; quay về phiên bản trước | Tài liệu đổi theo mỗi lần; không có tên thực thể nào của mô hình nằm trong mã lõi | | |
| T15-05 | WP15-02 | Happy path | `dxos client add app1 --model M --role R`, lấy token bằng secret, gọi `GET /api/v1/data/M/{entity}` | Secret in một lần; token lấy được; gọi trả 200 theo quyền của vai `R` | | |
| T15-06 | WP15-02 | Phân quyền | Cùng token gọi mô hình khác, `/api/v1/clients`, `/api/v1/models/apply`, API người dùng | 403 ở mọi đường | | |
| T15-07 | WP15-02 | Bảo mật | Tìm secret trong log backend, sự kiện quản trị, lịch sử và câu trả lời của `list` | Không thấy ở đâu ngoài câu trả lời của lệnh tạo hoặc đổi secret | | |
| T15-08 | WP15-02 | Vòng đời | `rotate` rồi dùng secret cũ và mới; `remove` rồi lấy token | Secret cũ bị từ chối, mới dùng được; sau `remove` không lấy được token; hồ sơ đã tạo còn | | |
| T15-09 | WP15-02 | Âm tính | Tên sai khuôn, mô hình không có, vai không có, tên trùng, vai `ADMIN` | Từ chối kèm lý do; không tạo client nào trong Keycloak | | |
| T15-10 | WP15-03 | Happy path | Áp dụng mô hình thử có `boolean`, `date`, `choice` nhiều lựa chọn; tạo, đọc, sửa, lọc | Ghi và đọc đúng; `date` dạng `YYYY-MM-DD`; nhiều lựa chọn là mảng; lọc ra đúng hồ sơ | | |
| T15-11 | WP15-03 | Âm tính | Gửi `"yes"` cho `boolean`, ngày sai khuôn, giá trị ngoài `options`, `required` trên `boolean` trong tệp | API và trình đọc tệp từ chối kèm tên trường, hoặc dòng và cột | | |
| T15-12 | WP15-03 | Ranh giới | Áp dụng lại cùng tệp; đổi kiểu một trường đang có dữ liệu | Áp dụng lại không làm gì; đổi kiểu được xếp mức rủi ro đúng hoặc bị từ chối | | |
| T15-13 | WP15-04 | Happy path | Trường có `min`, `max`, `pattern`; ghi giá trị trong khoảng và khớp mẫu | Ghi được | | |
| T15-14 | WP15-04 | Âm tính | Ghi giá trị ngoài khoảng hoặc không khớp qua API, rồi `INSERT` thẳng vào bảng | API từ chối kèm tên trường; cơ sở dữ liệu từ chối `INSERT` thẳng | | |
| T15-15 | WP15-04 | Dữ liệu cũ | Thêm `max` vào bảng đã có dòng vượt `max`; `pattern` ngoài tập con cho phép | Kế hoạch bị từ chối và nêu số dòng; `pattern` ngoài tập con bị báo lỗi theo dòng và cột | | |
| T15-16 | WP15-05 | Happy path | Trường `required_when: "kind == A"`: tạo hồ sơ loại A thiếu trường, loại B không có trường | Loại A bị từ chối kèm tên trường; loại B tạo được | | |
| T15-17 | WP15-05 | Âm tính | Sửa loại B sang A khi trường còn trống; điều kiện trỏ trường không có; `required_when` cùng `required` | Sửa bị từ chối; hai lỗi tệp báo theo dòng và cột | | |
| T15-18 | WP15-06 | Happy path | Mô hình có `ui:` đủ cột, sắp xếp, nhóm, gợi ý, menu, biểu tượng | Giao diện hiện đúng theo tệp | | |
| T15-19 | WP15-06 | Âm tính | `ui:` trỏ cột hay trường không có, biểu tượng ngoài danh sách | Báo lỗi theo dòng và cột, không áp dụng | | |
| T15-20 | WP15-06 | Ranh giới | Mọi mẫu có sẵn không có `ui:`; vai không đọc được một trường mà `ui:` liệt kê | Giao diện như trước; trường ngoài quyền vẫn không hiện | | |
| T15-21 | WP15-07 | Happy path | Chọn 10 hồ sơ chờ xử lý, bấm Duyệt | Cả 10 sang đã duyệt, mỗi hồ sơ có một dòng lịch sử | | |
| T15-22 | WP15-07 | Đồng thời | Trong lô có một hồ sơ đã bị người khác xử lý | Hồ sơ đó báo lý do; các hồ sơ còn lại được duyệt | | |
| T15-23 | WP15-07 | Phân quyền | Vai không có quyền thực hiện hành động; lô quá lớn | Không thấy nút; lô vượt giới hạn bị chặn trước khi gửi và không chạm giới hạn tốc độ | | |

| T15-24 | WP15-01 | Ranh giới | Lấy tài liệu OpenAPI của mô hình thử dùng đủ kiểu và ràng buộc mới | Có `enum`, mảng, `minimum`, `maximum`, `pattern` và điều kiện bắt buộc đúng với tệp mô hình | | |
| T15-25 | WP15-03 | Ranh giới | Hồ sơ có trường nhiều lựa chọn: sửa giá trị, xem lịch sử, chạy `dxos archive` rồi `archive check`; khai báo `multiple` cùng `unique` | Lịch sử ghi cũ và mới đọc được; archive qua; `multiple` cùng `unique` bị báo lỗi theo dòng và cột | | |
| T15-26 | WP15-02 | Ranh giới | Có client `dxos-app-*`: mở danh sách người dùng và ô chọn người; chạy `repair` và hủy bỏ một lần áp dụng dở | Tài khoản dịch vụ không hiện như người; client `dxos-app-*` không bị coi là lệch hay thừa | | |
| T15-27 | WP15-06 | Ranh giới | Thực thể còn hồ sơ đang xử lý: sửa `ui:` rồi xem kế hoạch và áp dụng; người đã chọn cột riêng mở danh sách | Kế hoạch mức `SAFE` và áp dụng được; người đã chọn cột thấy cột của mình | | |

Nhóm case nên có ở mọi WP khi làm: happy path, âm tính, phân quyền theo vai và theo client, ranh giới lõi và mô hình, áp dụng lại cùng tệp, hai người cùng thao tác, dữ liệu cũ sau khi áp dụng.

## 3. Hồi quy (không được hỏng)

**Dùng chung (mọi mô hình)**

- [ ] Đăng nhập SSO qua Keycloak (web, API) vẫn hoạt động.
- [ ] Người dùng chỉ thấy hồ sơ mà quyền của vai cho phép (`all`, `own`, `read`, `unit`, `assigned`).
- [ ] Trạng thái hồ sơ chỉ đổi qua quy trình, ghi đủ lịch sử; hành động sai trạng thái và cập nhật bản cũ bị từ chối (HTTP 409).
- [ ] Link tải tệp đính kèm có hạn hoạt động, hết hạn thì bị từ chối.
- [ ] Webhook và thông báo chat vẫn gửi, lỗi bên nhận không làm hỏng nghiệp vụ.
- [ ] Áp dụng lại cùng tệp mô hình không làm gì; hệ thống bị gián đoạn giữa chừng thì áp dụng lại để làm tiếp.
- [ ] Dựng từ clone sạch bằng `docker compose up -d --build` trong `deploy/` lên đủ dịch vụ healthy.
- [ ] Hai nhóm `deploy/dev/check_*.py` như CI (nhóm `api recovery case change process booking demo guards users docs conformance cli` và nhóm `events bootstrap`) đạt trên stack rỗng; `python3 deploy/apisix/check_routes.py` đạt.
- [ ] Bài Playwright của `e2e/` đạt.
- [ ] Danh sách, hộp việc, lịch và thống kê của mô hình mẫu hiển thị và cập nhật thời gian thực như trước.

**Theo mô hình** (mô hình mà phase đụng tới)

- [ ] `python3 deploy/dev/check_conformance.py <tên mô hình>` đạt cho mô hình thử và cho từng mẫu có sẵn.
- [ ] Mô hình mẫu có sẵn áp dụng vào hệ thống rỗng và áp dụng lại thì không làm gì.

## 4. Tiêu chí đạt của phase (exit criteria)

- Tất cả case mức bắt buộc đạt, không còn lỗi nghiêm trọng mở.
- Lỗi còn lại đã có issue và được chấp nhận ở `04_TRADEOFFS_RISKS.md`.
- Các tiêu chí ở `00_OVERVIEW.md` mục 6 đều có bằng chứng.

## 5. Bằng chứng và báo lỗi

- Bằng chứng: log chạy các `check_*.py`, ảnh chụp giao diện, tài liệu OpenAPI sinh ra, lưu ở thư mục tạm của phiên kiểm và tóm tắt trong báo cáo cho người dùng.
- Lỗi phát hiện: tạo issue, ghi mã vào cột "Kết quả" (ví dụ `Fail #123`).
