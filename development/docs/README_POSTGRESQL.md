# Hướng dẫn PostgreSQL và Alembic cho DX-Farm

Tài liệu này hướng dẫn thành viên trong nhóm khởi động PostgreSQL, kết nối để kiểm tra dữ liệu và quản lý thay đổi schema bằng Alembic trong môi trường phát triển local.

## 1. Cấu hình hiện tại của dự án

- PostgreSQL chạy bằng Docker image `pgvector/pgvector:pg16`.
- Database, user và password mặc định đều là `dxfarm`.
- PostgreSQL được mở tại `localhost:5432`.
- Dữ liệu được lưu ở `deploy/data/postgres`, nên vẫn còn sau khi chạy `docker compose down`.
- Alembic và các SQLAlchemy model nằm trong thư mục `ai`.
- Chuỗi migration hiện tại:
  - `9700136d7e00`: tạo schema ban đầu.
  - `4c2a0f9d8b71`: bật extension `vector`, tạo bảng `documents` và `document_chunks` cho RAG.
  - `c1f7e2d4a9b0`: đổi TreatmentRule sang `dose_min`/`dose_max`, tạo bảng
    `disease_management_policies`.

> Không commit các file `.env` vì chúng chứa cấu hình và mật khẩu local.

## 2. Yêu cầu trước khi chạy

Cần cài:

- Docker Engine và Docker Compose v2.
- Python 3.11 (phiên bản được khuyến nghị trong `ai/requirements.txt`).
- Git.

Kiểm tra nhanh:

```bash
docker --version
docker compose version
python3 --version
```

Tất cả lệnh dưới đây mặc định được chạy từ thư mục gốc `DX---Farm`, trừ khi có lệnh `cd` khác.

## 3. Khởi động PostgreSQL

Tạo cấu hình local lần đầu:

```bash
cp deploy/.env.example deploy/.env
```

Có thể giữ cấu hình mặc định khi phát triển local. Nếu đổi `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` hoặc `POSTGRES_PORT` trong `deploy/.env`, phải cập nhật `DATABASE_URL` của phần AI tương ứng.

Chỉ khởi động PostgreSQL:

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d postgres
```

Kiểm tra container và health check:

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.yml ps
docker compose --env-file deploy/.env -f deploy/docker-compose.yml logs postgres
```

Khi cột trạng thái hiển thị `healthy`, database đã sẵn sàng nhận kết nối.

## 4. Cấu hình kết nối cho AI/Alembic

Tạo môi trường Python lần đầu:

```bash
cd ai
python3 -m venv venv
source venv/bin/activate
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
```

Các lệnh trong tài liệu dùng virtualenv `ai/venv`. Nếu đã có virtualenv ở vị trí khác (ví dụ `venv` tại gốc repo), hãy kích hoạt đúng môi trường đó rồi vẫn chạy các lệnh Alembic từ thư mục `ai`.

Tạo cấu hình AI local từ file mẫu:

```bash
cp .env.example .env
```

Nếu đang dùng cấu hình PostgreSQL mặc định, kiểm tra `ai/.env` có dòng:

```dotenv
DATABASE_URL=postgresql+psycopg://dxfarm:dxfarm@localhost:5432/dxfarm
```

Nếu mật khẩu chứa ký tự đặc biệt như `@`, `:`, `/` hoặc `%`, cần URL-encode mật khẩu trước khi đưa vào `DATABASE_URL`.

## 5. Chạy migration lần đầu

Với database mới, chỉ bật service `postgres` trước và chạy Alembic trước khi khởi động backend:

```bash
cd ai
source venv/bin/activate
python -m alembic upgrade head
```

Kiểm tra revision đã áp dụng:

```bash
python -m alembic current
python -m alembic heads
python -m alembic history
```

Kết quả `current` đúng ở thời điểm viết tài liệu phải chứa:

```text
c1f7e2d4a9b0 (head)
```

Sau khi migration thành công, có thể khởi động toàn bộ ứng dụng:

```bash
cd ..
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d --build
```

### Lưu ý về Spring Boot và Alembic

Backend hiện đặt `spring.jpa.hibernate.ddl-auto: update` và có nhiều bảng cùng tên với schema Alembic. Với database mới, nếu chạy backend trước, Hibernate có thể tạo bảng trước và làm revision Alembic lỗi `relation/table already exists`.

Quy trình an toàn hiện tại là:

1. Khởi động riêng PostgreSQL.
2. Chạy `python -m alembic upgrade head` trong thư mục `ai`.
3. Sau đó mới khởi động backend hoặc toàn bộ Docker Compose.

Không dùng `alembic stamp head` để bỏ qua lỗi trừ khi đã đối chiếu chắc chắn schema thực tế giống hoàn toàn với migration. Về lâu dài, nhóm nên thống nhất một công cụ làm chủ schema chung thay vì để cả Hibernate và Alembic cùng thay đổi các bảng trùng nhau.

## 6. Mở PostgreSQL và xem dữ liệu

### Cách 1: dùng `psql` ngay trong container

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.yml exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

Một số lệnh `psql` hữu ích:

```sql
\conninfo
\dx
\dt
\d diseases
\d treatment_rules
\d disease_management_policies
SELECT version_num FROM alembic_version;
SELECT COUNT(*) FROM diseases;
SELECT COUNT(*) FROM pesticides;
SELECT COUNT(*) FROM treatment_rules;
SELECT COUNT(*) FROM disease_management_policies;
SELECT COUNT(*) FROM documents;
\q
```

### Cách 2: dùng pgAdmin, DBeaver hoặc DataGrip

Với cấu hình mặc định:

| Thuộc tính | Giá trị |
| --- | --- |
| Host | `localhost` |
| Port | `5432` |
| Database | `dxfarm` |
| Username | `dxfarm` |
| Password | `dxfarm` |

Nếu đã sửa `deploy/.env`, dùng các giá trị mới thay cho bảng trên. Không dùng hostname `postgres` từ ứng dụng chạy trực tiếp trên máy; tên này chỉ dùng giữa các container trong Docker Compose.

## 7. Tạo migration mới khi thay đổi SQLAlchemy model

Trước tiên cập nhật model trong `ai/app/db/models` và bảo đảm model được import qua `ai/app/db/models/__init__.py`. Sau đó chạy:

```bash
cd ai
source venv/bin/activate
python -m alembic current
python -m alembic revision --autogenerate -m "mo ta thay doi"
```

Alembic chỉ sinh bản nháp. Trước khi chạy migration, phải mở file mới trong `ai/migrations/versions` và kiểm tra:

- `revision` và `down_revision` nối đúng chuỗi hiện tại.
- Không có thao tác xoá bảng/cột ngoài ý muốn.
- Kiểu dữ liệu, nullable, unique, index và foreign key đúng thiết kế.
- Hàm `downgrade()` có thể hoàn tác hợp lý.
- Các lệnh riêng của PostgreSQL/pgvector đã được thêm nếu autogenerate không nhận ra.

Đặc biệt, metadata Python hiện không chứa mọi entity riêng của backend (ví dụ bảng `users`). Vì Alembic đang trỏ vào database dùng chung, `--autogenerate` có thể đề xuất xoá bảng do backend quản lý. Tuyệt đối loại bỏ các thao tác `drop_table`/`drop_column` ngoài phạm vi thay đổi đang làm trước khi chạy `upgrade`.

Áp dụng và xác minh:

```bash
python -m alembic upgrade head
python -m alembic current
python -m alembic check
```

Sau khi kiểm tra trên database local, commit cả thay đổi model và file revision trong cùng một nhánh/PR. Không sửa nội dung revision đã được đồng đội áp dụng; hãy tạo revision mới để sửa tiếp.

## 8. Quay lại migration trước

Xem lịch sử trước:

```bash
cd ai
python -m alembic history
```

Lùi một revision:

```bash
python -m alembic downgrade -1
```

Hoặc về revision cụ thể:

```bash
python -m alembic downgrade 9700136d7e00
```

> `downgrade` có thể xoá bảng hoặc dữ liệu. Chỉ dùng trên môi trường local/test hoặc sau khi đã sao lưu và đọc kỹ hàm `downgrade()`.

## 9. Nạp Structured Knowledge Stage 5

Sau khi database đã ở `head`, chạy từ thư mục `ai` theo đúng thứ tự:

```bash
python -m scripts.seed_diseases
python -m scripts.seed_pesticides
python -m scripts.seed_treatment_rules
python -m scripts.seed_disease_management_policies
```

- Các seeder idempotent: chạy lại không nhân đôi dữ liệu.
- Ba seeder Stage 5 chỉ đọc các file final trong `data/structured`; không đọc
  `data/stage3/hold`.
- Kết quả mong đợi hiện tại: 38 disease, 4 pesticide, 8 treatment rule và 21
  disease-management policy.
- Seeder rule/policy sẽ dừng nếu disease hoặc pesticide tham chiếu chưa tồn tại, vì vậy phải
  chạy `seed_diseases` và `seed_pesticides` trước.

### Tùy chọn: kiểm tra hoặc ingest dữ liệu RAG

Stage 4/5 không yêu cầu embedding. Chỉ chạy khi nhóm chủ động muốn cập nhật pgvector:

```bash
python -m scripts.ingest_diseases --dry-run --limit 3
python -m scripts.ingest_diseases
```

- `--dry-run` chỉ kiểm tra/hiển thị dữ liệu RAG, không ghi database.
- Lệnh không có `--dry-run` sẽ tạo embedding và ghi vào `documents`,
  `document_chunks`; lần chạy sau thay thế bộ tài liệu bệnh trước đó.
- Ingest thật cần tải/chạy model embedding, nên lần đầu có thể mất thời gian và cần mạng.

## 10. Dừng hoặc làm sạch PostgreSQL

Dừng container nhưng giữ dữ liệu:

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.yml down
```

Khởi động lại sẽ dùng dữ liệu cũ trong `deploy/data/postgres`.

Muốn tạo database sạch hoàn toàn, phải dừng Compose rồi xoá thư mục `deploy/data/postgres`. Thao tác này xoá toàn bộ dữ liệu local và không thể hoàn tác nếu chưa sao lưu, vì vậy không thực hiện trên môi trường dùng chung.

## 11. Lỗi thường gặp

### `DATABASE_URL is not configured`

- Bảo đảm có file `ai/.env`.
- Chạy Alembic từ thư mục `ai`.
- Kiểm tra tên biến phải là `DATABASE_URL`.

### `connection refused` hoặc timeout khi kết nối

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.yml ps
docker compose --env-file deploy/.env -f deploy/docker-compose.yml logs postgres
```

Kiểm tra container đã `healthy`, port không bị chương trình khác chiếm và port trong `DATABASE_URL` trùng `POSTGRES_PORT`.

### `password authentication failed`

Giá trị trong `ai/.env` phải trùng `deploy/.env`. Lưu ý thay đổi `POSTGRES_PASSWORD` sau khi volume database đã được tạo không tự đổi mật khẩu bên trong PostgreSQL; cần đổi bằng SQL hoặc tạo lại database local.

### `relation ... already exists`

Database có thể đã được Hibernate tạo schema trước Alembic. Không tự chạy `stamp head`. Nếu đây chỉ là dữ liệu local có thể bỏ, hãy tạo lại database sạch rồi chạy Alembic trước backend; nếu cần giữ dữ liệu, phải sao lưu và đối chiếu schema trước khi xử lý.

### `type "vector" does not exist`

Bảo đảm đang dùng image `pgvector/pgvector:pg16`, rồi kiểm tra:

```sql
CREATE EXTENSION IF NOT EXISTS vector;
\dx
```

Revision `4c2a0f9d8b71` cũng tự tạo extension này khi chạy `upgrade`.

### Pip tải dependency bị `ReadTimeoutError`

Đây thường là lỗi mạng khi tải từ PyPI, không phải lỗi PostgreSQL hay migration. Thử lại với timeout lớn hơn:

```bash
python -m pip install --default-timeout=120 --retries 10 -r requirements.txt
```

## Checklist nhanh cho thành viên mới

```bash
cp deploy/.env.example deploy/.env
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d postgres
cd ai
python3 -m venv venv
source venv/bin/activate
python -m pip install -r requirements.txt
# Tạo ai/.env với DATABASE_URL như mục 4
python -m alembic upgrade head
python -m alembic current
python -m scripts.seed_diseases
python -m scripts.seed_pesticides
python -m scripts.seed_treatment_rules
python -m scripts.seed_disease_management_policies
```

Khi `alembic current` hiển thị `c1f7e2d4a9b0 (head)` và có thể truy vấn bảng bằng
`psql`, môi trường PostgreSQL local đã sẵn sàng.
