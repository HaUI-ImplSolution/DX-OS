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
| Giấy phép và header | `python3 .github/scripts/check_licenses.py` | `Check licenses` của `Compliance` |
| Giấy phép của dự án gốc | `GITHUB_TOKEN=... python3 .github/scripts/check_upstream_licenses.py` | `Check upstream licenses` của `Compliance` |
| SBOM khớp tệp khóa | `python3 .github/scripts/check_sbom_fresh.py` | `Check SBOMs against lockfiles` của `Compliance` |
| Route của gateway | `python3 deploy/apisix/check_routes.py` | `Validate the gateway routes` của `Platform` |
| Compose hợp lệ, dừng khi thiếu bí mật | `docker compose config` trong `deploy/` | `Validate the Compose file` của `Platform` |
| Gói phát hành và bộ cài | `.github/scripts/check_archive.sh <gói> <phiên bản>` | `Install from the release archive` của `Platform` |
| Sao lưu và khôi phục | `deploy/backup.sh` rồi `verify` | `Back up and restore` của `Platform` |
| API trên stack thật | Các script `deploy/dev/check_*.py`, mục 4 | `API end-to-end` của `Platform`, hai nhóm |
| Trình duyệt | Xem [`e2e/README.md`](../e2e/README.md) | `Browser end-to-end` của `Platform` |
| Trang tài liệu | `cd website && npm ci && npm run build` | `Build the docs site` của `Docs` |
| Kiểm tay | Mục 6 | Không |

`./mvnw verify` sau khi đổi tài nguyên nên chạy `clean verify`: tài nguyên cũ còn trong `target/` làm Flyway thấy hai migration cùng phiên bản.
`./mvnw formatter:format` sửa định dạng khi `formatter:validate` báo lỗi.
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

Cổng 9080 hoặc 5432 bị chiếm thì đổi `APISIX_HOST_PORT`, `POSTGRES_HOST_PORT` trong `deploy/.env`.

## 4. Kiểm API trên stack

Chạy trên stack mới dựng, cơ sở dữ liệu rỗng, vì script áp dụng mô hình mẫu từ trạng thái rỗng.
Mỗi script phụ thuộc các script đứng trước trong cùng nhóm, nên chạy đúng thứ tự:

```bash
for c in api recovery case change process booking demo guards users docs conformance cli; do
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
| `check_process.py` | Các họ mẫu, quy tắc chung của quy trình, hạn và quá hạn, hành động hoàn tất qua outbox, tệp đính kèm, thống kê, chống ghi đè |
| `check_booking.py` | Họ `reservation`, danh mục của bảng điều khiển, mọi mẫu áp dụng được |
| `check_demo.py` | Tám tiêu chí của kịch bản trình diễn |
| `check_guards.py` | Người giữ vai, vai của nền tảng, bỏ vai, xóa thực thể còn hồ sơ đang chạy, lần áp dụng dừng giữa chừng (tiếp tục và hủy bỏ) |
| `check_users.py` | Quản trị người dùng: gán và gỡ vai và đơn vị, tạo, khóa, đặt lại mật khẩu, audit |
| `check_docs.py` | Mọi endpoint các tài liệu nêu có trong OpenAPI của backend đang chạy, đúng phương thức |
| `check_conformance.py` | Rào chắn của lõi giữ với mọi mô hình đã áp dụng, hoặc các mô hình nêu tên: không ghi được `status`, không token thì 401, không đọc được dữ liệu mô hình khác |
| `check_cli.py` | Lệnh `dxos plan` và `apply --accept` (tệp giống, đổi an toàn, đổi mất dữ liệu bị từ chối khi `--accept` thấp hơn mức của kế hoạch), lịch sử ghi tài khoản của lệnh, `dxos archive` và `archive check` (sửa một byte Parquet thì mã 1), bộ mô phỏng IT/OT gửi lại cùng quan sát thì in `duplicate` và không thêm hồ sơ; cần Docker; tự áp dụng một mô hình riêng |
| `check_events.py` | Webhook: thứ tự, chữ ký, thử lại, nội dung không lộ, nhắc hạn; chuỗi hash của lịch sử và bên nhận so hash |
| `check_bootstrap.py` | Tệp mô hình lúc khởi động: `applied`, `unchanged`, `skipped`, `failed` |

Các script tạo client Keycloak `dxos-check` (cho phép đăng nhập bằng mật khẩu, chỉ dùng cho máy dev) và các người dùng thử như `admin1`, `req1`, `keeper1`.
Vai của mô hình được gán ở client `dxos-m-<mô hình>`, vai `ADMIN` ở realm.
Mỗi kiểm in một dòng `PASS` hoặc `FAIL` kèm nhãn, cuối script in `N/M passed`, và script thoát với mã 1 khi có kiểm hỏng.

Điều kiện đặc biệt:

- Tiêu chí 3 của `check_demo.py` (chặn tệp nhiễm mã độc, bằng tệp EICAR) chỉ chạy khi bật quét: `dxos av on` trên bản cài, hoặc đặt `COMPOSE_PROFILES=av` và `STORAGE_SCAN_HOST=clamav` trong `deploy/.env` rồi `docker compose up -d`.
- Một số kiểm cần backend chạy với biến gây lỗi có chủ ý (`ENGINE_FAULT_AFTER_DECISION=true` làm quyết định lỗi sau bước quy trình, `ENGINE_FAULT_APPLY_STEP=ENSURE_CLIENT` làm bước áp dụng có tên đó lỗi); các script tự bật rồi tắt bằng cách tạo lại service `backend`.
- Keycloak 26.8 trả 400 thay vì 401 cho mật khẩu sai; các kiểm về mật khẩu nhận cả hai.

## 5. Trình duyệt

Bài Playwright của bảng điều khiển nằm ở `e2e/`; cách chạy, biến cấu hình và nơi có ảnh chụp ở [`e2e/README.md`](../e2e/README.md).
CI chạy bài này ở job `Browser end-to-end` của workflow `Platform`.

## 6. Kiểm tay trước khi phát hành

Những mục dưới chưa có kiểm tự động.

Giao diện, đăng nhập bằng `req1` (vai `BORROWER`), `keeper1` (vai `KEEPER`) và `admin1` (vai `ADMIN`) của script ở mục 4:

1. Mở `http://localhost:9080/`, đăng nhập bằng `BORROWER`: menu có Thiết bị, Phiếu mượn, Ghi chú phiếu mượn; danh sách Thiết bị chỉ đọc, danh sách Phiếu mượn chỉ có phiếu của mình, biểu mẫu tạo có đủ trường theo kiểu của mô hình.
2. Đăng nhập bằng `KEEPER`: thấy mọi phiếu; phiếu `Chờ duyệt` có nút Duyệt và Từ chối, bấm Duyệt thì trạng thái thành `Đã duyệt`.
3. Bundle web không chứa tên thực thể hay vai nào của mô hình.
4. Mọi chữ trên giao diện là tiếng Việt: nút, bộ lọc, thông báo bắt buộc nhập, lỗi tệp mô hình theo dòng và cột, nhãn các loại thay đổi trong kế hoạch.
5. Chế độ tối hiện đủ hộp việc, danh sách, chi tiết hồ sơ và bảng điều khiển; ở màn hình 360 px các ô xếp dọc, không tràn ngang.
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
    Tệp mô hình có hành động tên `overdue`, `due_soon`, `created`, `updated`, `deleted`, `assigned`, `file_added`, `file_removed`, `completion_skipped` hay `auto_closed` bị từ chối ở bước kiểm, kèm dòng và cột; tên thường như `review` được nhận.

Lệnh `dxos` và các tính năng chạy ngoài stack kiểm:

1. `dxos history verify` sau khi sửa, xóa hoặc chèn một dòng `record_event` bằng SQL: chỉ ra dòng đầu tiên bị đổi; trả lại dòng thì chuỗi nguyên vẹn.
2. `dxos reconcile`: xóa container `backend` thì chạy lại; sau `dxos stop` thì không bật lại; mọi service có chính sách restart `unless-stopped`.
3. Áp dụng lại tệp không đổi khi bảng thiếu hoặc thừa cột, đổi kiểu cột, hoặc client, vai, nhóm đơn vị bị xóa: kế hoạch ghi `repair` đúng loại, áp dụng xong thì về `unchanged`.
4. Áp dụng một mô hình đổi bảng trong lúc có yêu cầu ghi hồ sơ của mô hình đó: không có 500, không hồ sơ nào ghi sai cấu trúc.
5. Thống kê với vai `unit` và `own`, có và không có bộ lọc: `total` và tổng `byStatus` bằng số dòng của danh sách cùng bộ lọc.

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
