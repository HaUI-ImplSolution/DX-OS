# Kịch bản trình diễn 15 phút

Kịch bản cho phần trình diễn chung kết của bản `2.1.0`.
Phần trình chiếu đi cùng ở [`TRINH_BAY.md`](./TRINH_BAY.md); ba phần demo của nó theo các mục 3, 4 và 5 dưới đây.
Mỗi bước nêu việc làm, tài khoản dùng, kết quả mong đợi và cách xử lý khi sự cố.
Khung kiểm gốc là tám mục "Thế nào là đạt" ở [`docs/dynamic-core/README.md`](./dynamic-core/README.md#thế-nào-là-đạt), chạy tự động bằng `python3 e2e/api/check_demo.py`.

## Chuẩn bị trước ngày trình diễn

1. Cài bản `2.1.0` trên máy chủ công khai bằng `dxos-install.sh`, rồi `dxos start` kèm tệp `onestop.yaml` ở [`backend/dx-engine/src/main/resources/templates/`](../backend/dx-engine/src/main/resources/templates/onestop.yaml).
2. `dxos doctor` phải không báo lỗi, kể cả dòng quét mã độc của tệp tải lên.
3. `dxos demo` để có bốn tài khoản thử và khoảng 40 hồ sơ mẫu; mật khẩu in ra một lần, ghi lại ngay.
   Chạy lệnh này sớm: hạn xử lý của một cửa sinh viên là 72 giờ và không thể làm trễ qua API, nên hồ sơ quá hạn chỉ xuất hiện khi các hồ sơ đã nộp đủ ba ngày.
4. Đặt mật khẩu cho tài khoản quản trị bằng `dxos admin` (tài khoản `admin`, bắt buộc đổi mật khẩu ở lần đầu).
5. Tập trọn kịch bản một lần trên đúng máy sẽ trình diễn, kể cả các bước đổi mô hình ở phần 4, vì chúng làm thay đổi mô hình đang chạy.
6. Mở sẵn bốn cửa sổ trình duyệt riêng, mỗi cửa sổ đã đăng nhập một tài khoản: `judge_student`, `judge_officer`, `judge_officer2` và `admin`.

| Tài khoản | Vai | Thấy gì |
|---|---|---|
| `judge_student` | Sinh viên | Hồ sơ của chính mình |
| `judge_officer` | Cán bộ Phòng Đào tạo | Hồ sơ của đơn vị Đào tạo |
| `judge_officer2` | Cán bộ Phòng Công tác sinh viên | Hồ sơ của đơn vị Công tác sinh viên |
| `judge_supervisor` | Người giám sát | Mọi hồ sơ, chỉ đọc |
| `admin` | Quản trị | Bảng điều khiển ở `/console` |

## Tiến trình

| Phút | Phần | Nội dung |
|---|---|---|
| 0 đến 2 | Vấn đề | Phần mềm rời rạc tạo ốc đảo dữ liệu, hệ thống nguyên khối biến tổ chức thành con tin của nhà cung cấp |
| 2 đến 4 | Kiến trúc | Lõi không giao diện, bốn dịch vụ bắt buộc, ba tầng |
| 4 đến 8 | Quy trình không lách được | Hồ sơ từ nộp đến duyệt trên hai trình duyệt |
| 8 đến 12 | Đổi nghiệp vụ khi hệ thống đang chạy | Đổi tệp mô hình, xem kế hoạch, áp dụng |
| 12 đến 14 | Vận hành và dữ liệu | Thống kê, lịch sử chống sửa, lưu trữ dài hạn |
| 14 đến 15 | Định hướng | Phần còn thiếu và hướng phát triển |

## 1. Vấn đề (0 đến 2 phút)

Nói ba ý, không mở máy:

- Một trường học hay một doanh nghiệp nhỏ có những quy trình giống nhau: một người gửi, một người xem xét, duyệt hoặc trả lại, mọi bước đều phải để lại dấu vết.
- Mỗi lần có nghiệp vụ mới, người ta dựng lại đăng nhập, phân quyền, lưu tệp và luồng duyệt.
- DX-OS làm phần dùng chung đó một lần, nghiệp vụ nằm trong một tệp mô hình.

Nếu còn thời gian, thêm ba ý sau và nói rõ đây là giả thuyết, vì đội chưa nói chuyện với khách hàng nào ([#231](https://github.com/HaUI-ImplSolution/DX-OS/issues/231)):

- **Nỗi đau:** đơn vị nhỏ cần quy trình duyệt có phân quyền và lịch sử, nhưng không đủ người để dựng và giữ một hệ thống riêng.
- **Thị trường:** các đơn vị đã có phần mềm chạy sẵn và chỉ thiếu một quy trình; họ cần thêm quy trình vào đó, không cần thay phần mềm.
- **Cạnh tranh:** bộ quản lý quy trình đóng gói và nền tảng low-code thường gắn với một nhà cung cấp hoặc tính phí theo người dùng; DX-OS là nguồn mở, tự chạy trên máy của đơn vị, và ứng dụng ngoài gọi nó như gọi Keycloak hay MinIO.

## 2. Kiến trúc (2 đến 4 phút)

Mở [`ARCHITECTURE.md`](../ARCHITECTURE.md) hoặc sơ đồ ở README.

- **Tầng 1, lõi:** chỉ có API, không có trang nào do đội viết.
  Keycloak cho định danh, APISIX cho cổng API, PostgreSQL và SeaweedFS cho dữ liệu có cấu trúc và tệp, Flowable chạy nhúng cho luồng công việc.
- **Tầng 2:** giao diện dựng từ metadata của mô hình đang chạy, cùng portal và màn hình giám sát.
- **Tầng 3:** nghiệp vụ chỉ là tệp mô hình, không có mã.

Mở `/portal/` để cho thấy trạng thái các dịch vụ, rồi chạy `dxos status` ở terminal.

Sự cố: nếu một dịch vụ báo ngừng, chạy `dxos reconcile` rồi `dxos status`; nếu vẫn lỗi, bỏ qua phần này và nói bằng lời.

## 3. Quy trình không lách được (4 đến 8 phút)

**Cửa sổ `judge_student`:**

1. Danh sách "Hồ sơ" chỉ có hồ sơ của chính sinh viên.
2. Bấm Tạo mới: nhập lý do, chọn loại hồ sơ, chọn Phòng Đào tạo ở "Đơn vị xử lý", lưu.
3. Mở hồ sơ vừa tạo, đính kèm một tệp PDF vào trường minh chứng.
4. Thử tải một tệp `.html`: bị từ chối.

**Đặt hai cửa sổ cạnh nhau:** `judge_officer` ở hộp việc và `judge_student` ở trang tạo hồ sơ.
Khi sinh viên lưu, số "Việc đang chờ" của cán bộ tăng lên trong khoảng hai giây mà không cần tải lại trang.
Đây là thông báo thời gian thực của hộp việc.

**Cửa sổ `judge_officer`:**

5. Mở hồ sơ trong hộp việc, bấm "Yêu cầu bổ sung" và nhập lý do (bắt buộc).
6. Chuyển sang `judge_student`: hồ sơ ở trạng thái "Cần bổ sung", sửa thông tin rồi bấm "Nộp lại".
7. Chuyển lại `judge_officer`: bấm "Duyệt".
   Trang chi tiết hiện lịch sử đủ ai làm gì và lúc nào, kèm lý do.

**Cửa sổ `judge_officer2`:** cán bộ Phòng Công tác sinh viên không thấy hồ sơ của Phòng Đào tạo.

**Chặn lách quy trình:** trạng thái của hồ sơ chỉ đổi qua quy trình.
Chiếu kết quả tiêu chí 4 của `check_demo.py`: gọi thẳng API để ghi cột trạng thái bị chặn.

Sự cố: nếu hộp việc không tự cập nhật, tải lại trang rồi nói tiếp; nếu tệp tải lên báo lỗi, bỏ bước tệp.

## 4. Đổi nghiệp vụ khi hệ thống đang chạy (8 đến 12 phút)

**Cửa sổ `admin`, bảng điều khiển `/console`.**

1. Tab "Mô hình đang chạy": mô hình `onestop` đang ở một phiên bản, có dữ liệu thật.
2. Tab "Soạn và áp dụng": mở phiên bản đang chạy, thêm một trường tùy chọn, ví dụ `priority: {type: choice, label: Mức ưu tiên, options: [Thấp, Cao]}`.
3. Bấm "Xem kế hoạch": mức **An toàn**, áp dụng được ngay, không build lại.
   Bấm "Áp dụng".
4. Quay lại cửa sổ `judge_student`, tải lại: form tạo hồ sơ có trường mới, hồ sơ cũ còn nguyên.
5. Đổi tên một trường bằng `renamed_from: <tên cũ>`: kế hoạch hiện mức cần chuyển dữ liệu và nói rõ giữ nguyên số dòng.
6. Đặt `required: true` cho trường `needed_at`, mà các hồ sơ mẫu để trống: kế hoạch bị từ chối và nêu số dòng vi phạm kèm vài mã hồ sơ, hệ thống vẫn chạy phiên bản đang chạy.
7. Thư viện mẫu: nạp một mô hình khác họ quy trình (hỗ trợ kỹ thuật hoặc đặt phòng), chạy được ngay mà không sửa mã lõi.
   Mô hình đặt phòng có chế độ lịch.

Sự cố: nếu kế hoạch báo lỗi ở một dòng, đọc lỗi cho hội đồng (lỗi chỉ đúng dòng và cột là một phần của điểm nhấn), sửa rồi bấm lại; không áp dụng khi chưa rõ kế hoạch.

## 5. Vận hành và dữ liệu (12 đến 14 phút)

- **Thống kê:** từ danh sách hồ sơ của `judge_supervisor` bấm Thống kê: số hồ sơ theo trạng thái và đơn vị, thời gian xử lý trung bình, tỷ lệ đóng đúng hạn khi mô hình có hạn, và mục Báo cáo theo trường gộp hồ sơ theo một trường hoặc theo tháng.
- **Lịch sử chống sửa:** `dxos history verify` báo chuỗi nguyên vẹn.
  Nếu có thời gian, nói rằng sửa một dòng lịch sử bằng SQL sẽ bị chỉ ra dòng đầu tiên bị đổi.
- **Lưu trữ dài hạn:** `dxos archive onestop --out /tmp/archive` ghi hồ sơ đã đóng và lịch sử ra tệp Parquet, kèm manifest để kiểm lại bằng `dxos archive check`.
- **Kiểm tự động:** nhắc rằng toàn bộ tám mục và các lệnh `dxos` được kiểm bằng `check_*.py` ở CI.

Sự cố: lần chạy `dxos archive` đầu cần tải extension DuckDB từ mạng; nếu không có mạng, bỏ bước này.

## 6. Định hướng (14 đến 15 phút)

Nói thẳng phần chưa có, theo [Việc tồn đọng](./dynamic-core/BACKLOG.md):

- Phần AI mới có trợ lý tùy chọn đề xuất giá trị một trường, người dùng xác nhận trước khi lưu; chưa có hỏi đáp trên tri thức nội bộ và tác tử nghe sự kiện.
- Hồ dữ liệu là kho Parquet trong SeaweedFS; `dxos archive query` đọc một lần chạy bằng DuckDB mà không cần cấp quyền cơ sở dữ liệu.
- Không có bản sao số: số đo của thiết bị vào lõi thành hồ sơ sự cố, lõi không giữ trạng thái thời gian thực của thiết bị.

Kết bằng một câu: đổi nghiệp vụ là đổi tệp, không đổi mã.

## 7. Ứng dụng của người ngoài (tùy chọn)

Phần này chứng minh lõi là nền tảng cho ứng dụng tầng trên: một ứng dụng bán hàng không liên quan đến dự án thêm hai quy trình bằng hai tệp mô hình.
Các bước từ máy sạch ở [`examples/appliance-store`](../examples/appliance-store/README.md); làm trước theo README trên máy trình diễn.

- **Cửa sổ ứng dụng (`nhanvien`):** mở một đơn bán, gửi yêu cầu đổi trả; trạng thái "Chờ duyệt" kèm liên kết "Mở trên DX-OS".
- **Cửa sổ `manager` ở DX-OS:** mở liên kết, duyệt phiếu ở hộp việc; quay lại ứng dụng thấy "Đã duyệt" mà không bấm gì.
- **Bảo hành:** gửi yêu cầu bảo hành, để quá hạn xử lý (2 phút) rồi mở lại trang ở ứng dụng: nhãn "Quá hạn" hiện ra (khi bật webhook thì hiện không cần mở trang).
- **Vì sao không tự viết (ba phép thử ở mục "Vì sao không tự viết vòng duyệt" của README, mỗi phép chừng một phút):** tài khoản máy của ứng dụng không tự duyệt được phiếu của mình (403), sửa một dòng lịch sử thì `dxos history verify` chỉ ra dòng đó, đổi quy trình có kế hoạch và mức rủi ro (`SAFE`, `LOSS`, từ chối khi còn phiếu mở). Chọn hai phép nếu thiếu thời gian.
- **Điều cần nói:** ứng dụng không có mã quy trình, phân quyền hay lịch sử; phần tích hợp là một mẫu dùng nguyên, một tệp mở rộng mẫu, vài biến môi trường và 109 dòng mã.

Sự cố: nếu ứng dụng không cập nhật trạng thái, kiểm `DXOS_BASE_URL` và `extra_hosts`; khi bật webhook thì mở tab Hàng đợi sự kiện ở `/console` để xem thông điệp bị dừng, kiểm địa chỉ trong `deploy/webhooks/targets.yml`, rồi bấm Thử lại.

## Xử lý sự cố chung

| Sự cố | Cách xử lý |
|---|---|
| Không đăng nhập được một tài khoản | Chạy lại `dxos demo` để đặt lại mật khẩu và in mật khẩu mới |
| Hồ sơ mẫu chưa có hồ sơ quá hạn | Bình thường nếu mới chạy `dxos demo`; bỏ ý này hoặc dùng số liệu thống kê |
| Một thành phần ngừng | `dxos status`, `dxos reconcile`; hệ thống tự chạy lại container bị xóa |
| Mất mạng | Mọi bước ngoài `dxos archive` lần đầu chạy được không cần Internet |
| Sau buổi trình diễn | `dxos demo remove` gỡ bốn tài khoản thử; hồ sơ mẫu ở lại |
