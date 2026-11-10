# Ma trận bằng chứng

Tài liệu này trả lời một câu hỏi: tính năng nào của lõi đã xong thật, căn cứ vào đâu.
Mỗi yêu cầu của đề và mỗi tính năng của lõi được xếp một mức, kèm cách kiểm, bước CI, lệnh chạy lại và giới hạn đã biết.
Cách chạy từng bài kiểm ở [`docs/TESTING.md`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/docs/TESTING.md); tài liệu này không chép lại kết quả của từng lượt CI, xem tab Actions của repository.
Việc chưa làm và giới hạn chấp nhận ở [Việc tồn đọng](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/docs/dynamic-core/BACKLOG.md).

## Các mức

| Mức | Điều kiện |
|---|---|
| **Hoàn thiện** | Đủ cả bốn: (1) có kiểm tự động khẳng định đúng sai; (2) kiểm đó chạy trong CI trên pull request; (3) chạy lại được bằng một lệnh; (4) không còn giới hạn đã ghi nào chặn luồng chính |
| **Dùng được** | Chạy đầu-cuối, nhưng thiếu một trong (1) đến (3): kiểm chỉ chạy tay hoặc chỉ chạy khi bật thêm cấu hình, hoặc chưa kiểm trên bản phát hành thật |
| **Minh họa** | Có mã và có kiểm, nhưng chủ đích chỉ để chứng minh năng lực, bằng dữ liệu hoặc thiết bị giả lập |
| **Một phần** | Mới có một phần phạm vi đã định |
| **Chưa làm** | Chưa có mã trong repository |

## Bốn loại bằng chứng

| Loại | Chạy ở đâu | Có trong CI |
|---|---|---|
| Test đơn vị và tích hợp của backend | `cd backend && ./mvnw verify`; một số test dùng Testcontainers nên cần Docker | Có, job `Build and test the backend` của workflow `Backend` trên mọi pull request |
| Script kiểm trên stack thật | `python3 deploy/dev/check_<tên>.py` trên stack rỗng dựng bằng `docker compose up -d --build` | Có, job `API end-to-end` của workflow `Platform` trên mọi pull request |
| Kiểm bằng trình duyệt | Một kịch bản Playwright của bảng điều khiển ở `e2e/`; các màn hình còn lại kiểm tay theo `docs/TESTING.md`, mục 6 | Kịch bản Playwright có, job `Browser end-to-end` của workflow `Platform`; kiểm tay không |
| Bản triển khai công khai | VPS của đội | Không; hiện giữ bản `v1.1.1` nên chưa có bằng chứng loại này cho lõi mới |

Dữ liệu của hai loại đầu do chính các script tạo ra trên stack rỗng; chưa có tổ chức nào dùng lõi với dữ liệu thật.
Job `API end-to-end` của workflow `Platform` chia hai nhóm, mỗi nhóm một stack rỗng: `model and process` (11 script) và `events and bootstrap` (2 script).
Mỗi script in `N/M passed` và thoát với mã 1 khi có kiểm hỏng; số kiểm của từng script xem ở kết quả của lượt CI.

## 1. Bốn dịch vụ bắt buộc của đề

| Yêu cầu | Tính năng của lõi | Kiểm | Lệnh | Mức | Giới hạn |
|---|---|---|---|---|---|
| Định danh và SSO | Keycloak; vai và đơn vị sinh từ mô hình; mỗi mô hình có client riêng nên hai mô hình dùng cùng tên vai không vào được nhau | `check_users.py` 41, `check_guards.py` 61, `check_conformance.py` 60 | `python3 deploy/dev/check_conformance.py` | Hoàn thiện | Đổi vai hoặc đơn vị có hiệu lực sau tối đa khoảng 5 phút vì đọc từ token đã ký |
| Cổng API | APISIX kiểm token, giới hạn tốc độ, định tuyến; route không kiểm token nằm trong danh sách công khai | `deploy/apisix/check_routes.py`; mọi script đi qua cổng 9080; `check_conformance.py` kiểm 401 khi thiếu token | `python3 deploy/apisix/check_routes.py` | Hoàn thiện | Giới hạn tốc độ chưa có kiểm riêng; lúc chạy `check_conformance.py` gặp 429 cho thấy giới hạn đang hoạt động |
| Dữ liệu có cấu trúc | Schema, ràng buộc và chỉ mục sinh từ mô hình trên PostgreSQL; bắt buộc, duy nhất, lựa chọn kiểm cả ở API lẫn cơ sở dữ liệu | `check_api.py` 51, `check_change.py` 65 | `python3 deploy/dev/check_api.py` | Hoàn thiện | Danh sách 10.000 dòng có lọc và sắp xếp đo ở phân vị 95 là 11 ms; chưa thử tải lớn hơn |
| Dữ liệu phi cấu trúc | Tệp đính kèm lưu ở SeaweedFS, siêu dữ liệu ở PostgreSQL, quét ClamAV, quyền theo quyền của hồ sơ | `check_process.py` (tải lên, tải về, gỡ), `check_demo.py` tiêu chí 3 (tệp EICAR bị chặn), job `Back up and restore` của workflow `Platform` (sao lưu và khôi phục kèm tệp) | `python3 deploy/dev/check_demo.py` | Dùng được | Quét mã độc tắt mặc định; kiểm tệp EICAR chỉ chạy khi bật `dxos av on`, và job `API end-to-end` của workflow `Platform` không bật nên phần này chưa nằm trong CI |
| Động cơ luồng công việc | Flowable nhúng, ba họ mẫu `approval`, `work_item`, `reservation`; trạng thái chỉ đổi qua quy trình, cùng giao dịch với việc ghi hồ sơ | `check_process.py` 69, `check_case.py` 76, `check_booking.py` 46, `check_recovery.py` 19 | `python3 deploy/dev/check_process.py` | Hoàn thiện | Họ phê duyệt có sẵn chỉ có một cấp; nhiều cấp tuần tự và điều kiện `when` khai báo được bằng `states` và `actions` của quy trình tự khai báo (kiểm tay trên stack tạm, chưa có kiểm tự động); không có trình thiết kế BPMN |
| Lõi không có giao diện | `dx-engine` chỉ phục vụ API; giao diện và portal là Tầng 2 | Mọi script chỉ gọi API; `ArchitectureTest` kiểm phụ thuộc giữa các lớp của `dx-engine`; `docs/TESTING.md` mục 7 | | Dùng được | Chưa có test khẳng định rằng dịch vụ lõi không trả trang nào |

## 2. Năng lực của lõi

| Tính năng | Kiểm | Lệnh | Mức | Giới hạn |
|---|---|---|---|---|
| Đọc và kiểm tệp mô hình, báo lỗi theo dòng và cột | `check_api.py` và `check_case.py` với bảy tệp sai mẫu ở `models/invalid/` | `python3 deploy/dev/check_api.py` | Hoàn thiện | |
| Xem trước và áp dụng mô hình, áp dụng lại không đổi gì, sửa phần lệch của bảng và Keycloak | `check_api.py`, `check_recovery.py` 19 | `python3 deploy/dev/check_recovery.py` | Hoàn thiện | Việc kiểm lệch chỉ chạy khi xem kế hoạch hoặc áp dụng, không chạy lúc khởi động |
| Đổi mô hình khi đã có dữ liệu: mức rủi ro, đổi tên giữ dữ liệu, dòng vi phạm, xác nhận khi mất dữ liệu, quay về | `check_change.py` 65, `check_guards.py` 61 | `python3 deploy/dev/check_change.py` | Hoàn thiện | Đổi người xử lý, hạn xử lý, quyền tự quyết hoặc đặt chỗ chỉ được khi thực thể không còn hồ sơ đang xử lý |
| Tiếp tục hoặc hủy bỏ lần áp dụng dừng giữa chừng | `check_recovery.py`, `check_guards.py`; thẻ "Đang dừng dở" kiểm bằng Chrome | `python3 deploy/dev/check_guards.py` | Hoàn thiện | |
| API chung với quyền theo vai và theo dòng (`all`, `read`, `own`, `unit`, `assigned`, `owner_via`) | `check_api.py`, `check_case.py` | `python3 deploy/dev/check_case.py` | Hoàn thiện | Không có quyền theo từng trường |
| Chặn lách quy trình: không ghi được `status` qua API dữ liệu | `check_demo.py` tiêu chí 4, `check_conformance.py` | `python3 deploy/dev/check_conformance.py` | Hoàn thiện | |
| Quy tắc chung của quy trình: lý do khi từ chối hoặc trả lại, rút lại, người tạo không tự quyết, hạn và quá hạn | `check_process.py`, `check_case.py` | `python3 deploy/dev/check_process.py` | Hoàn thiện | |
| Đặt chỗ theo khung giờ có sức chứa | `check_booking.py` 46 | `python3 deploy/dev/check_booking.py` | Hoàn thiện | Kéo thả trên lịch kiểm tay bằng Chrome |
| Tự đóng hồ sơ đã xử lý của họ `work_item` | `check_process.py` | `python3 deploy/dev/check_process.py` | Hoàn thiện | |
| Hành động hoàn tất qua outbox, thử lại khi lỗi | `check_process.py`; `AdvisoryJobLockIntegrationTest` | `python3 deploy/dev/check_process.py` | Hoàn thiện | Chỉ có `add_to_group` và `remove_from_group` |
| Chống ghi đè khi sửa đồng thời (`version`, 409) | `check_process.py` | `python3 deploy/dev/check_process.py` | Hoàn thiện | |
| Mã lý do cho lỗi 403 và 409 | `check_case.py`, `check_process.py`, `check_booking.py` | `python3 deploy/dev/check_case.py` | Dùng được | `STILL_REFERENCED` và một số mã của 403 chưa có kiểm riêng |
| Lịch sử hồ sơ kèm giá trị cũ và mới của từng trường | `check_case.py` | `python3 deploy/dev/check_case.py` | Hoàn thiện | |
| Chuỗi hash của lịch sử, điểm neo, lệnh kiểm | `check_events.py` 24 | `python3 deploy/dev/check_events.py` | Hoàn thiện | Phát hiện sửa chứ không ngăn sửa; người sửa được cả cơ sở dữ liệu lẫn kho thì chỉ bị lộ khi so với hash bên nhận giữ |
| Webhook theo mô hình: chữ ký, thứ tự theo hồ sơ, thử lại, lấy lại phần đã lỡ | `check_events.py`, `WebhookSenderTest` | `python3 deploy/dev/check_events.py` | Hoàn thiện | Dạng `chat` chưa thử với Mattermost và Slack thật |
| Bên nhận so hash lịch sử (`witness.py`) | `check_events.py`; chạy dòng lệnh kiểm tay | `python3 examples/history-witness/witness.py compare` | Hoàn thiện | |
| Bộ kiểm chung cho mô hình mới | `check_conformance.py` 60 | `python3 deploy/dev/check_conformance.py` | Hoàn thiện | |
| Quản trị người dùng: gán vai và đơn vị, tạo, khóa, đặt lại mật khẩu, nhật ký | `check_users.py` 41, `KeycloakAdminClientTest`; tab Người dùng kiểm bằng Chrome | `python3 deploy/dev/check_users.py` | Hoàn thiện | Thay đổi làm thẳng ở Keycloak Admin Console không qua đây |
| Thống kê theo thực thể, kể cả thời gian xử lý | `check_process.py` | `python3 deploy/dev/check_process.py` | Hoàn thiện | Chưa có tỷ lệ hoàn tất trong hạn |
| Tài liệu khớp với API thật | `check_docs.py` 3 | `python3 deploy/dev/check_docs.py` | Hoàn thiện | |
| Nạp mô hình lúc khởi động (`dxos start <tệp>`) | `check_bootstrap.py` 8; job `Install from the release archive` của workflow `Platform` | `python3 deploy/dev/check_bootstrap.py` | Dùng được | Chưa kiểm bằng image phát hành thật của bản `2.0.0` |
| Cài một lệnh, nâng cấp, gói sai SHA-256 bị từ chối | Job `Install from the release archive` của workflow `Platform` | `curl -fsSL .../dxos-install.sh \| bash` | Dùng được | Chưa chạy trên máy sạch với image phát hành thật |
| Sao lưu và khôi phục kèm tệp đính kèm | Job `Back up and restore` của workflow `Platform` | `dxos backup` | Hoàn thiện | Một bản backend, một máy |
| Màn hình giám sát và cảnh báo | `ServiceProbeTest` 4, `AlertJobTest` | `cd backend && ./mvnw verify` | Dùng được | Không có kiểm đầu-cuối của giao diện giám sát |
| Bảng điều khiển và giao diện react-admin | `npx tsc --noEmit` trong workflow `Frontend`; `e2e/tests/console.spec.ts` (quản trị viên đăng nhập, áp dụng một mẫu, thấy mô hình chạy) trong job `Browser end-to-end` của workflow `Platform`; còn lại kiểm tay bằng Chrome | `cd e2e && npx playwright test` trên stack đang chạy | Dùng được | Tự động hóa mới có luồng bảng điều khiển; hộp việc, lịch đặt chỗ và trang hồ sơ kiểm tay |
| Ví dụ kết nối thiết bị (`itot-simulator`) | Kiểm tay: gửi lại cùng quan sát in `duplicate`, không tạo thêm hồ sơ | `python3 examples/itot-simulator/simulator.py run` | Minh họa | Thiết bị và dữ liệu giả lập |
| Hộp việc thời gian thực (`GET /api/v1/inbox/stream`) | Kiểm tay theo `docs/TESTING.md` mục 6, mục 23; `mvnw verify` kiểm biên dịch và test của dịch vụ | Mở hộp việc ở hai cửa sổ rồi nộp một hồ sơ | Dùng được | Không có kiểm tự động của kênh; chạy một bản backend nên kênh chỉ báo thay đổi do chính bản đó ghi |
| Tài khoản và hồ sơ mẫu để dùng thử (`dxos demo`) | Kiểm tay theo `docs/TESTING.md` mục 6, phần lệnh `dxos` mục 6 | `dxos demo` | Dùng được | Không có kiểm tự động; hồ sơ quá hạn chỉ có sau ba ngày kể từ lần nộp |

## 3. Giấy phép, chuỗi cung ứng, phát hành

| Tính năng | Kiểm | Lệnh | Mức | Giới hạn |
|---|---|---|---|---|
| Header SPDX ở mọi tệp mã, giấy phép của mọi gói trong SBOM, danh mục image và mô hình | Job `Check licenses` của workflow `Compliance` | `python3 .github/scripts/check_licenses.py` | Hoàn thiện | Ngoại lệ có lý do ở `.github/dependency-exceptions.json` |
| Quét bí mật trong cây mã và lịch sử | Job `Scan for secrets` của workflow `Compliance` (gitleaks) | | Hoàn thiện | |
| Lỗ hổng của dependency và image | workflow `Dependencies` (Trivy, SBOM của Maven), chạy theo lịch | | Hoàn thiện | Image bên thứ ba chỉ báo cáo, không chặn |
| Gói phát hành `.tar.gz`, `.sha256`, SBOM CycloneDX | Job `Install from the release archive` của workflow `Platform` dựng gói của commit và chạy `check_archive.sh` ở mọi pull request; workflow `Release Package` chạy lại kiểm đó rồi mới đăng khi đẩy tag `v*` | `.github/scripts/check_archive.sh <gói> <phiên bản>` | Dùng được | Tag `v2.0.0` chưa tạo; bản đã phát hành gần nhất là `v1.1.1` |
| SBOM khớp với tệp khóa | Job `Check SBOMs against lockfiles` của workflow `Compliance` dựng lại SBOM của `frontend` và `website` rồi so với `sbom/` | `python3 .github/scripts/check_sbom_fresh.py` | Hoàn thiện | SBOM của backend so bằng `check_sbom_fresh.py` với kết quả của `cyclonedx-maven-plugin`, chạy tay ngày 06/11/2026: 221 gói, 0 khác biệt |
| Tệp bắt buộc có đủ, shell script đúng cú pháp và chạy được | Job `Check required files and scripts` của workflow `Compliance` | | Hoàn thiện | |
| Workflow đúng cú pháp | Job `Lint workflows` của workflow `Compliance` | | Hoàn thiện | |
| Image theo phiên bản trên GHCR | Workflow `Release Images` | | Dùng được | Chưa có image `v2.0.0` |
| Compose dừng khi thiếu bí mật, tệp mẫu hợp lệ | Job `Validate the Compose file` của workflow `Platform` | `docker compose config` | Hoàn thiện | |

## 4. Chưa làm hoặc ngoài phạm vi

| Nội dung | Mức | Ghi chú |
|---|---|---|
| Hỏi đáp sinh câu trả lời và tác tử AI có người duyệt | Chưa làm | Cần mô hình ngôn ngữ đạt tiêu chuẩn mở hoàn toàn, chưa chọn |
| Ứng dụng Tầng 2 phủ chữ I của H-P-D-I | Chưa làm | |
| Hồ dữ liệu, bản sao số (hai nguyên lý đầu của đề) | Chưa làm | Ngoài phạm vi bản dự thi, nói rõ khi trình bày |
| Nhiều bản backend sau gateway | Chưa làm | Chạy một bản; hai bản thì danh sách mô hình giữ lệch nhau sau một lần áp dụng |
| Triển khai công khai cho lõi mới | Chưa làm | VPS giữ `v1.1.1`; bản mới không có đường nâng cấp dữ liệu nên phải dựng lại |

## 5. Kiểm lại

Chạy ở gốc repository, trên stack rỗng:

```bash
deploy/make_env.sh
cd deploy && docker compose up -d --build && cd ..
for c in api recovery case change process booking demo guards users docs conformance; do python3 deploy/dev/check_$c.py; done
python3 deploy/dev/check_events.py
python3 deploy/dev/check_bootstrap.py
```

`check_events.py` và `check_bootstrap.py` khởi động lại backend nhiều lần nên chạy ở stack riêng, như hai nhóm của job `API end-to-end` của workflow `Platform`.
Kiểm tĩnh:

```bash
cd backend && ./mvnw verify
cd frontend && npx tsc --noEmit
python3 .github/scripts/check_licenses.py
python3 deploy/apisix/check_routes.py
```

Một tính năng đổi mức thì sửa dòng của nó trong bảng ở cùng pull request với thay đổi đó.
