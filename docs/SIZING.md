# Cỡ cấu hình và tải đồng thời

Tài liệu này trả lời hai câu hỏi: lõi chịu được bao nhiêu người dùng đồng thời, và cấu hình nhỏ nhất đáp ứng ngưỡng công bố là bao nhiêu nhân.
Số liệu do `deploy/dev/load_test.py` đo trên một stack tạm, ngày 2026-11-12.
Mọi số là cận dưới vì bộ tạo tải chạy cùng máy với hệ thống được đo.

## Ngưỡng công bố

Một mức đồng thời đạt khi p95 của mọi yêu cầu dưới 500 ms và dưới 1% yêu cầu lỗi.
p99 chỉ được báo cáo, không dùng để quyết đạt hay không.

## Cách đo

- Máy đo có 12 nhân và 30 GB RAM; bản đo ở mức 12 nhân dùng cả máy.
- Các mức 8, 4 và 2 nhân ghim mọi container vào dải nhân `0` đến `N-1` bằng `cpuset`, còn bộ tạo tải ghim ngoài dải đó.
- Riêng mức 12 nhân, bộ tạo tải dùng chung nhân với hệ thống và tiêu 0,04 đến 0,33 nhân.
- Giới hạn bộ nhớ của mọi cấu hình, tổng khoảng 4,7 GB: PostgreSQL, backend và Keycloak 1 GB; APISIX, monitor và SeaweedFS 512 MB; frontend và portal 64 MB.
- Một lần đo thêm ở 2 nhân dùng giới hạn nhỏ hơn, tổng khoảng 3,06 GB: backend và Keycloak 768 MB, PostgreSQL 512 MB, APISIX 384 MB, SeaweedFS và monitor 256 MB, frontend và portal 32 MB.
- Mô hình đo có một thực thể `ticket` với họ quy trình `work_item`, 10 người nộp và 5 người xử lý.
- Mỗi người dùng ảo lặp: tạo hồ sơ, liệt kê hồ sơ đang mở có lọc, thực hiện hành động đầu tiên của quy trình, đọc lịch sử, liệt kê hồ sơ của mình.
- Trước mỗi mức đồng thời, dữ liệu được đưa về đúng 10.000 hồ sơ; mỗi mức làm nóng 30 giây rồi đo 60 giây.
- Mỗi người dùng ảo lấy token một lần rồi dùng lại; đăng nhập được đo riêng.
- Giới hạn tốc độ của cổng API và của dịch vụ lõi được tắt trên stack tạm, nên số đo là năng lực của lõi, không phải ngưỡng cho một địa chỉ IP.

## Kết quả theo số nhân

Mỗi ô ghi thông lượng (yêu cầu mỗi giây), rồi p50/p95/p99 tính bằng ms.
Lỗi là 0% ở mọi ô.
Ô in đậm là mức lớn nhất còn đạt ngưỡng.

### 12 nhân

| Đồng thời | Yêu cầu/giây | p50 / p95 / p99 (ms) | Đạt |
|---|---|---|---|
| 1 | 113 | 6 / 16 / 88 | có |
| 10 | 800 | 10 / 26 / 42 | có |
| 25 | 683 | 28 / 100 / 164 | có |
| 50 | 828 | 52 / 134 / 285 | có |
| 75 | 755 | 88 / 224 / 344 | có |
| **100** | 833 | 111 / 244 / 487 | có |
| 200 | 714 | 244 / 627 / 901 | không |

### 8 nhân

| Đồng thời | Yêu cầu/giây | p50 / p95 / p99 (ms) | Đạt |
|---|---|---|---|
| 1 | 173 | 5 / 12 / 14 | có |
| 10 | 778 | 10 / 28 / 86 | có |
| 25 | 656 | 32 / 83 / 162 | có |
| 50 | 807 | 57 / 125 / 222 | có |
| 75 | 686 | 98 / 241 / 396 | có |
| **100** | 750 | 122 / 287 / 505 | có |
| 200 | 694 | 248 / 656 / 1042 | không |

### 4 nhân

| Đồng thời | Yêu cầu/giây | p50 / p95 / p99 (ms) | Đạt |
|---|---|---|---|
| 1 | 116 | 5 / 13 / 98 | có |
| 10 | 676 | 11 / 37 / 47 | có |
| 25 | 503 | 42 / 128 / 198 | có |
| 50 | 643 | 71 / 159 / 255 | có |
| 75 | 518 | 127 / 324 / 449 | có |
| **100** | 585 | 160 / 367 / 512 | có |
| 200 | 587 | 310 / 733 / 954 | không |

### 2 nhân

| Đồng thời | Yêu cầu/giây | p50 / p95 / p99 (ms) | Đạt |
|---|---|---|---|
| 1 | 143 | 5 / 12 / 74 | có |
| 10 | 366 | 18 / 78 / 105 | có |
| 25 | 331 | 67 / 160 / 239 | có |
| 50 | 354 | 132 / 287 / 366 | có |
| **75** | 348 | 207 / 426 / 568 | có |
| 100 | 354 | 273 / 568 / 777 | không |
| 200 | 349 | 560 / 1198 / 1664 | không |

## Cấu hình nhỏ nhất

Cấu hình nhỏ nhất đã đo là 2 nhân; nó đạt ngưỡng đến 75 người dùng đồng thời với thông lượng đỉnh khoảng 350 yêu cầu mỗi giây.
Từ 4 nhân trở lên đạt ngưỡng đến 100 người dùng đồng thời; điểm gãy ở mọi cấu hình nằm giữa 100 và 200.

| Số nhân | Đồng thời lớn nhất còn đạt | p95 tại mức đó (ms) | Thông lượng đỉnh (yêu cầu/giây) |
|---|---|---|---|
| 12 | 100 | 244 | khoảng 830 |
| 8 | 100 | 287 | khoảng 800 |
| 4 | 100 | 367 | khoảng 680 |
| 2 | 75 | 426 | khoảng 365 |

## Tài nguyên

CPU lấy từ `cpu.stat` của cgroup, tính bằng số nhân trung bình dùng thật ở 100 người dùng đồng thời.

| Số nhân | PostgreSQL | Backend | APISIX | Tổng |
|---|---|---|---|---|
| 12 | 3,2 | 2,5 | 1,0 | khoảng 6,7 |
| 8 | 2,8 | 2,1 | 0,8 | khoảng 5,7 |
| 4 | 1,6 | 1,35 | 0,5 | khoảng 3,5 |
| 2 | 0,85 đến 1,0 | 0,75 | 0,29 | khoảng 1,9 |

- Ở 2 nhân, CPU đã dùng hết (khoảng 1,9 trên 2) từ 10 người dùng đồng thời trở lên, và thông lượng không tăng thêm.
- Ở 4 nhân trở xuống, nhân là giới hạn.
- Ở 8 và 12 nhân, thông lượng đứng ở khoảng 800 yêu cầu mỗi giây dù mới dùng khoảng 6 nhân.
- Pool kết nối cơ sở dữ liệu mặc định 10 kết nối chỉ góp một phần nhỏ vào mức đứng này; nguyên nhân còn lại chưa được kiểm chứng, xem mục pool kết nối bên dưới.
- RAM đỉnh trong mọi cấu hình: backend 555 đến 583 MiB, Keycloak 496 đến 591 MiB, APISIX 363 đến 382 MiB, PostgreSQL 240 đến 289 MiB, SeaweedFS 117 đến 140 MiB.
- Trong mọi bản đo, RAM đỉnh nằm dưới giới hạn đã đặt.
- Cộng các đỉnh cao nhất của năm dịch vụ chính (backend 583, Keycloak 591, APISIX 382, PostgreSQL 289, SeaweedFS 140 MiB), RAM thật dùng khoảng 2,0 GB.

### Giới hạn bộ nhớ nhỏ hơn, 2 nhân

Cấu hình giới hạn bộ nhớ khoảng 3,06 GB cho cùng kết quả như cấu hình 1 GB ở 2 nhân: đạt đến 75 người dùng đồng thời, gãy ở 100.
Không container nào bị dừng vì thiếu bộ nhớ hay khởi động lại, và nhật ký backend không có `OutOfMemoryError`.
RAM đỉnh trong lần đo này: backend 494 MiB, Keycloak 476, APISIX 356, PostgreSQL 242, SeaweedFS 111.

| Đồng thời | Yêu cầu/giây | p50 / p95 / p99 (ms) | Đạt |
|---|---|---|---|
| 10 | 243 | 19 / 90 / 691 | có |
| 50 | 338 | 136 / 308 / 404 | có |
| **75** | 326 | 220 / 460 / 611 | có |
| 100 | 355 | 271 / 571 / 774 | không |

p95 ở 75 người dùng đồng thời là 460 ms, sát ngưỡng hơn 426 ms của cấu hình 1 GB; chưa rõ chênh lệch này có phải nhiễu đo hay không.
Đăng nhập ở mức 1, 10 và 50: 22, 31 và 34 lượt mỗi giây, p95 lần lượt 49, 341 và 1634 ms, không lỗi.
Biến đặt giới hạn là `LOAD_MEM_BACKEND`, `LOAD_MEM_KEYCLOAK`, `LOAD_MEM_POSTGRES`, `LOAD_MEM_APISIX`, `LOAD_MEM_SEAWEEDFS`, `LOAD_MEM_MONITOR`, `LOAD_MEM_FRONTEND` và `LOAD_MEM_PORTAL`.

### Pool kết nối lớn hơn, 12 nhân

Đặt pool kết nối cơ sở dữ liệu của backend lên 30 (`SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=30`) và đo lại ở 12 nhân, cùng giới hạn bộ nhớ 1 GB; PostgreSQL thấy 32 kết nối.
Mỗi ô ghi bản pool 30, kèm thông lượng và p95 của pool 10 mặc định trong ngoặc.

| Đồng thời | Yêu cầu/giây | p50 / p95 / p99 (ms) | Lỗi | Pool 10: yêu cầu/giây, p95 |
|---|---|---|---|---|
| 25 | 877 | 10 / 61 / 242 | 0% | 683, 100 ms |
| 50 | 967 | 33 / 106 / 157 | 0% | 828, 134 ms |
| **100** | 859 | 84 / 224 / 753 | 0% | 833, 244 ms |
| 200 | 792 | 209 / 561 / 1133 | 0% | 714, 627 ms |

- Pool 30 tăng thông lượng khoảng 3 đến 17% và hạ p95 ở 25 và 50 người dùng đồng thời; điểm gãy vẫn nằm giữa 100 và 200.
- CPU ở 100 người dùng đồng thời: PostgreSQL 3,0 nhân, backend 2,4, APISIX 0,9, tổng khoảng 6,3 trên 12.
- RAM đỉnh: backend 582 MiB, Keycloak 514, PostgreSQL 376, APISIX 378, SeaweedFS 156.
- Đăng nhập ở mức 1, 10 và 50: 31, 33 và 35 lượt mỗi giây, p95 lần lượt 35, 335 và 1531 ms.
- Thông lượng vẫn đứng dưới khoảng 1000 yêu cầu mỗi giây dù chỉ dùng 6 đến 7 trên 12 nhân; nguyên nhân còn lại chưa được kiểm chứng.
- Giả thuyết: mọi lần ghi dòng lịch sử lấy cùng một khóa tư vấn toàn cục của PostgreSQL (`HistoryChain.lock()`) để xếp hàng chuỗi hash, nên các lần ghi nối tiếp nhau; ghi là ba trên năm bước của vòng đo.

## Đăng nhập

Đăng nhập nặng hơn một yêu cầu API nhiều lần nên được đo riêng, với số đăng nhập đồng thời 1, 10 và 50.
Keycloak bão hòa ở khoảng 23 đến 34 lượt đăng nhập mỗi giây (28 đến 34 ở 50 đăng nhập đồng thời), bất kể số nhân từ 2 đến 12.

| Số nhân | Lượt/giây ở 1 / 10 / 50 | p95 ở 1 / 10 / 50 (ms) |
|---|---|---|
| 12 | 23 / 28 / 28 | 50 / 375 / 1933 |
| 8 | 28 / 32 / 32 | 43 / 337 / 1608 |
| 4 | 28 / 32 / 34 | 49 / 326 / 1536 |
| 2 | 27 / 29 / 34 | 41 / 435 / 1617 |

Vì vậy một lượng lớn người dùng cùng đăng nhập trong vài giây sẽ chậm, còn người dùng đã có token thì không bị ảnh hưởng.

## Độ trễ theo cỡ dữ liệu

Độ trễ phụ thuộc số hồ sơ trong bảng.
Cùng 12 nhân và 10 người dùng đồng thời: với 10.000 hồ sơ, thông lượng là 800 yêu cầu mỗi giây và p95 là 26 ms; với khoảng 198.000 hồ sơ, thông lượng là 335 yêu cầu mỗi giây và p95 là 43 ms, lúc đó PostgreSQL dùng khoảng 2,8 đến 3 nhân.
Mọi bảng số ở trên đo trên bảng 10.000 hồ sơ; bảng lớn hơn cần đo lại.

## Lỗi đã tìm thấy và sửa khi đo

Lần đo đầu cho thấy từ 50 người dùng đồng thời trở lên, backend treo 30 giây rồi trả lỗi 500 (lỗi 74% đến 92%), trong khi CPU của backend gần bằng 0.
Bộ sinh id của Flowable lấy khối id từ cơ sở dữ liệu trong một khối đồng bộ và cần thêm một kết nối; 10 kết nối của pool đều bị các luồng giữ trong lúc chờ khối đồng bộ đó, nên không luồng nào tiến được.
Flowable nay dùng id UUID sinh trong bộ nhớ, nên không còn cần kết nối để lấy id.

## Những gì chưa đo

- Cấu hình 1 nhân.
- Các mức đồng thời mịn hơn giữa 75 và 200.
- Giới hạn bộ nhớ nhỏ hơn cấu hình 3,06 GB ở trên; heap JVM mặc định bằng một phần tư giới hạn của container, nên backend dưới khoảng 640 MB chưa được thử.
- Kiểm chứng giả thuyết khóa tư vấn của chuỗi hash lịch sử: đo lại với việc ghi lịch sử không xếp hàng, hoặc với vòng đo ít bước ghi hơn.
- Pool kết nối lớn hơn 30.
- Bảng dữ liệu lớn hơn 10.000 hồ sơ.
- Tải qua một cổng API còn bật giới hạn tốc độ: một địa chỉ IP bị giới hạn 600 yêu cầu mỗi phút ở `/api/*` và 120 mỗi phút ở `/token`.

## Chạy lại

Chạy trên một bản sao tạm của `deploy/`, không bao giờ trên stack đang dùng: `--reset` xóa dữ liệu của stack.

1. Sao chép `deploy/` (không kèm thư mục `data/`) ra một thư mục tạm và dựng `.env` của bản sao với `COMPOSE_PROJECT_NAME=dxscratch`, `APISIX_HOST_PORT=9180`, `POSTGRES_HOST_PORT=127.0.0.1:5532`, `KEYCLOAK_ADMIN_CONSOLE=true` và `RATE_LIMIT_ENABLED=false`.
2. Nới các giá trị `limit-count` trong `apisix/common.yaml` của bản sao.
3. Chuẩn bị sẵn image có cùng `DXOS_IMAGE_TAG`.
4. Dựng stack với dải nhân của máy giả lập, trong thư mục bản sao của `deploy/`.
   Đổi `LOAD_CPUSET` thì phải tạo lại container vì JVM đọc số nhân lúc khởi động:

```
LOAD_CPUSET=0-3 docker compose -p dxscratch -f docker-compose.yml -f dev/load_override.yml up -d --force-recreate
```

5. Chạy bộ tạo tải từ gốc repository.
   Từ 8 nhân trở xuống, ghim bộ tạo tải ngoài dải nhân của stack bằng `taskset -c N-11` (N là số nhân):

```
DXOS_ENV_FILE=<.env của bản sao> DXOS_BASE_URL=http://localhost:9180 DXOS_PROJECT=dxscratch \
  taskset -c 4-11 python3 deploy/dev/load_test.py --levels 1,10,25,50,75,100,200 --reset \
  --warmup 30 --duration 60 --login-levels 1,10,50 --out result.json
```

`LOAD_CPUSET` là dải nhân của máy giả lập, ví dụ `0-3` cho bốn nhân.
`DXOS_PROJECT` là bắt buộc: `--reset` và số CPU, RAM đều đi qua `docker compose` của dự án đó.
Số CPU đọc từ `/sys/fs/cgroup` nên cần cgroup v2 và quyền đọc thư mục đó.
