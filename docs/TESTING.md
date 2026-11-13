# Hướng dẫn kiểm thử

Tài liệu này nói cách kiểm lõi, từng loại kiểm chạy ở đâu và những gì còn phải kiểm tay.
Kết quả của từng lần chạy nằm ở tab Actions của repository, tài liệu này không chép lại.
Mọi lệnh chạy từ gốc repo, trừ khi ghi khác; lệnh dựng stack ở [`README.md`](../README.md).
Mức hoàn thiện của từng tính năng và bài kiểm của nó ở [`website/docs/evidence-matrix.md`](../website/docs/evidence-matrix.md).

## 1. Chuẩn bị

| Cần có | Ghi chú |
|---|---|
| Docker và Docker Compose v2 | Backend `verify` dùng Testcontainers nên cần Docker chạy |
| JDK 21 | Dùng Maven wrapper trong repo; đặt `JAVA_HOME` về JDK 21 nếu máy mặc định bản khác |
| Node 22 trở lên | Cho `frontend`, `website` và `e2e` |
| Python 3 | Cho các script kiểm ở `deploy/dev` |

## 2. Các loại kiểm

| Loại | Lệnh | Chạy ở CI |
|---|---|---|
| Định dạng, biên dịch, test của backend | `cd backend && ./mvnw verify` | Workflow `Backend` |
| Kiểu của web | `cd frontend && npx tsc --noEmit` | Workflow `Frontend` |
| Giấy phép và header | `python3 .github/scripts/check_licenses.py`; `pipx run --spec reuse==6.3.0 reuse lint` | `Check licenses` của `Compliance` |
| Giấy phép của dự án gốc | `GITHUB_TOKEN=... python3 .github/scripts/check_upstream_licenses.py` | `Check upstream licenses` của `Compliance` |
| SBOM khớp tệp khóa | `python3 .github/scripts/check_sbom_fresh.py` | `Check SBOMs against lockfiles` của `Compliance` |
| Route của gateway | `python3 deploy/apisix/check_routes.py` | `Validate the gateway routes` của `Platform` |
| Compose hợp lệ, dừng khi thiếu bí mật | `docker compose config` trong `deploy/` | `Validate the Compose file` của `Platform` |
| Gói phát hành và bộ cài | `.github/scripts/check_archive.sh <gói> <phiên bản>` | `Install from the release archive` của `Platform` |
| Sao lưu và khôi phục | `deploy/backup.sh` rồi `verify` | `Back up and restore` của `Platform` |
| API trên stack thật | Các script `deploy/dev/check_*.py`, mục 4 | `API end-to-end` của `Platform`, ba nhóm, một nhóm chạy ở chế độ lõi và giữ quét mã độc |
| Bề mặt của gateway | `python3 deploy/dev/check_surface.py` | `Gateway surface` của `Platform`, hai chế độ |
| Trình duyệt | Xem [`e2e/README.md`](../e2e/README.md) | `Browser end-to-end` của `Platform` |
| Trang tài liệu | `cd website && npm ci && npm run build` | `Build the docs site` của `Docs` |
| Tải đồng thời và cấu hình nhỏ nhất | `python3 deploy/dev/load_test.py` trên stack tạm; các bước và kết quả ở [`SIZING.md`](SIZING.md) | Không |
| Kiểm tay | Mục 6 | Không |

`./mvnw verify` sau khi đổi tài nguyên nên chạy `clean verify`: tài nguyên cũ còn trong `target/` làm Flyway thấy hai migration cùng phiên bản.
`./mvnw spotless:apply` sửa định dạng khi `spotless:check` báo lỗi.
Test của client Keycloak, bộ gửi webhook và bộ dò dịch vụ dùng WireMock, không cần Docker; test chậm gắn `@Tag("slow")`, bỏ qua bằng `-DexcludedGroups=slow`.

## 3. Dựng stack

```bash
deploy/make_env.sh                          # sinh deploy/.env
cd deploy && docker compose up -d --build
docker compose ps                           # mọi service healthy
```

Dựng lại từ đầu: `./reset.sh` trong `deploy/` (xóa `deploy/data`).
Image `dxos-*:local` trên máy chỉ mới theo lần `docker compose build` gần nhất, nên build lại sau khi đổi mã.

Kiểm trên một stack riêng thì không đụng stack chính:

- Dùng tệp env riêng và project Compose riêng, đổi cổng gateway và Postgres trong tệp env đó (`APISIX_HOST_PORT`, `POSTGRES_HOST_PORT`).
- Các script đọc `DXOS_ENV_FILE`, `DXOS_BASE_URL` và `DXOS_PROJECT` (tên project Compose); đặt đủ ba biến thì lệnh `docker compose` của script chỉ chạm project đó.
- Xóa project, volume và thư mục dữ liệu của nó sau khi kiểm.

Các script gọi Keycloak Admin API qua gateway, mà gateway đóng `/admin` mặc định: đặt `KEYCLOAK_ADMIN_CONSOLE=true` trong env của stack kiểm rồi `docker compose up -d apisix-config` và `docker compose restart apisix`.
Chế độ lõi thì đặt `DXOS_MODE=core` và `COMPOSE_PROFILES=av` trong env đó, hoặc dùng `dxos start --core`.

Cổng 9080 hoặc 5432 bị chiếm thì đổi `APISIX_HOST_PORT`, `POSTGRES_HOST_PORT` trong `deploy/.env`.

## 4. Kiểm API trên stack

Chạy trên stack mới dựng, cơ sở dữ liệu rỗng, vì script áp dụng mô hình mẫu từ trạng thái rỗng.
Mỗi script phụ thuộc các script đứng trước trong cùng nhóm, nên chạy đúng thứ tự:

```bash
for c in api recovery case change process booking demo guards users docs conformance cli browser; do
  python3 deploy/dev/check_$c.py
done
```

`check_events.py` và `check_bootstrap.py` khởi động lại backend nhiều lần, nên chạy ở một stack rỗng khác, theo thứ tự này:

```bash
python3 deploy/dev/check_events.py
python3 deploy/dev/check_bootstrap.py
```

| Script | Kiểm gì |
|---|---|
| `check_api.py` | Áp dụng mô hình mẫu vào hệ thống rỗng; tệp sai báo dòng và cột; quyền theo vai và theo dòng; chặn ghi `status`; vai `report_ro`; tên trường và toán tử có SQL; 10.000 dòng có lọc và sắp xếp |
| `check_recovery.py` | Quyết định lỗi sau bước quy trình thì hồ sơ và quy trình cùng quay về; áp dụng bị Keycloak làm gián đoạn rồi áp dụng lại |
| `check_case.py` | Trạng thái và hành động, đơn vị, giao việc, hộp việc, dòng lịch sử kèm giá trị cũ và mới, mã lý do của lỗi 403 và 409 |
| `check_change.py` | Đổi mô hình khi đã có dữ liệu: mức rủi ro, đổi tên giữ dữ liệu, dòng vi phạm, quay về |
| `check_process.py` | Các họ mẫu, quy tắc chung của quy trình, hạn và quá hạn, hành động hoàn tất qua outbox, tệp đính kèm, thống kê, số đo chất lượng, chống ghi đè |
| `check_codes.py` | Mã hồ sơ: mã đầu tiên của năm, đếm riêng theo thực thể và mô hình, hai mươi hồ sơ tạo cùng lúc, ghi và sửa mã bị từ chối, tra theo mã, metadata và OpenAPI, thêm trường mã vào hồ sơ có sẵn (chuyển năm), đổi khuôn, hết số |
| `check_booking.py` | Họ `reservation`, danh mục của bảng điều khiển, mọi mẫu áp dụng được |
| `check_demo.py` | Tám tiêu chí của kịch bản trình diễn |
| `check_guards.py` | Người giữ vai, vai của nền tảng, bỏ vai, xóa thực thể còn hồ sơ đang chạy, lần áp dụng dừng giữa chừng (tiếp tục và hủy bỏ) |
| `check_browser.py` | Giao diện trình duyệt ngoài: `dxos client browser add` tạo public client PKCE S256, origin vào `CORS_ALLOWED_ORIGINS` và được CORS nhận, `list` không in secret, redirect không an toàn bị từ chối trước khi đổi Keycloak, đăng nhập PKCE gọi được API, `remove` xóa client |
| `check_users.py` | Quản trị người dùng: gán và gỡ vai và đơn vị, tạo, khóa, đặt lại mật khẩu, audit |
| `check_docs.py` | Mọi endpoint các tài liệu nêu có trong OpenAPI của backend đang chạy, đúng phương thức |
| `check_conformance.py` | Rào chắn của lõi giữ với mọi mô hình đã áp dụng, hoặc các mô hình nêu tên: không ghi được `status`, không token thì 401, không đọc được dữ liệu mô hình khác |
| `check_cli.py` | Lệnh `dxos lint` cho đúng những lỗi mà `plan` trả với bảy tệp ở `models/invalid/` và chấp nhận các mẫu, `dxos conformance` qua với mô hình đã áp dụng và hỏng với mô hình chưa áp dụng, lệnh `dxos plan` và `apply --accept` (tệp giống, đổi an toàn, đổi mất dữ liệu bị từ chối khi `--accept` thấp hơn mức của kế hoạch), `model upgrade` xem diff, ghi nguyên tử, chạy lặp và từ chối format tương lai, lịch sử ghi tài khoản của lệnh, `dxos archive` và `archive check` (sửa một byte Parquet thì mã 1), `archive --store` vào SeaweedFS, `archive check --store`, `archive query` chỉ đọc một run với tham số, `archive list` và `archive auto` không ghi trùng, bộ mô phỏng IT/OT gửi lại cùng quan sát thì in `duplicate` và không thêm hồ sơ; cần Docker; tự áp dụng một mô hình riêng |
| `check_events.py` | Webhook: thứ tự, chữ ký, thử lại, nội dung không lộ, nhắc hạn; chuỗi hash của lịch sử và bên nhận so hash |
| `check_bootstrap.py` | Tệp mô hình lúc khởi động: `applied`, `unchanged`, `skipped`, `failed` |
| `check_surface.py` | Không cần tài khoản, chạy được ở cả hai chế độ: `/v3/api-docs` trả JSON; `/swagger-ui*` và `/admin*` trả 404 không phải HTML (`/admin` mở khi `KEYCLOAK_ADMIN_CONSOLE=true`); đăng nhập Keycloak vẫn phục vụ; backend gọi được Keycloak Admin API; chế độ `core` không chạy container và không phục vụ trang nào của Tầng 2 |

Các script tạo client Keycloak `dxos-check` (cho phép đăng nhập bằng mật khẩu, chỉ dùng cho máy dev) và các người dùng thử như `admin1`, `req1`, `keeper1`.
Vai của mô hình được gán ở client `dxos-m-<mô hình>`, vai `ADMIN` ở realm.
Mỗi kiểm in một dòng `PASS` hoặc `FAIL` kèm nhãn, cuối script in `N/M passed`, và script thoát với mã 1 khi có kiểm hỏng.

Điều kiện đặc biệt:

- Tiêu chí 3 của `check_demo.py` (tệp sạch tải lên và tải về, tệp EICAR bị chặn và không lưu, tệp bị từ chối khi ClamAV dừng) chỉ chạy khi bật quét, như `deploy/.env` mặc định; khi tắt quét (`dxos av off`) script in `NOTE` rồi bỏ qua phần này.
- Đặt `DXOS_REQUIRE_SCAN=1` thì stack không quét làm script báo `FAIL`; nhóm chế độ lõi của `API end-to-end` đặt biến này, còn các nhóm khác tắt quét để không tốn thêm khoảng 1 GB RAM.
- Với quét bật, đợi `clamav` khỏe (`docker compose up -d --wait clamav`) trước khi chạy script, vì tệp tải lên bị từ chối khi ClamAV chưa nạp xong chữ ký.
- Một số kiểm cần backend chạy với biến gây lỗi có chủ ý (`ENGINE_FAULT_AFTER_DECISION=true` làm quyết định lỗi sau bước quy trình, `ENGINE_FAULT_APPLY_STEP=ENSURE_CLIENT` làm bước áp dụng có tên đó lỗi); các script tự bật rồi tắt bằng cách tạo lại service `backend`.
- Keycloak 26.8 trả 400 thay vì 401 cho mật khẩu sai; các kiểm về mật khẩu nhận cả hai.

## 5. Trình duyệt

Bài Playwright của bảng điều khiển nằm ở `e2e/`; cách chạy, biến cấu hình và nơi có ảnh chụp ở [`e2e/README.md`](../e2e/README.md).
Bài `phone.spec.ts` chạy ở cỡ điện thoại 390 px và kiểm bảng điều khiển cùng hộp việc không rộng hơn màn hình; hộp việc và danh sách có dữ liệu kiểm tay ở mục 6.
CI chạy bài này ở job `Browser end-to-end` của workflow `Platform`.

## 6. Kiểm tay trước khi phát hành

Những mục dưới chưa có kiểm tự động.

Giao diện, đăng nhập bằng `req1` (vai `BORROWER`), `keeper1` (vai `KEEPER`) và `admin1` (vai `ADMIN`) của script ở mục 4:

1. Mở `http://localhost:9080/`, đăng nhập bằng `BORROWER`: menu có Thiết bị, Phiếu mượn, Ghi chú phiếu mượn; danh sách Thiết bị chỉ đọc, danh sách Phiếu mượn chỉ có phiếu của mình, biểu mẫu tạo có đủ trường theo kiểu của mô hình.
   Với mẫu có trường `code` (mẫu `onestop` có "Số hồ sơ"): biểu mẫu tạo và sửa không có ô nhập mã; sau khi lưu, trang chi tiết nêu mã trên đầu và ở mục Thông tin kèm nút sao chép mã (bấm thì dán ra đúng mã); danh sách có cột mã đầu tiên và ô tìm theo một phần của mã, gõ "00002" chỉ còn hồ sơ có mã chứa số đó; ở màn hình điện thoại mã hiện trong thẻ của hồ sơ.
2. Đăng nhập bằng `KEEPER`: thấy mọi phiếu; phiếu `Chờ duyệt` có nút Duyệt và Từ chối, bấm Duyệt thì trạng thái thành `Đã duyệt`.
3. Bundle web không chứa tên thực thể hay vai nào của mô hình.
4. Mọi chữ trên giao diện là tiếng Việt: nút, bộ lọc, thông báo bắt buộc nhập, lỗi tệp mô hình theo dòng và cột, nhãn các loại thay đổi trong kế hoạch.
5. Chế độ tối hiện đủ hộp việc, danh sách, chi tiết hồ sơ và bảng điều khiển.
   Ở màn hình điện thoại (390 px) hộp việc và danh sách hiện mỗi hồ sơ là một thẻ, tên trang trên thanh đầu không bị công tắc ngôn ngữ che, biểu mẫu tạo và các nút hành động dùng được, và không trang nào tràn ngang.
6. Tab Hàng đợi sự kiện có ba danh sách Lỗi, Chờ gửi, Đã gửi; thông điệp lỗi hiện lỗi gần nhất và nút Thử lại ngay.
7. Tải một tệp `.html` lên trường tệp: bị từ chối (400); tải một tệp `.pdf` rồi mở link tải: trình duyệt lưu thành tệp; gỡ tệp rồi mở lại link cũ: 404.
8. Tab Soạn và áp dụng: sơ đồ quy trình vẽ lại sau 5 giây không gõ và đổi theo `deadline`, `auto_close` và `after` của tệp; tệp sai thì báo lỗi kèm dòng và cột và giữ sơ đồ đúng gần nhất; sơ đồ mở to thành hộp thoại; vai `ADMIN` khai báo trong tệp báo lỗi tiếng Việt kèm dòng và cột, không có nút Áp dụng; vai trùng tên với mô hình khác thì áp dụng được.
9. Thực thể còn hồ sơ đang xử lý: thêm một hành động vào quy trình hoặc đổi hạn xử lý rồi bấm Xem kế hoạch báo không đổi được và không có nút Áp dụng; rút hết hồ sơ rồi xem lại thì có nút Áp dụng.
10. Dừng Keycloak (`docker compose stop keycloak`), áp dụng một thay đổi, rồi bật lại: thẻ mô hình hiện nhãn "Đang dừng dở" kèm nút Tiếp tục áp dụng và Hủy bỏ; Tiếp tục thì phiên bản tăng, Hủy bỏ hỏi xác nhận rồi đưa mô hình về phiên bản đang chạy. Với lần áp dụng đầu dừng dở (backend chạy với `ENGINE_FAULT_APPLY_STEP=ENSURE_CLIENT`) thẻ hiện "Chưa có phiên bản" và Hủy bỏ làm thẻ biến mất.
11. Tab Người dùng: tìm, gán và gỡ vai và đơn vị, tạo người dùng (mật khẩu tạm phải đổi ở lần đăng nhập đầu), khóa, mở, đặt lại mật khẩu; hộp thoại người dùng hiện lịch sử thay đổi, không có mật khẩu nào.
12. Lịch đặt chỗ: kéo một lượt để đổi giờ, kéo cạnh dưới để đổi giờ kết thúc; lượt trùng giờ bị đưa về chỗ cũ kèm lỗi.
13. Trang chi tiết hồ sơ hiện lịch sử với `Nhãn: cũ → mới` của từng trường; sửa hồ sơ đã bị người khác đổi thì báo "Bản ghi vừa được người khác thay đổi".
14. Menu người dùng, mục "Tài khoản của tôi", mở Account Console của Keycloak ở tab mới, đã đăng nhập.
15. Lần chạy đầu trên máy sạch: `admin` đăng nhập bằng mật khẩu tạm, bị buộc đổi mật khẩu, vào bảng điều khiển, áp dụng mẫu đầu tiên.
16. Tab Soạn và áp dụng, chế độ Điền form: các tab Chung, Vai, Đơn vị, Thực thể gọn trên một màn hình; Thực thể có tab con Chung, Trường, Quyền, Quy trình; vai, trường và thực thể chọn từ danh sách đã khai báo.
    Ở Quy trình, thêm vai xử lý thứ hai chưa có quyền xem hồ sơ: form cảnh báo và sơ đồ báo lỗi kèm dòng và cột; cho vai đó quyền thì hết lỗi.
    Thêm một hành động, đổi mã trạng thái, bỏ một hành động của khung (hiện "Đã bỏ khỏi quy trình này" kèm nút Khôi phục), chọn "Tự khai báo, không dùng khung": sơ đồ vẽ lại theo tệp và tệp YAML giữ nguyên các dòng chú thích.
17. Nút "Phạm vi có thể làm" ở mỗi phần của form và bảng "Viết được gì trong tệp" bên phải trình soạn: có mục hệ thống chấp nhận gì, mở rộng được gì, tự tạo được gì, tên trạng thái và hành động của từng khung; rê chuột lên một khóa trong YAML hiện giải thích tiếng Việt.
18. Quy trình tự khai báo khi còn hồ sơ đang mở: đổi nhãn một hành động rồi áp dụng được; thêm hoặc bỏ trạng thái, đổi `from`, `to`, `by` hay `when` của một hành động, hoặc đổi `initial` thì kế hoạch bị từ chối với câu tiếng Việt nêu số hồ sơ.
    Đổi `role` của một hành động hoặc danh sách `handler` rồi áp dụng: vai cũ không thực hiện được nữa, vai mới thực hiện được hồ sơ đang mở.
19. Bỏ `terminal` của một trạng thái cuối còn hồ sơ đã đóng: kế hoạch bị từ chối kèm số dòng vi phạm ở `status`; rút hết hồ sơ ở trạng thái đó thì áp dụng được.
20. Chi tiết hồ sơ theo vai: người chỉ giữ vai `AGENT` không thấy nút của hành động dành cho `LEAD`; hồ sơ đã giao thì chỉ người được giao thấy nút, người điều phối bấm "Bỏ giao" thì vai khác thấy lại.
    Bấm hành động có `require` khi trường còn trống: báo "Bắt buộc nhập trước khi thực hiện" kèm tên trường, hồ sơ giữ nguyên trạng thái; điền trường rồi bấm lại thì qua.
    Hồ sơ có quy trình không có nút Xóa ở form sửa; nút hành động không đặt nhãn hiện mã hành động và form nhắc điều đó.
21. Hành động có `when`: một trạng thái có `lead_approve` (`when: "amount <= 5000000"`), `lead_forward` (`when: "amount > 5000000"`) và `lead_reject` không có `when`.
    Hồ sơ 1.000.000: nút duyệt sáng, nút chuyển mờ kèm chú thích "Chỉ thực hiện khi Số tiền > 5.000.000"; hồ sơ 9.000.000 thì ngược lại; hồ sơ không có số tiền thì cả hai nút có `when` mờ, nút từ chối vẫn sáng.
    Gọi API của hành động đang mờ trả 409 `ERR_INVALID_STATE` với `data.reason` là `CONDITION_NOT_MET`; đúng 5.000.000 thì `<=` đạt.
    Trình soạn YAML không gạch chân `when`, ô "Chỉ khi" ở chế độ Điền form hiện đúng điều kiện, nhãn cạnh của sơ đồ ghi điều kiện.
22. Tỷ lệ đúng hạn: thực thể khai hạn ngắn, trang Thống kê hiện ô "Đúng hạn" với phần trăm và `n/N` hồ sơ đã đóng; thực thể không khai hạn không có ô này.
    Hồ sơ đóng sau hạn dù trước lần quét có dòng `overdue` đứng trước dòng quyết định và tính là muộn; hồ sơ đóng trước hạn không có dòng này; lần quét sau không ghi dòng thứ hai và outbox chỉ có một thông điệp.
    Hồ sơ quá hạn rồi được trả lại, nộp lại và duyệt vẫn tính là muộn; hồ sơ rút lại sau hạn không tính; hai quyết định đồng thời trên hai hồ sơ quá hạn không kẹt khóa; `GET /api/v1/history/verify` vẫn báo chuỗi nguyên vẹn.
    Số `closedOnTime` cộng `closedLate` bằng số hồ sơ đã quyết định và không rút lại khi đối chiếu bằng SQL, ở nhiều người xem và nhiều bộ lọc.
    Tệp mô hình có hành động tên `overdue`, `due_soon`, `created`, `updated`, `deleted`, `assigned`, `file_added`, `file_removed`, `completion_skipped`, `auto_closed` hay `escalated` bị từ chối ở bước kiểm, kèm dòng và cột; tên thường như `review` được nhận.
23. Hộp việc thời gian thực: mở hộp việc của một cán bộ ở một cửa sổ, ở cửa sổ khác cho sinh viên nộp một hồ sơ gửi đơn vị của cán bộ đó: số "Việc đang chờ" tăng trong khoảng hai giây mà không tải lại trang, ở mọi tab của cán bộ.
    Trang chi tiết đang mở của một hồ sơ đổi trạng thái khi người khác duyệt; form tạo hoặc sửa đang gõ dở không bị làm mới.
    Khởi động lại `backend`: sau vài giây tab đang mở nhận lại thay đổi mới.
    `GET /api/v1/inbox/stream` không có token thì 401, có token thì 200 `text/event-stream` kèm `: ping` mỗi 20 giây, và server đóng sau 600 giây.
24. Kiểu và quy tắc mới trên biểu mẫu: áp dụng mẫu Sổ tài sản; trường `boolean`, `date` và nhiều lựa chọn có ô nhập riêng; mã sai mẫu báo "Không đúng định dạng", giá trị ngoài khoảng bị từ chối, ghi chú bắt buộc khi loại là phần mềm và báo "Bắt buộc nhập khi"; hồ sơ lưu rồi hiện đủ ở chi tiết và lọc được theo từng trường; trường nhiều lựa chọn có `default` là danh sách thì hồ sơ tạo không gửi trường đó nhận danh sách ấy, và `required_when: "due >= 2026-01-31"` trên trường ngày chặn hồ sơ có `due` từ mốc đó mà để trống trường bắt buộc; ở chế độ Điền form, ô mặc định của trường nhiều lựa chọn chọn từ `options` và YAML ghi danh sách.
25. Khối `ui:`: menu hiện nhóm và biểu tượng khai báo; danh sách mở với đúng cột và thứ tự khai báo, nút Cột vẫn hiện đủ cột và lựa chọn của người dùng đè lên; biểu mẫu chia nhóm theo `ui.form` với nhóm Khác ở cuối và hiện lời gợi ý; đổi `ui:` khi còn hồ sơ mở vẫn áp dụng được.
26. Hành động hàng loạt: chọn nhiều hồ sơ chờ xử lý, thanh phía trên bảng hiện các hành động hợp lệ, bấm Duyệt thì hộp xác nhận hiện số hồ sơ và bảng kết quả báo từng hồ sơ; một hồ sơ đã bị người khác xử lý trước thì báo lý do và các hồ sơ còn lại vẫn xong; hành động cần ghi chú bắt buộc nhập ghi chú; chọn hơn 50 hồ sơ thì nút mờ; vai không có quyền xử lý không có ô chọn.
27. Tab Ứng dụng ngoài: đăng ký một ứng dụng với một vai của một mô hình, mã bí mật hiện một lần kèm nút sao chép; token lấy bằng mã đó đọc được mô hình đã gán, gọi bảng điều khiển hay quản trị người dùng thì 403; đổi mã thì mã cũ bị từ chối; đổi vai bằng nút Đổi vai thì mã bí mật cũ vẫn lấy được token, token mới mang vai mới còn token cũ giữ vai cũ đến khi hết hạn; xóa thì không lấy được token và hồ sơ đã tạo vẫn còn.
28. Tài liệu OpenAPI của mô hình: nhập vào một công cụ API hoặc trình sinh thư viện không lỗi; đổi một trường rồi áp dụng và quay về thì tài liệu đổi theo; người không có vai của mô hình nhận 404.
29. Bảng theo trạng thái: ở danh sách một thực thể có quy trình bấm nút Bảng theo trạng thái; mỗi trạng thái là một cột có tổng số, tối đa 10 thẻ, thẻ mở đúng chi tiết hồ sơ, "Xem cả N hồ sơ" mở danh sách lọc theo trạng thái đó; tải lại trang vẫn ở chế độ bảng; bộ lọc đang đặt áp cho mọi cột; ở màn hình 360 px trang không tràn ngang và các cột cuộn bên trong.
30. Xuất CSV: bấm Xuất CSV ở một danh sách có lọc; tệp `<thực thể>-<ngày>.csv` có BOM, một dòng tiêu đề và đúng số dòng của danh sách đã lọc; tạo hồ sơ có lý do `=1+1` hoặc `@SUM(A1)` thì ô trong tệp bắt đầu bằng dấu nháy; giá trị có dấu phẩy hoặc ngoặc kép được bao trong ngoặc kép; vai chỉ thấy hồ sơ của mình chỉ nhận được các dòng đó.
31. Khóa chống chạy lặp: gọi `POST /api/v1/data/{model}/{entity}` hai lần với cùng `Idempotency-Key` và cùng nội dung thì cả hai trả cùng `id`, lần hai có header `Idempotent-Replayed: true` và không có thêm hồ sơ; cùng khóa với nội dung khác hoặc với hành động khác thì 409 `IDEMPOTENCY_KEY_REUSED`; khóa sai khuôn thì 400; một yêu cầu lỗi không giữ khóa nên gửi lại được.
32. Danh mục dữ liệu: vào bảng điều khiển bằng ADMIN, mở tab Danh mục dữ liệu và chọn một mô hình có quy trình và tham chiếu; trang liệt kê vai và ứng dụng ngoài đi vào, nơi dữ liệu đi ra, các liên kết giữa thực thể, và mỗi thực thể có số bản ghi, quyền theo vai và danh sách trường. Đăng ký thêm một ứng dụng ngoài ở tab Ứng dụng ngoài rồi tải lại thì nó hiện ở "Dữ liệu đi vào từ". Gọi `GET /api/v1/models/{model}/data-catalog` bằng vai không phải ADMIN thì 403. Mở một thực thể rồi mở mục Nguồn gốc của từng trường: mỗi trường có ba cột Đi vào từ, Được dùng bởi và Đi ra tới; trường có `default` hiện giá trị mặc định, trường `reference` hiện thực thể nó trỏ tới, trường là điều kiện của `required_when` hoặc của `require` hay `when` trong quy trình hiện quy tắc đó, và mọi trường (trừ tệp) hiện dòng lịch sử, dòng sự kiện và lưu trữ khi thực thể có quy trình. Gọi `GET /api/v1/models/{model}/data-lineage` bằng vai không phải ADMIN thì 403. Mở một thực thể trong tab: hiện số đo chất lượng kèm thời điểm đo và số bản ghi được xét; độ điền của từng trường, thiếu theo điều kiện và vi phạm ràng buộc khác 0 được tô nổi bật, và độ mới nêu số bản ghi chưa đổi quá số ngày đã chọn ở ô "Không cập nhật quá (ngày)" (30, 90, 180 hoặc 365; mặc định 90). Với mô hình có `required_when`, bỏ một dòng khỏi điều kiện bằng SQL (`update <bảng> set <trường> = null`) rồi tải lại thì "thiếu theo điều kiện" tăng 1. Đặt `updated_at` của một bản ghi lùi 120 ngày thì số bản ghi chưa đổi tăng 1 với `staleDays=90` và về lại khi `staleDays=200`. Gọi `GET /api/v1/data/{model}/{entity}/quality` bằng ADMIN thì `sampleSize` bằng số dòng của bảng, bằng vai không phải ADMIN và không chạm thực thể thì 403, bằng vai chỉ thấy dòng của mình thì `sampleSize` bằng số dòng trong danh sách của họ, và `staleDays=0` thì 400.

Hướng dẫn và mẫu mô hình, kiểm bằng đúng các lệnh và thao tác trong tài liệu:

1. `deploy/REPORTING.md`, trên stack đã áp dụng `onestop` và `equipment`:
   - Kết nối bằng `report_ro`; câu liệt kê quyền chỉ ra các bảng của `m_onestop` và `m_equipment`.
   - Truy vấn nối hai mô hình trả một dòng cho mỗi người.
   - `UPDATE` bị từ chối với `permission denied for table request`, và đọc `engine.outbox` bị từ chối.
   - Đổi mật khẩu bằng `ALTER ROLE` làm mật khẩu cũ bị từ chối; áp dụng lại mô hình không đặt lại mật khẩu.
2. `docs/EXTENSIONS.md`:
   - Đăng ký một ứng dụng bằng `dxos client add`, lấy token từ tệp secret và tải OpenAPI của mô hình đúng như lệnh trong tài liệu.
   - Mô hình khác và API quản trị trả 403.
   - Hai lần tạo cùng `Idempotency-Key` và cùng nội dung trả cùng `id`, khác nội dung trả 409.
   - Đặt upstream và route mẫu trong `deploy/apisix/local.yaml`, chạy `docker compose run --rm --no-deps apisix-config --check`: mẫu đạt; thiếu token check, trùng id hoặc đường dẫn lõi, plugin/schema sai và tham chiếu thiếu phải bị từ chối.
   - Nạp cấu hình bằng cùng lệnh bỏ `--check`; sửa hoặc bỏ local rồi nạp lại, gateway đang chạy nhận cấu hình mới; cấu hình lỗi giữ nguyên file đầu ra.
   - Nâng bản cài từ archive với file local có sẵn: nội dung file giữ nguyên, không có trong `.dxos-files`, route riêng vẫn được ghép với cấu hình lõi mới.
   - Sau khi nạp lại cấu hình, không token và token sai trả 401, token đúng tới được dịch vụ với nguyên đường dẫn.
3. Mẫu Đặt lịch khám và Đổi trả hàng, cấp vai theo mô hình cho tài khoản thử và chỉ dùng dữ liệu giả:
   - Ở Thư viện mẫu bấm Dùng mẫu này rồi áp dụng từng mẫu.
   - `clinic`: người đặt chỉ thấy lịch của mình; lịch trùng giờ trong cùng phòng bị từ chối lúc đặt, phòng khác thì nhận; người tiếp nhận đi qua xác nhận, tiếp nhận khám và hoàn tất.
   - `retail`: số lượng dưới 1 và thiếu lý do bị từ chối; nhân viên trả phiếu về kèm ghi chú, khách nộp lại rồi nhân viên duyệt; khách không tự duyệt và không đọc được phiếu của người khác.
   - `python3 deploy/dev/check_conformance.py clinic retail` đạt.

Lệnh `dxos` và các tính năng chạy ngoài stack kiểm:

1. `dxos history verify` sau khi sửa, xóa hoặc chèn một dòng `record_event` bằng SQL: chỉ ra dòng đầu tiên bị đổi; trả lại dòng thì chuỗi nguyên vẹn.
2. `dxos reconcile`: xóa container `backend` thì chạy lại; sau `dxos stop` thì không bật lại; mọi service có chính sách restart `unless-stopped`.
3. Áp dụng lại tệp không đổi khi bảng thiếu hoặc thừa cột, đổi kiểu cột, hoặc client, vai, nhóm đơn vị bị xóa: kế hoạch ghi `repair` đúng loại, áp dụng xong thì về `unchanged`.
4. Áp dụng một mô hình đổi bảng trong lúc có yêu cầu ghi hồ sơ của mô hình đó: không có 500, không hồ sơ nào ghi sai cấu trúc.
5. Thống kê với vai `unit` và `own`, có và không có bộ lọc: `total` và tổng `byStatus` bằng số dòng của danh sách cùng bộ lọc.
6. `dxos demo` trên stack có `dxos-cli`: tạo bốn tài khoản `judge_*` và 40 hồ sơ; `judge_student` thấy hồ sơ của mình, mỗi cán bộ chỉ thấy đơn vị mình, `judge_supervisor` thấy mọi hồ sơ và không có nút Tạo mới; chạy lại không nộp thêm hồ sơ và in mật khẩu mới; `dxos demo remove` gỡ bốn tài khoản.
7. `dxos client add`, `list`, `role`, `rotate`, `remove` trên stack có `dxos-cli`: secret chỉ in ở `add` và `rotate`, `list` không có secret, `role` đổi vai mà secret cũ vẫn lấy được token và token mới mang vai mới, `rotate` làm secret cũ bị từ chối, `remove` làm client không lấy được token.
8. `dxos client browser add` tạo public client chỉ dùng Authorization Code với PKCE S256, có mapper audience và nhóm, lưu origin chính xác vào `CORS_ALLOWED_ORIGINS`; preflight từ origin đó được nhận, đăng nhập đổi code PKCE lấy token rồi gọi được API, HTTP ngoài loopback và wildcard bị từ chối trước khi tạo client; `list` không in secret và `remove` xóa client.
9. `dxos archive schedule install` rồi `systemctl --user start dxos-archive.service`: `deploy/archive.log` ghi lượt chạy, `dxos archive list` thấy lần chạy mới khi có hồ sơ đã đóng quá `ARCHIVE_AFTER_DAYS`, chạy lần nữa không ghi lại các hồ sơ đó; `dxos archive check --store` khớp manifest; khóa S3 sai thì `list` và `auto` thoát mã 1 và không in khóa; `schedule uninstall` gỡ hai tệp đơn vị.

10. `dxos gitops`, nguồn mô hình, trên stack có `dxos-cli` với một kho git cục bộ có một tệp mô hình mới:
    - `report`: `sync` ghi `drift`, `status` thoát mã 2, mô hình chưa đổi và không có schema `m_<mô hình>` trong PostgreSQL; `approve` bị từ chối khi chế độ không phải `approve`.
    - `approve`: `sync` ghi `pending`, `approve` với commit khác bị từ chối, với đúng commit thì `applied` và `status` thoát mã 0; `dxos plan --check` trên tệp đó thoát mã 0.
    - `auto`: một trường mới tùy chọn được áp dụng, xóa trường nằm chờ ở `pending`, `git revert` rồi `sync` thì về `in-sync`.
    - Một tệp hỏng không chặn tệp khác: `failed` cho tệp hỏng, `sync` thoát mã 1, các tệp còn lại vẫn được xét.
11. `dxos gitops`, nguồn hạ tầng, trên một bản cài thử đã chạy `install.sh` (đặt `COMPOSE_PROJECT_NAME` riêng để không đụng stack đang chạy) và một kho DX-OS cục bộ:
    - Cùng nội dung thì `in-sync`; commit sửa một tệp, thêm một tệp và bỏ một tệp thì `drift` với đúng ba con số `changed`, `added`, `removed`.
    - `approve`: `pending`, commit sai bị từ chối, đúng commit thì `applied`, tệp trong bản cài khớp kho, tệp bị bỏ không còn và `apisix/local.yaml` giữ nguyên.
    - `GITOPS_INFRA_MODE=auto` thiếu `GITOPS_REQUIRE_SIGNED=true` bị từ chối; commit không ký bị từ chối với mã 1 và không đổi gì, commit ký đúng khóa được áp dụng.
    - `GITOPS_INFRA_REF` là tag `vX.Y.Z`: `drift` ghi tag mới, sau khi áp dụng `DXOS_IMAGE_TAG` đổi theo; nhánh thì giữ nguyên `DXOS_IMAGE_TAG`.
    - `update.sh` lỗi (stack đang chạy): lượt áp dụng báo `failed` và thoát mã 1, lượt sau chạy lại `update.sh` và vẫn `failed` cho đến khi nó thành công, rồi mới `in-sync`; `approve infra` với `--accept` mức khác `SAFE` bị từ chối.
12. Chung cho hai nguồn:
    - Nhánh không tồn tại thì `sync` thoát mã 1, `status` ghi lỗi nêu nguyên nhân và giữ các mục của lượt tốt trước; `GITOPS_*` sai bị `sync` và `doctor` từ chối với cùng thông báo.
    - Địa chỉ SSH với host lạ, khóa sai hoặc khóa có quyền rộng bị từ chối, mỗi lỗi ghi nguyên nhân riêng.
    - API tắt thì nguồn mô hình ghi `error` và thoát mã 1.
    - `dxos gitops install` tạo timer và `uninstall` gỡ; `history.log` ghi mỗi lần commit hoặc kết quả đổi.

## 7. Hồi quy

- Đăng nhập SSO qua Keycloak ở web hoạt động.
- `docker compose up -d --build` từ clone sạch lên đủ tám service healthy: `postgres`, `keycloak`, `seaweedfs`, `backend`, `frontend`, `portal`, `monitor`, `apisix`.
- Màn hình giám sát `/monitor/` mở được với tài khoản ADMIN.
- Không còn từ khóa của ứng dụng cũ:

```bash
grep -rIlw -i -E 'farm|lab|farmer|DXOS_APP|dx-host|app-farm|app-lab|dx-core-api' \
  --exclude-dir=node_modules --exclude-dir=target --exclude-dir=.git --exclude-dir=docs \
  --exclude-dir=dist .
```

## 8. Sự cố thường gặp

| Hiện tượng | Cách xử lý |
|---|---|
| `release version 21 not supported` | Đặt `JAVA_HOME` về JDK 21 |
| Flyway báo "more than one migration with version 1" khi test | Chạy `./mvnw clean verify` |
| Keycloak không có vai `keycloak_app` | Thư mục dữ liệu Postgres đã có từ trước; dựng lại bằng `./reset.sh` để `deploy/postgres/init` chạy |
| `check_api.py` lỗi ở bước đầu | Cơ sở dữ liệu đã có mô hình; dựng lại stack rỗng |
| Gateway trả 429 giữa chừng khi chạy nhiều script | Giới hạn tốc độ của gateway; chờ một phút hoặc chạy cách nhau |
| Áp dụng mô hình trả 502 `ERR_ACCOUNT_SERVICE_DENIED` | Tài khoản dịch vụ `dxos-backend-admin` thiếu quyền hoặc scope `kc-admin-api`; xem [`sso/README.md`](../sso/README.md) |
| Kết quả không khớp mã vừa sửa | Image `dxos-backend:local` cũ hơn mã; chạy `docker compose build` rồi tạo lại service |
| Cổng 9080 hoặc 5432 bị chiếm | Đổi `APISIX_HOST_PORT`, `POSTGRES_HOST_PORT` trong `deploy/.env` |
