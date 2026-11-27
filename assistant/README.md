# Trợ lý nghiệp vụ

Dịch vụ độc lập đề xuất một giá trị cho một trường của hồ sơ.
Mô hình của trợ lý là bộ phân loại hồ sơ tiếng Việt do đội tự huấn luyện, chạy trên CPU ngay trong trợ lý.
Người dùng xem căn cứ, sửa hoặc xác nhận trên giao diện hồ sơ.
Core kiểm quyền, quy tắc và phiên bản trước khi ghi, nên model không có đường ghi trực tiếp.

| Cần | Xem |
|---|---|
| Bật, tắt, cấu hình, dựng image | [Chạy, cấu hình và tắt](#chạy-cấu-hình-và-tắt) |
| Bộ phân loại và trọng số | [Bộ phân loại](#bộ-phân-loại) |
| Một đề xuất đi từ tạo đến ghi | [Đề xuất và xác nhận](#đề-xuất-và-xác-nhận) |
| Mạng, dữ liệu lưu, thu hồi, thay khóa | [Vận hành](#vận-hành) |
| Kiểm trợ lý | [Kiểm tra](#kiểm-tra) |
| Gỡ trợ lý khỏi mã nguồn | [Gỡ trợ lý](#gỡ-trợ-lý) |

## Chạy, cấu hình và tắt

### Chạy và tắt

Trợ lý chạy cùng stack ở chế độ full (nhóm `app`), nhẹ vì suy luận bằng CPU trong tiến trình; chế độ core không có container hay route của trợ lý.

1. Chạy `dxos start`; trợ lý lên cùng web, cổng và giám sát.
2. Khai báo `ai.tasks` cho thực thể trong tệp mô hình (hoặc tab trợ lý của bảng điều khiển) rồi áp dụng; cú pháp ở [MODEL](../docs/MODEL.md#nhiệm-vụ-trợ-lý-nghiệp-vụ).
   Các mẫu `onestop`, `support_desk` và `clinic_appointment` có sẵn nhiệm vụ phân loại, dùng ngay khi trợ lý chạy.
3. Người dùng mở **Trợ lý nghiệp vụ**, liên kết tài khoản DX-OS, rồi mở một hồ sơ đã lưu, chọn nhiệm vụ và tạo đề xuất.
   Sửa giá trị thì đề xuất được tạo lại để xem lại, sau đó xác nhận lưu bằng một thao tác riêng.
4. Chạy `dxos start --core` thì trợ lý không chạy; `dxos start --full` đưa nó trở lại.

Trợ lý không giữ nền tảng: `dxos start`, `dxos update` và `reset.sh` khởi động nó sau nền tảng, nên trợ lý lỗi hay bị dừng chỉ có cảnh báo, Core và ứng dụng vẫn chạy bình thường.
Giao diện chỉ hiện mục Trợ lý khi dịch vụ trả lời, nên dừng `assistant-control` là ẩn mục đó.

### Cấu hình

Trợ lý chạy được không cần tệp cấu hình riêng; hai biến tùy chọn `ASSISTANT_CLASSIFIERS_DIR` và `ASSISTANT_OIDC_REVOKE_URL` ở mục 6 của [`deploy/.env.example`](../deploy/.env.example).
Giới hạn RAM và CPU của container nằm ở dịch vụ `assistant-control` trong `deploy/docker-compose.yml`.

Nếu mật khẩu quản trị Keycloak trong `deploy/.env` không còn đúng, `assistant-sso` chỉ ghi cảnh báo và trợ lý không liên kết được tài khoản.
Xem `docker compose logs assistant-sso`, sửa `deploy/.env` rồi chạy lại `docker compose up -d`.

### Image

`dxos update` kéo cả image trợ lý ở chế độ full.
Image có sau khi chạy workflow `Release Images` (cùng `services=all`).
Image chạy bằng Node.js và chứa mã trợ lý cùng các trọng số phân loại.
Dựng image từ mã nguồn bằng `docker compose build assistant-init` trong `deploy/`.
Dựng thẳng bằng `docker build -f assistant/Dockerfile .` ở gốc repo thì cần BuildKit.

## Bộ phân loại

### Ba bộ phân loại có sẵn

Image đóng gói ba bộ phân loại mà các mẫu dùng, đều đạt điều kiện phát hành:

| Bộ phân loại | Mẫu | Đọc trường | Gợi ý cho trường |
|---|---|---|---|
| `onestop` | `onestop` | `reason` | `kind` |
| `support` | `support_desk` | `title`, `detail` | `priority` |
| `clinic` | `clinic_appointment` | `note` | `service` |

Dữ liệu, cách huấn luyện và trọng số ở [`tools/classifier/`](../tools/classifier/README.md); số đo và giới hạn ở [thẻ mô hình](../tools/classifier/MODEL_CARD.md).
Tệp mô hình chỉ chọn được bộ phân loại đã có trọng số; nghiệp vụ khác cần dữ liệu và huấn luyện riêng.

### Nạp trọng số

Trợ lý nạp trọng số từ `/app/classifiers` khi khởi động.
Đặt `ASSISTANT_CLASSIFIERS_DIR` để nạp từ thư mục khác bên trong container, ví dụ `/run/secrets/classifiers` nếu đặt tệp dưới `deploy/data/assistant/private/classifiers`.
Mỗi tệp có tên `<classifier>.json`, với `<classifier>` khớp tên nhiệm vụ trong tệp trọng số.
Trợ lý báo `configured` khi nạp được ít nhất một bộ phân loại.
`GET /me` trả `model.classifiers` gồm các tên đã nạp.

Revision của nhiệm vụ bao gồm SHA-256 của tệp trọng số.
Thay tệp và khởi động lại trợ lý làm các đề xuất dùng trọng số cũ hết hiệu lực.

### Kết quả và căn cứ

Nhiệm vụ đọc các trường chữ theo thứ tự `input_fields`.
Mẫu hỗ trợ kỹ thuật khai `title` rồi `detail` theo thứ tự huấn luyện.
Kết quả từ chối trả `insufficient: true` và `proposal: null`.
Đề xuất lưu `evidence` gồm một đến ba cụm chữ trích từ các trường đầu vào và `confidence` là xác suất của nhãn gợi ý.
Giao diện hiển thị các cụm chữ trong mục **Căn cứ trong hồ sơ** kèm độ tự tin theo phần trăm.
Sửa giá trị khác gợi ý thì đề xuất mới không còn `confidence`.

### Lỗi thường gặp

| Mã | Khi nào |
|---|---|
| 409 `ERR_CLASSIFIER_UNAVAILABLE` | Thiếu hoặc không đọc được trọng số của nhiệm vụ; chỉ nhiệm vụ đó bị ảnh hưởng |
| 409 `ERR_CLASSIFIER_LABELS` | Các lựa chọn của trường đầu ra không khớp tập nhãn nghiệp vụ của tệp trọng số |

## Đề xuất và xác nhận

### Vòng đời một đề xuất

Đề xuất thuộc về một chủ, hết hạn sau 120 giây và được xóa sau 30 ngày.
Đề xuất ghi lại hồ sơ, nhiệm vụ, giá trị, căn cứ, phiên bản hồ sơ, phiên bản mô hình và revision của nhiệm vụ.
Sửa nội dung tạo đề xuất mới và vô hiệu đề xuất cũ.

Trạng thái đi từ `pending` qua `executing` tới `succeeded`, `failed` hoặc `uncertain`.
Danh tính lấy từ JWT đã kiểm issuer và audience, không nhận từ body hay model.
Liên kết với Core dùng PKCE và state dùng một lần.

### Khi xác nhận

Trợ lý đọc lại hồ sơ và cấu hình, rồi chỉ gửi `PATCH` một trường kèm `version` và `modelVersion`.

| Tình huống | Kết quả |
|---|---|
| Hồ sơ, mô hình, nhiệm vụ hoặc trọng số đã đổi | 409 `ERR_PROPOSAL_STALE` |
| Giá trị trùng hồ sơ | 409 `ERR_PROPOSAL_NO_CHANGE`, không gọi Core |
| Timeout hoặc dừng tiến trình khi ghi | Trạng thái `uncertain`, không tự gửi lại; người dùng đối chiếu lịch sử hồ sơ |

### Rà loại hồ sơ ở Hộp việc

Khi trợ lý có trọng số cho một nhiệm vụ phân loại đã khai báo, nút **Rà loại hồ sơ** rà 25 hồ sơ đủ điều kiện đầu tiên chưa rà trên trang Hộp việc đang xem.
Bấm lại để rà lượt tiếp theo.
Hồ sơ có giá trị khác gợi ý hiện **Cần rà** cùng giá trị đề xuất, độ tự tin và cụm chữ căn cứ.
Bấm dấu **Cần rà** mở khối trợ lý trên trang chi tiết và gọi `POST /me/proposals` để tạo đề xuất cần xác nhận.
Giao diện giữ kết quả rà trong bộ nhớ tối đa 120 giây mỗi lượt, còn nguyên khi chuyển trang trong ứng dụng, và xóa khi đăng xuất hoặc hết phiên.
Hồ sơ không đọc được được đếm riêng và rà lại được ở lượt sau.

`POST /assistant-api/v1/me/reviews` nhận `{"items":[{"model":"onestop","entity":"request","id":1}]}` với từ 1 đến 25 hồ sơ.
Trợ lý đọc hồ sơ bằng phiên Core của người gọi, đọc metadata một lần cho mỗi thực thể, gửi tối đa 5 yêu cầu đồng thời và chỉ rà hồ sơ còn mở thuộc thực thể người gọi được sửa.
Hồ sơ gặp lỗi riêng (không có quyền, không tồn tại, bị giới hạn tốc độ, lỗi máy chủ hoặc quá thời gian chờ) được đếm ở `data.skipped`.
Lỗi phiên (chưa liên kết, liên kết hết hạn, Core trả 401) làm cả lượt trả lỗi.
API bỏ qua nhiệm vụ thiếu trọng số hoặc có tập lựa chọn khác tập nhãn.
`data.items` có một kết quả cho mỗi nhiệm vụ đủ điều kiện, gồm `model`, `entity`, `id`, `task`, `field`, `current`, `suggested`, `evidence`, `mismatch` và `confidence` khi có gợi ý.
Khi bộ phân loại từ chối, `suggested` là `null` và `mismatch` là `false`.
Lượt rà chỉ đọc, không tạo đề xuất và không ghi nội dung hồ sơ vào nhật ký.
API dùng cùng giới hạn 120 yêu cầu mỗi phút theo địa chỉ IP tại cổng của trợ lý.

Đường dẫn, thân yêu cầu và phản hồi của các endpoint nằm ở [`docs/API.md`](../docs/API.md).

## Vận hành

### Mạng

Container `assistant-control` chạy trên network nội bộ `assistant`, không có đường ra Internet và không thấy các dịch vụ khác của stack.
Chỉ `backend`, `keycloak` và `apisix` nằm chung network đó.
Bộ phân loại tính ngay trong tiến trình của trợ lý, nên nội dung hồ sơ không được gửi tới dịch vụ nào khác.

### State và giới hạn

State là một SQLite chứa token liên kết đã mã hóa theo chủ, đề xuất và phiên liên kết đang chờ.
Master key ở `deploy/data/assistant/private/master.key` chỉ giải mã state trên máy này.
`deploy/backup.sh` không sao lưu state của trợ lý; sau khi khôi phục Core, người dùng liên kết lại tài khoản.
State có sẵn mà thiếu hoặc sai master key thì trợ lý không khởi động; xóa `deploy/data/assistant` rồi chạy lại trợ lý để tạo state mới.

Một trợ lý phục vụ nhiều người, mỗi người có state riêng; không chạy nhiều bản trên cùng SQLite.
Số đo nghiệm thu của bộ phân loại lấy trên câu do đội viết, chưa trên hồ sơ thật.
Hỏi đáp mở, embedding, reranker và tác tử tự tạo đề xuất theo sự kiện chưa có.

### Thu hồi và thay khóa

- **Hủy liên kết của một người:** nút "Hủy liên kết" ở trang Trợ lý nghiệp vụ, hoặc `DELETE /assistant-api/v1/me/core/link`.
  Khóa hoặc xóa tài khoản ở Keycloak cũng chặn trợ lý: lần làm mới token kế tiếp bị từ chối và token đã lưu bị xóa.
- **Thay master key:** dừng trợ lý, chạy lệnh dưới, rồi chạy lại.
  Lệnh mã hóa lại toàn bộ state bằng khóa mới trong một giao dịch rồi mới thay `master.key`; bị ngắt giữa chừng thì chạy lại lệnh.
  Các phiên liên kết đang dở bị xóa, người dùng bắt đầu liên kết lại.

  ```sh
  docker compose stop assistant-control
  docker compose run --rm assistant-init dist/rotate.js /bootstrap
  docker compose up -d
  ```

## Kiểm tra

Workflow `Assistant` chạy khi đổi `assistant/`, `tools/classifier/`, tệp compose, tệp SSO hay SBOM của trợ lý.
Nó build và test dịch vụ, so SBOM với tệp khóa, kiểm tệp compose, và kiểm route cổng ở mọi chế độ.
Workflow `Platform` dựng image trợ lý cùng các image khác.

- **Test của dịch vụ:** `npm test` trong thư mục `assistant` kiểm nhãn, xác suất và cụm chữ của Node với Python, dựa trên tệp đối chiếu `test/fixtures/classifier.json`.
  Tệp này lấy câu gốc và biến thể đã dùng để huấn luyện, phát hành theo CC-BY-4.0 như thẻ dữ liệu nêu.
- **Sinh lại tệp đối chiếu:** khi đổi trọng số hoặc dữ liệu huấn luyện, chạy `PYTHONDONTWRITEBYTECODE=1 python3 tools/classifier/make_parity_fixture.py` từ gốc repo.
- **Kiểm trình duyệt:** hai bài ở `e2e/web/tests/assistant.spec.ts` tự bỏ qua khi không đặt biến môi trường của hồ sơ thử.
  `E2E_ASSISTANT_RECORD_PATH` là hồ sơ mà đề xuất khác giá trị hiện tại, và `E2E_ASSISTANT_UNCHANGED_RECORD_PATH` là hồ sơ mà đề xuất trùng giá trị hiện tại.

## Gỡ trợ lý

Chạy chế độ core như bước 4 ở [Chạy và tắt](#chạy-và-tắt) thì Core, sao lưu và cập nhật chạy như bình thường.
Gỡ hẳn khỏi mã nguồn thì làm theo danh sách ở [`REMOVAL.md`](./REMOVAL.md): các tệp cần xóa, các dòng cần sửa trong tệp chung và phần khai báo `ai:` của mô hình có thể giữ.
