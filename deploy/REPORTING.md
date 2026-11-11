# Kết nối công cụ báo cáo

DX-OS tạo vai PostgreSQL `report_ro` để công cụ báo cáo của người vận hành đọc dữ liệu nghiệp vụ đang chạy.
Vai này có `USAGE` trên schema `m_<mô hình>` và `SELECT` trên từng bảng do tệp mô hình khai báo, nhưng không có quyền ghi.
Mỗi bảng mới được tạo qua một lần áp dụng mô hình cũng nhận quyền đọc này.

Kết nối trực tiếp cơ sở dữ liệu không áp dụng quyền `own`, `unit` hay `assigned` của API.
`report_ro` đọc được mọi dòng của mọi bảng nghiệp vụ đã được cấp quyền, nên chỉ giao thông tin đăng nhập cho công cụ báo cáo do người vận hành tin cậy.
Vai này không được cấp quyền đọc schema nội bộ `engine`, cơ sở dữ liệu Keycloak hay dữ liệu của màn hình giám sát.

Hướng dẫn dưới đây dùng [Apache Superset](https://superset.apache.org/) làm ví dụ vì mã nguồn của nó dùng [Apache License 2.0](https://github.com/apache/superset/blob/master/LICENSE.txt).
Superset là thành phần tùy chọn do người vận hành cài riêng; DX-OS không cài, cập nhật hay đưa nó vào stack.

## Chuẩn bị thông tin kết nối

`deploy/make_env.sh` sinh `REPORT_DB_PASSWORD` ngẫu nhiên và đặt quyền `600` cho `deploy/.env`.
Nếu tự tạo `.env`, đặt biến này trước khi áp dụng mô hình đầu tiên.
Dịch vụ lõi tạo `report_ro` ở lần áp dụng đầu tiên và cấp quyền đọc khi mỗi mô hình được áp dụng.

| Trường | Giá trị |
|---|---|
| Host | Điểm cuối PostgreSQL riêng mà công cụ báo cáo nhìn thấy; mặc định trên máy DX-OS là `127.0.0.1` |
| Port | Phần cổng của `POSTGRES_HOST_PORT`; mặc định là `5432` |
| Database | Giá trị `POSTGRES_DB`; mặc định là `dxos` |
| Username | `report_ro` |
| Password | Giá trị `REPORT_DB_PASSWORD` trong `deploy/.env` |
| Schema nghiệp vụ | `m_<tên mô hình>`, ví dụ `m_onestop` và `m_equipment` |

Không dùng `POSTGRES_USER` cho công cụ báo cáo.
Không chép `.env`, URI có mật khẩu, ảnh chụp cấu hình hoặc kết quả chứa dữ liệu thật vào issue, log hay repository.

## Giữ kết nối trong mạng riêng

Cấu hình mặc định `POSTGRES_HOST_PORT=127.0.0.1:5432` chỉ công bố PostgreSQL trên loopback của máy DX-OS.
Giữ nguyên ràng buộc này khi công cụ báo cáo chạy trên cùng máy.
Nếu công cụ chạy trong container, `127.0.0.1` là chính container đó; đưa công cụ tới một điểm cuối riêng của máy chủ hoặc một mạng Docker riêng thay vì công bố cổng ra Internet.

Khi công cụ báo cáo chạy trên máy khác, dùng VPN hoặc tunnel SSH.
Ví dụ dưới đây chạy trên máy báo cáo và chỉ mở cổng tunnel ở loopback của máy đó:

```bash
ssh -N -L 127.0.0.1:15432:127.0.0.1:5432 <user>@<dxos-host>
```

Sau đó công cụ kết nối tới `127.0.0.1:15432`.
Không đổi `POSTGRES_HOST_PORT` thành `0.0.0.0:5432` hay chỉ `5432` để phục vụ báo cáo từ xa.
Nếu Superset chạy trong container, đặt tunnel hoặc điểm cuối riêng trong cùng mạng kín với container và không bind nó lên giao diện công khai.

## Kết nối Apache Superset

Superset cần driver PostgreSQL trong chính môi trường Superset.
Cài driver theo [hướng dẫn kết nối cơ sở dữ liệu](https://superset.apache.org/docs/databases/) và [tham chiếu PostgreSQL](https://superset.apache.org/user-docs/databases/supported/postgresql/) chính thức của dự án.
Driver đó thuộc bản triển khai Superset, không phải dependency của DX-OS.

1. Trong Superset, mở **Settings → Data: Database Connections** và thêm một cơ sở dữ liệu PostgreSQL.
2. Nhập host, port, database, username và password ở bảng trên, hoặc dùng URI sau với các giá trị đã mã hóa URL:

   ```text
   postgresql+psycopg2://report_ro:<password>@<private-host>:<port>/<database>
   ```

3. Chạy **Test Connection**, rồi lưu kết nối.
4. Chỉ tạo dataset từ các schema `m_<mô hình>` cần báo cáo và giữ các tùy chọn DML tắt.

Không dùng URI thật trong lệnh shell vì mật khẩu có thể vào history hoặc danh sách tiến trình.
Lưu mật khẩu bằng cơ chế quản lý secret của bản triển khai Superset và giới hạn quyền quản trị kết nối trong Superset.

## Kiểm tra phạm vi đọc

Chạy truy vấn sau bằng kết nối `report_ro` để liệt kê các bảng nghiệp vụ được cấp `SELECT`:

```sql
SELECT table_schema, table_name
FROM information_schema.role_table_grants
WHERE grantee = current_user
  AND privilege_type = 'SELECT'
  AND table_schema LIKE 'm\_%' ESCAPE '\'
ORDER BY table_schema, table_name;
```

Ví dụ sau tổng hợp số hồ sơ Một cửa sinh viên và số phiếu mượn thiết bị theo cùng mã tài khoản Keycloak.
`FULL OUTER JOIN` giữ cả người chỉ xuất hiện ở một mô hình, nên kết quả không phụ thuộc hai mô hình đã có cùng tập người dùng hay chưa:

```sql
WITH one_stop AS (
  SELECT created_by, COUNT(*) AS request_count
  FROM m_onestop.request
  GROUP BY created_by
), equipment AS (
  SELECT created_by, COUNT(*) AS loan_count
  FROM m_equipment.loan
  GROUP BY created_by
)
SELECT
  COALESCE(one_stop.created_by, equipment.created_by) AS user_id,
  COALESCE(one_stop.request_count, 0) AS one_stop_requests,
  COALESCE(equipment.loan_count, 0) AS equipment_loans
FROM one_stop
FULL OUTER JOIN equipment USING (created_by)
ORDER BY user_id;
```

`created_by` là id tài khoản Keycloak, không phải tên hiển thị.
Truy vấn này chỉ ghép dữ liệu theo định danh dùng chung và không tạo khóa ngoại giữa hai schema.

Kiểm tra quyền ghi bằng câu lệnh không thể thay đổi dòng nào kể cả khi quyền bị cấu hình sai:

```sql
UPDATE m_onestop.request
SET reason = reason
WHERE false;
```

PostgreSQL phải trả `permission denied for table request`.
Nếu công cụ báo cáo chặn DML trước khi gửi tới cơ sở dữ liệu, chạy câu lệnh bằng `psql` với chính tài khoản `report_ro`.

## Xoay mật khẩu

Đổi `REPORT_DB_PASSWORD` trong `.env` không đổi mật khẩu của role đã tồn tại.
Xoay mật khẩu ở PostgreSQL, bản triển khai công cụ báo cáo và `.env` trong cùng một lần vận hành:

1. Tạo một mật khẩu ngẫu nhiên mới trong trình quản lý mật khẩu.
2. Mở `psql` quản trị mà không đưa mật khẩu mới lên dòng lệnh:

   ```bash
   cd deploy
   docker compose exec postgres sh -lc 'psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB"'
   ```

3. Ở dấu nhắc `psql`, chạy `\password report_ro`, nhập mật khẩu mới hai lần và chưa đóng phiên quản trị.
4. Cập nhật `REPORT_DB_PASSWORD` trong `deploy/.env`, giữ quyền tệp là `600`, rồi cập nhật secret của kết nối trong Superset.
5. Nếu phải vô hiệu ngay các kết nối còn giữ mật khẩu cũ, chạy trong phiên quản trị:

   ```sql
   SELECT pg_terminate_backend(pid)
   FROM pg_stat_activity
   WHERE usename = 'report_ro'
     AND pid <> pg_backend_pid();
   ```

6. Chạy lại **Test Connection**, truy vấn đọc và phép thử ghi bị từ chối ở trên, rồi thoát `psql` bằng `\q`.

Việc dừng phiên sẽ ngắt các dashboard đang chạy.
Không cần khởi động lại PostgreSQL; giữ `.env` đồng bộ để bản cài mới hoặc lần phục hồi tiếp theo dùng đúng thông tin xác thực.

## Danh sách kiểm tra

- Công cụ đăng nhập bằng `report_ro`, không dùng `POSTGRES_USER`.
- PostgreSQL chỉ được tiếp cận qua loopback, VPN, tunnel hoặc mạng riêng.
- Chỉ người vận hành tin cậy được quản trị kết nối vì `report_ro` đọc mọi dòng nghiệp vụ.
- Truy vấn xuyên hai mô hình chạy được và phép thử ghi trả `permission denied`.
- Mật khẩu không xuất hiện trong repository, log, URI được chia sẻ hoặc ảnh chụp.
- Sau mỗi lần xoay mật khẩu, PostgreSQL, `.env` và công cụ báo cáo dùng cùng một giá trị.
