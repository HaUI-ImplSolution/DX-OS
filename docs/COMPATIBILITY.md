# Tương thích giữa các bản

Tài liệu này nói bên dùng DX-OS được trông cậy vào điều gì khi lên bản mới: ứng dụng gọi API, bên nhận webhook, tệp mô hình, cơ sở dữ liệu và số đo vận hành.
Mỗi thay đổi người dùng thấy được ghi ở [`CHANGELOG.md`](../CHANGELOG.md), theo từng bản.

## Đánh số bản

Bản đánh số `X.Y.Z` và gắn tag `vX.Y.Z` trên nhánh `main`.

- **Bản vá (`Z`):** sửa lỗi, không đổi cách dùng.
- **Bản nhỏ (`Y`):** thêm tính năng, thêm đường dẫn, thêm trường; cái đang chạy được vẫn chạy.
- **Bản chính (`X`):** được bỏ hoặc đổi nghĩa một thứ đã có, và luôn có mục nói rõ cách chuyển ở `CHANGELOG.md`.

## API

Mọi đường dẫn nằm dưới `/api/v1`; phản hồi có dạng chung gồm `success`, `data`, `message` và `errorCode`.

Trong cùng `v1`, những thay đổi sau được phép và không làm hỏng bên gọi:

- thêm đường dẫn;
- thêm trường vào phản hồi, thêm giá trị vào các trường chọn;
- thêm tham số tùy chọn;
- thêm mã `errorCode` mới cho một lỗi mới.

Bỏ đường dẫn, bỏ hoặc đổi tên một trường, đổi kiểu hay đổi nghĩa của một trường, và đổi `errorCode` của một lỗi đã có chỉ làm ở bản chính tiếp theo.
Bên gọi nên bỏ qua trường mà mình không biết.

Đặc tả đầy đủ ở OpenAPI (`/v3/api-docs`) của backend đang chạy.

## Số đo vận hành

Tên, kiểu và nhãn của các số đo `dxos_*` ở `/actuator/prometheus` (bảng ở [`docs/MONITORING.md`](MONITORING.md)) được giữ trong cùng bản chính; thêm số đo hoặc thêm giá trị cho một nhãn được phép ở bản nhỏ.
Bỏ hoặc đổi tên một số đo, đổi kiểu hoặc đổi nghĩa của nó, và thêm nhãn mới cho một số đo đã có chỉ làm ở bản chính tiếp theo.
Các số đo chuẩn của JVM, cơ sở dữ liệu và HTTP do thư viện sinh ra và theo bản của thư viện, nên bảng của DX-OS không nói tới chúng.

## Tệp mô hình

Tệp mô hình ghi `format` là số của định dạng; hiện là `1`.

- Thêm một khóa tùy chọn vào định dạng không đổi `format`.
- Đổi định dạng theo cách làm tệp cũ không còn hợp lệ thì tăng `format`, và ghi cách chuyển ở `CHANGELOG.md`.
- Trong cùng bản chính, một tệp hợp lệ ở bản trước vẫn hợp lệ.
- Lõi tự nâng tệp format cũ trong bộ nhớ khi đọc; `dxos model upgrade` xem trước và ghi tệp đã nâng (xem `docs/MODEL.md`).
- `dxos lint` đọc tệp bằng cùng bộ đọc nên nhận cùng các format như lõi, và từ chối format mới hơn bản lõi (xem `docs/MODEL.md`).

Đổi một mô hình đang chạy đi qua kế hoạch: lõi nói trước thay đổi nào chuyển được dữ liệu, thay đổi nào bị từ chối và vì sao (xem [`docs/MODEL.md`](./MODEL.md)).

## Webhook và sự kiện

Mỗi thông điệp có `schemaVersion`, hiện là `1`.

- Thêm trường vào thông điệp không đổi `schemaVersion`.
- Bỏ hoặc đổi nghĩa một trường thì tăng `schemaVersion` ở bản chính tiếp theo.
- Bên nhận nên bỏ qua trường lạ và đọc `schemaVersion` trước khi hiểu nội dung.

Chữ ký `X-Dxos-Signature` và quy ước khử trùng theo `eventId` giữ nguyên trong cùng bản chính; chi tiết ở [`docs/INTEGRATIONS.md`](INTEGRATIONS.md).

## Cơ sở dữ liệu

Các bước migration chỉ đi tới và đã phát hành thì không sửa nội dung, vì Flyway kiểm checksum của từng tệp.
Nâng bản là chạy thêm các migration mới; không có đường quay lại bản trước ngoài khôi phục từ bản sao lưu.

## Khi một phần sắp bị bỏ

Một phần dự định bỏ được ghi ở mục "Sắp bỏ" trong `CHANGELOG.md` của ít nhất một bản nhỏ trước khi bản chính bỏ nó, kèm cái thay thế.
Việc bỏ không xảy ra ở bản vá.
