# Đợt làm: Ứng dụng mẫu cửa hàng điện máy

| Mục | Giá trị |
|---|---|
| Trạng thái | Draft |
| Mức độ | Quan trọng |
| Nhóm ưu tiên | A (thứ tự 1 ở mục 1 của `docs/dynamic-core/BACKLOG.md`) |
| Module chính | `examples/` (ứng dụng ngoài), deploy (`extra_hosts` của backend) |
| Người phụ trách | acc1 |
| Phụ thuộc | #344 (`dxos connect`) và #345 (`webUrl`), đã vào `Khuong/main` |

## 1. Tham chiếu

| Nguồn yêu cầu | Nội dung | Mức độ | Phạm vi trong đợt làm này |
|---|---|---|---|
| Issue #343 | Ứng dụng mẫu của bên ngoài tích hợp DX-OS để thêm quy trình | Quan trọng | Toàn bộ |
| Mục 1 và mục 3.1 của `docs/dynamic-core/BACKLOG.md` | Bằng chứng lõi là PaaS: ứng dụng mẫu | Quan trọng | Toàn bộ |
| `docs/dynamic-core/UNG_DUNG_MAU.md` | Các điểm đã chốt về hướng, vai, lĩnh vực, danh tính, dữ liệu tham chiếu, webhook | Quan trọng | Toàn bộ |
| `examples/appliance-store/README.md` | Mục tiêu, vai, kịch bản demo, giới hạn | Quan trọng | Toàn bộ |

Đề thi: câu "PaaS phục vụ các ứng dụng tầng trên" và tiêu chí nguyên gốc, phát triển bền vững.

## 2. Lý do

- Vấn đề đang gặp: chưa có bằng chứng chạy được rằng một ứng dụng có sẵn của người ngoài thêm được quy trình bằng DX-OS; `examples/order_approval` chỉ là một script dòng lệnh.
- Vì sao làm ở đợt làm này: #344 và #345 đã xong nên ứng dụng mẫu dùng được `dxos connect` và `webUrl`; đây là việc đứng đầu trong thứ tự đến hạn nộp, trước bài trình bày chung kết.

## 3. Tổng quan sửa đổi

Dựng `examples/appliance-store/`: ứng dụng bán hàng nhỏ, tự chứa, không dùng mã của dự án.
Thứ tự: mô hình và script cài đặt, ứng dụng, phần tích hợp, kiểm chạy theo README, rồi tài liệu và đóng việc.

| Module hoặc lớp | Có thay đổi | Ghi chú ngắn |
|---|---|---|
| `dx-model` (định dạng tệp mô hình) | Không | |
| `dx-core` (thư viện dùng chung) | Không | |
| `dx-engine` (API chung, quy trình, áp dụng mô hình) | Không | |
| `dx-monitor` (giám sát) | Không | |
| Frontend (web) | Không | Người duyệt dùng giao diện có sẵn |
| Data / Migration | Không | |
| Infra / Deploy / CI | Có | `extra_hosts` cho backend để gọi webhook về ứng dụng |
| Docs | Có | README của ví dụ, `docs/DEMO.md`, CHANGELOG, xóa `UNG_DUNG_MAU.md` |

## 4. Phạm vi

- Trong phạm vi: ứng dụng Node.js với sổ bán hàng; hai tệp mô hình (đổi trả dùng nguyên, bảo hành mở rộng); script cài đặt; phần tích hợp; README có số dòng mã tích hợp; phần pitch ở `docs/DEMO.md`.
- Ngoài phạm vi: màn hình duyệt trong ứng dụng, đăng nhập một lần, đồng bộ sản phẩm liên tục, chức năng ngoài sổ bán hàng, đổi trả và bảo hành.
- Giả định: người vận hành chạy DX-OS bản đầy đủ (không `--core`) cùng máy với ứng dụng, trên Linux có Docker.
- Ranh giới lõi và mô hình: không đổi tệp mô hình lõi, không thêm thuộc tính ở `dx-model`; nghiệp vụ cửa hàng điện máy nằm trong tệp mô hình của ví dụ, không nằm trong mã lõi.

## 5. Ưu điểm và hạn chế

- Ưu điểm: bằng chứng chạy được cho câu "PaaS phục vụ ứng dụng tầng trên"; đo được số bước và số dòng tích hợp; dùng lại #344 và #345.
- Hạn chế: nhân viên duyệt có hai tài khoản; quyền `own` áp cho client chứ không cho từng nhân viên; chưa thử trên Docker Desktop.
- Trade-off lớn: không có; hai điểm chọn nhỏ ở mục 9.

## 6. Kết quả đầu ra mong đợi (Definition of Done)

- Demo được: nhân viên lập đổi trả trong ứng dụng, quản lý duyệt ở hộp việc DX-OS qua liên kết `webUrl`, ứng dụng đổi trạng thái khi webhook về; nhân viên lập bảo hành, kỹ thuật viên nhận, hạn ngắn trôi qua thì ứng dụng hiện cảnh báo.
- Sản phẩm bàn giao: `examples/appliance-store/` (ứng dụng, mô hình, script cài đặt, compose, README), mục pitch ở `docs/DEMO.md`.
- Tiêu chí đo được:
  - Làm theo README trên máy sạch có Docker, từ cài DX-OS đến duyệt xong một hồ sơ của mỗi quy trình.
  - Không sửa mã lõi; mọi dependency của ứng dụng qua `check_licenses.py` không báo lỗi.
  - Client của đổi trả gọi API của bảo hành bị từ chối (403), và ngược lại.
  - Số dòng mã tích hợp ghi ở README là số đếm thật của tệp tích hợp.
- Với mỗi tệp mô hình mà đợt làm đụng tới: áp dụng được vào hệ thống rỗng, áp dụng lại cùng tệp thì không làm gì, và `python3 e2e/api/check_conformance.py <tên mô hình>` đạt mà không phải sửa mã lõi.
- Bằng chứng: log chạy theo README trên stack thử, ảnh chụp các màn hình chính.

## 7. Phụ thuộc và thứ tự

- Cần từ đợt làm trước: `dxos connect` (#344), `webUrl` (#345).
- Việc sau cần từ đợt làm này: bài trình bày chung kết dùng ví dụ này làm kịch bản demo.

## 8. Ảnh hưởng đến thi

- Điểm rubric liên quan: nguyên gốc 10, phát triển bền vững 10, và phần trình diễn ở bài chung kết.
- Dùng làm bằng chứng cho: báo cáo, slide, video demo, câu hỏi về vai trò PaaS của lõi.

## 9. Câu hỏi mở và quyết định cần chốt

| # | Câu hỏi | Lựa chọn | Hạn chốt | Kết luận |
|---|---|---|---|---|
| Q1 | Backend DX-OS chưa có `extra_hosts`, nên chưa gọi được webhook tới ứng dụng ở `host.docker.internal` | Thêm `extra_hosts` cho backend trong `deploy/docker-compose.yml`, đối xứng với phía ứng dụng, kèm tài liệu và kiểm tay | Đã chốt |  Đã chốt: thêm `extra_hosts`, hỗ trợ đủ ở compose, tài liệu và kiểm tay |
| Q2 | Chia PR | Một PR cho cả đợt làm | Đã chốt | Một PR, `Closes #343` |
| Q3 | `ai.tasks` của `support_desk` có bị thừa kế vào mô hình bảo hành không | Xác minh bằng `dxos plan` ở WP-01 | Khi làm WP-01 | _(chưa chốt)_ |

## 10. Tài liệu cần cập nhật khi xong

- [ ] `README.md` của ví dụ (bỏ dòng "định hướng", ghi số dòng tích hợp), `CHANGELOG.md`.
- [ ] `docs/DEMO.md` (phần pitch, nhận định thị trường ghi là giả thuyết, #231).
- [ ] `DEPENDENCIES.txt` và `sbom/` nếu thêm dependency ngoài Fastify; `deploy/.env.example` nếu đổi cấu hình (`extra_hosts` không thêm biến).
- [ ] `docs/dynamic-core/BACKLOG.md`: xóa dòng #343; xóa `docs/dynamic-core/UNG_DUNG_MAU.md`.
- [ ] Xóa thư mục này này khi xong, việc còn lại gộp vào `BACKLOG.md`.
